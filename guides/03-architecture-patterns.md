# Architecture Patterns

> **Time:** 25 minutes | **Level:** Intermediate | **Prerequisites:** [Guide 02](02-core-concepts.md)

This guide covers proven design patterns for MCP servers. Each pattern includes when to use it, the architecture, and implementation guidance.

## Pattern Catalog

| Pattern | Use When | Complexity |
|---------|----------|------------|
| [API Wrapper](#api-wrapper) | Exposing a REST/GraphQL API to AI | Low |
| [Database Explorer](#database-explorer) | Giving AI access to query a database | Medium |
| [File System Server](#file-system-server) | AI needs to read/search/manage files | Low |
| [Aggregator](#aggregator) | Combining multiple data sources | Medium |
| [Stateful Server](#stateful-server) | Server needs to maintain session state | High |
| [Pipeline](#pipeline) | Multi-step data processing workflows | Medium |

---

## API Wrapper

**Use when:** You have an existing REST or GraphQL API and want AI to interact with it.

**This is the most common pattern.** Most MCP servers in the wild are API wrappers.

### Architecture

```
AI Client ──► MCP Server ──► External API
              │                   │
              ├── Auth handling    │
              ├── Rate limiting    │
              ├── Response mapping │
              └── Error translation│
```

### Design Decisions

**One tool per endpoint vs. one generic tool:**

```
PREFER: One tool per logical action     AVOID: Generic proxy tool
├── search_issues                       └── call_api(method, path, body)
├── create_issue                            ↑ Too much flexibility
├── close_issue                             ↑ AI may misuse it
└── list_labels                             ↑ No input validation
```

**Why?** Specific tools give the AI clear affordances and let you validate inputs per-endpoint.

### Implementation Sketch (TypeScript)

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

const server = new McpServer({ name: "github-server", version: "1.0.0" });
const BASE_URL = "https://api.github.com";
const TOKEN = process.env.GITHUB_TOKEN;

// Helper — all API calls go through this
async function githubFetch(path: string, options?: RequestInit) {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      Accept: "application/vnd.github.v3+json",
      ...options?.headers,
    },
  });

  if (!response.ok) {
    throw new Error(`GitHub API error: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

// Each API action gets its own tool
server.tool(
  "search_issues",
  "Search GitHub issues and pull requests",
  {
    query: z.string().describe("Search query (GitHub search syntax)"),
    repo: z.string().optional().describe("Filter to a specific repo (owner/name)"),
    state: z.enum(["open", "closed", "all"]).optional().default("open"),
  },
  async ({ query, repo, state }) => {
    const q = repo ? `${query} repo:${repo} state:${state}` : `${query} state:${state}`;
    const data = await githubFetch(`/search/issues?q=${encodeURIComponent(q)}`);

    const summary = data.items.map((issue: any) =>
      `#${issue.number} [${issue.state}] ${issue.title}`
    ).join("\n");

    return {
      content: [{ type: "text", text: summary || "No issues found." }],
    };
  }
);
```

### Key Principles for API Wrappers

1. **Validate inputs strictly** — Don't let the AI pass arbitrary params to your API
2. **Translate errors** — Return human-readable messages, not raw HTTP errors
3. **Limit scope** — Expose only the endpoints the AI should use, not the entire API
4. **Format responses** — Return summarized text, not raw JSON (the AI processes text better)
5. **Handle auth centrally** — Use environment variables, never hardcode credentials

---

## Database Explorer

**Use when:** You want AI to query and understand a database.

### Architecture

```
AI Client ──► MCP Server ──► Database
              │
              ├── Tools: run_query, explain_query
              ├── Resources: schema, table info, stats
              └── Prompts: review-sql, optimize-query
```

### Design Decisions

**Read-only vs. read-write:**

For most use cases, start with **read-only**. Let the AI query data but not modify it.

```
Safe default:
├── Tool: run_query (SELECT only — reject INSERT/UPDATE/DELETE)
├── Resource: db://schema (full schema)
├── Resource: db://tables/{name} (table details + sample rows)
└── Prompt: write-query (help AI write correct queries)
```

**Query safety:**

```typescript
// ALWAYS validate queries before execution
function isSafeQuery(sql: string): boolean {
  const normalized = sql.trim().toUpperCase();
  const forbidden = ["INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "GRANT"];
  return !forbidden.some(keyword =>
    normalized.startsWith(keyword) || normalized.includes(` ${keyword} `)
  );
}
```

### Key Principles for Database Servers

1. **Default to read-only** — Require explicit opt-in for write operations
2. **Expose schema as a resource** — The AI needs to know the structure to write good queries
3. **Limit result sets** — Always enforce `LIMIT` to prevent returning millions of rows
4. **Parameterize queries** — Never string-interpolate user values into SQL
5. **Log all queries** — Maintain an audit trail of what the AI executed

---

## File System Server

**Use when:** You need AI to access, search, or manage files on disk.

### Architecture

```
AI Client ──► MCP Server ──► File System
              │
              ├── Tools: search_files, read_file, write_file
              ├── Resources: file://{path}
              └── Security: sandboxed to allowed directories
```

### The Critical Security Decision

**Always sandbox file access.** The AI should only access directories you explicitly allow.

```typescript
const ALLOWED_DIRS = ["/home/user/projects", "/tmp/workspace"];

function isPathAllowed(filePath: string): boolean {
  const resolved = path.resolve(filePath);
  return ALLOWED_DIRS.some(dir => resolved.startsWith(dir));
}
```

---

## Aggregator

**Use when:** Combining data from multiple sources into a unified interface.

### Architecture

```
                         ┌──► API A
AI Client ──► MCP Server ├──► Database B
                         └──► File System C
```

This pattern is powerful for giving AI a **single interface** to multiple backends.

### Implementation Approach

```typescript
// Each data source is an internal module
import { searchGitHub } from "./sources/github.js";
import { queryDatabase } from "./sources/database.js";
import { searchFiles } from "./sources/filesystem.js";

// The tool unifies them
server.tool(
  "search_everything",
  "Search across code, issues, and documentation",
  {
    query: z.string(),
    sources: z.array(z.enum(["code", "issues", "docs"])).optional().default(["code", "issues", "docs"]),
  },
  async ({ query, sources }) => {
    const results = await Promise.all([
      sources.includes("code") ? searchFiles(query) : [],
      sources.includes("issues") ? searchGitHub(query) : [],
      sources.includes("docs") ? queryDatabase(query) : [],
    ]);

    return {
      content: [{ type: "text", text: formatResults(results.flat()) }],
    };
  }
);
```

### Key Principle

Keep each data source independent. If GitHub is down, the file system and database should still work. Use `Promise.allSettled` instead of `Promise.all` when partial results are acceptable.

---

## Stateful Server

**Use when:** Your server needs to track state across multiple tool calls (e.g., a multi-step workflow, a REPL, or a shopping cart).

### Architecture

```
AI Client ──► MCP Server (with state)
              │
              ├── In-memory state (sessions, caches)
              ├── Tools modify and query state
              └── Resources expose current state
```

### Design Consideration

MCP itself is **stateless per request**, but your server process persists between calls. Use this for:
- Database connections (keep them pooled)
- Caches (avoid re-fetching expensive data)
- Multi-step workflows (track progress)

Be careful with:
- Assuming state survives server restarts (it won't)
- Sharing state across multiple client connections (scope state per client if needed)

---

## Pipeline

**Use when:** Data needs to pass through multiple processing stages.

### Architecture

```
Input ──► Stage 1 ──► Stage 2 ──► Stage 3 ──► Output
          (fetch)     (transform)  (analyze)
```

Expose the pipeline as a single tool, or expose each stage as a separate tool for flexibility.

---

## Choosing a Pattern

```
What are you building?
│
├── Connecting to an external service?
│   └── API Wrapper
│
├── Giving AI access to a database?
│   └── Database Explorer
│
├── Working with files on disk?
│   └── File System Server
│
├── Combining multiple data sources?
│   └── Aggregator
│
├── Tracking state between calls?
│   └── Stateful Server
│
└── Processing data through stages?
    └── Pipeline
```

## What's Next

- **[Guide 04: Error Handling](04-error-handling.md)** — Making your server resilient
- **[Guide 05: Testing](05-testing.md)** — Testing strategies for MCP servers
- **[Examples](../examples/)** — See these patterns in complete, runnable servers
