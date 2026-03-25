# Security & Authentication

> **Time:** 20 minutes | **Level:** Advanced | **Prerequisites:** [Guide 04](04-error-handling.md)

MCP servers are a bridge between AI and your systems. A vulnerable server can leak data, destroy resources, or become an attack vector. This guide covers how to build servers that are secure by default.

## Threat Model

```
AI Client ──► MCP Server ──► Your Data / APIs / Systems
              │
              Threats:
              ├── Prompt injection (AI tricked into malicious tool calls)
              ├── Path traversal (accessing files outside allowed dirs)
              ├── SQL injection (malicious queries via tool inputs)
              ├── Credential exposure (leaking API keys in responses)
              ├── Denial of service (unbounded queries, huge files)
              └── Privilege escalation (read-only server doing writes)
```

## Principle: Least Privilege

> Give your server the minimum permissions it needs. Nothing more.

```
GOOD                              BAD
├── Read-only database access     ├── Full database admin
├── Single directory sandboxed    ├── Entire filesystem access
├── Specific API scopes           ├── All API permissions
└── SELECT queries only           └── All SQL operations
```

## Input Validation

Every tool input must be validated. The AI may pass unexpected values — either through prompt injection or simply misunderstanding.

### Validate File Paths

```typescript
import { resolve } from "path";

const ALLOWED_ROOT = "/home/user/projects";

function validatePath(filePath: string): string {
  const resolved = resolve(ALLOWED_ROOT, filePath);

  if (!resolved.startsWith(ALLOWED_ROOT)) {
    throw new Error("Access denied: path is outside allowed directory");
  }

  return resolved;
}

// Blocks: "../../../etc/passwd", "/etc/shadow", symlinks outside root
```

### Validate SQL

```typescript
function validateQuery(sql: string): void {
  const upper = sql.toUpperCase().trim();

  // Whitelist approach: only allow SELECT
  if (!upper.startsWith("SELECT")) {
    throw new Error("Only SELECT queries are allowed");
  }

  // Block dangerous patterns even within SELECTs
  const dangerous = ["INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE"];
  for (const pattern of dangerous) {
    if (upper.includes(pattern)) {
      throw new Error(`Query contains forbidden pattern: ${pattern}`);
    }
  }
}
```

### Validate URLs

```typescript
function validateUrl(url: string): void {
  const parsed = new URL(url);

  // Only allow HTTP(S)
  if (!["http:", "https:"].includes(parsed.protocol)) {
    throw new Error("Only HTTP and HTTPS URLs are allowed");
  }

  // Block internal network access (SSRF prevention)
  const hostname = parsed.hostname;
  if (
    hostname === "localhost" ||
    hostname === "127.0.0.1" ||
    hostname.startsWith("10.") ||
    hostname.startsWith("192.168.") ||
    hostname.startsWith("172.16.") ||
    hostname.endsWith(".internal")
  ) {
    throw new Error("Access to internal network addresses is not allowed");
  }
}
```

## Credential Management

### Never Hardcode Secrets

```typescript
// BAD — secret in source code
const API_KEY = "sk-1234567890abcdef";

// GOOD — from environment
const API_KEY = process.env.API_KEY;
if (!API_KEY) {
  throw new Error("API_KEY environment variable is required");
}
```

### Never Expose Secrets in Responses

```typescript
server.tool("call_api", "Call external API", { endpoint: z.string() }, async ({ endpoint }) => {
  try {
    const data = await apiFetch(endpoint);
    return { content: [{ type: "text", text: JSON.stringify(data) }] };
  } catch (error) {
    // BAD — might leak API key in error message
    // return { content: [{ type: "text", text: error.message }] };

    // GOOD — sanitized error
    return {
      content: [{ type: "text", text: `API call to ${endpoint} failed. Check server logs.` }],
      isError: true,
    };
  }
});
```

### Pass Credentials via Environment

In client configs, use `env` to pass secrets:

```json
{
  "mcpServers": {
    "my-server": {
      "command": "npx",
      "args": ["tsx", "server.ts"],
      "env": {
        "API_KEY": "your-key-here",
        "DATABASE_URL": "postgres://..."
      }
    }
  }
}
```

## Rate Limiting

Prevent the AI from overwhelming external services:

```typescript
class RateLimiter {
  private timestamps: number[] = [];

  constructor(
    private maxRequests: number,
    private windowMs: number,
  ) {}

  check(): void {
    const now = Date.now();
    this.timestamps = this.timestamps.filter((t) => now - t < this.windowMs);

    if (this.timestamps.length >= this.maxRequests) {
      const waitMs = this.windowMs - (now - this.timestamps[0]);
      throw new Error(`Rate limited. Try again in ${Math.ceil(waitMs / 1000)} seconds.`);
    }

    this.timestamps.push(now);
  }
}

const limiter = new RateLimiter(30, 60_000); // 30 requests per minute
```

## Security Checklist

Before deploying any MCP server:

- [ ] **Inputs validated** — All tool inputs are checked before use
- [ ] **Paths sandboxed** — File access restricted to allowed directories
- [ ] **Queries safe** — SQL injection prevented (parameterized or whitelist)
- [ ] **URLs validated** — SSRF prevention for any URL-accepting tools
- [ ] **Credentials in env** — No hardcoded secrets in source code
- [ ] **Errors sanitized** — No secrets or internal paths in error messages
- [ ] **Results bounded** — Limits on query results, file sizes, API responses
- [ ] **Rate limits set** — External API calls are throttled
- [ ] **Least privilege** — Server has minimum necessary permissions
- [ ] **Dependencies audited** — `npm audit` / `pip audit` run before deploy

## What's Next

- **[Guide 07: Production](07-production.md)** — Deploy your secure server
- **[Guide 08: Performance](08-performance.md)** — Optimize for scale
