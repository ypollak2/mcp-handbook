# Advanced Topics

> **Time:** 30 minutes | **Level:** Advanced | **Prerequisites:** [Guide 03](03-architecture-patterns.md)

This guide covers MCP features beyond basic tools and resources. These capabilities unlock powerful patterns but are poorly documented elsewhere.

## Overview

| Feature | What It Does | When You Need It |
|---------|-------------|-----------------|
| [Streamable HTTP](#streamable-http-transport) | Remote server communication | Servers not on the same machine |
| [Sampling](#sampling) | Server asks the AI to generate text | Server-side summarization, analysis |
| [Elicitation](#elicitation) | Server asks the user for input | Confirmations, credentials, forms |
| [Progress Notifications](#progress-notifications) | Report progress on long tasks | Operations > 5 seconds |
| [Resource Subscriptions](#resource-subscriptions) | Live data updates | Config changes, file watching |
| [Completions](#completions) | Auto-complete tool arguments | Better UX for complex inputs |
| [OAuth / Authorization](#oauth--authorization) | Authenticate with external services | APIs that need user auth |

---

## Streamable HTTP Transport

By default, MCP servers use **stdio** — the client spawns your server as a child process. **Streamable HTTP** is for remote servers that clients connect to over the network.

### When to Use What

```
Use STDIO when:                    Use STREAMABLE HTTP when:
├─ Server runs locally             ├─ Server is remote/cloud-hosted
├─ Client spawns the process       ├─ Multiple clients share one server
├─ Simple deployment               ├─ Server needs a web interface
└─ No auth needed                  └─ Enterprise/team deployment
```

### TypeScript — HTTP Server

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { NodeStreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import express from "express";

const app = express();
const server = new McpServer({ name: "remote-server", version: "1.0.0" });

// Register your tools, resources, prompts...
server.tool("hello", "Say hello", { name: z.string() }, async ({ name }) => ({
  content: [{ type: "text", text: `Hello, ${name}!` }],
}));

// Stateless mode (no session tracking)
app.post("/mcp", async (req, res) => {
  const transport = new NodeStreamableHTTPServerTransport({
    sessionIdGenerator: undefined, // Stateless
  });
  await server.connect(transport);
  await transport.handleRequest(req, res);
});

// Stateful mode (with sessions)
const sessions = new Map();

app.post("/mcp", async (req, res) => {
  const sessionId = req.headers["mcp-session-id"];

  if (sessionId && sessions.has(sessionId)) {
    // Reuse existing session
    const transport = sessions.get(sessionId);
    await transport.handleRequest(req, res);
  } else {
    // New session
    const transport = new NodeStreamableHTTPServerTransport({
      sessionIdGenerator: () => crypto.randomUUID(),
    });
    transport.onclose = () => sessions.delete(transport.sessionId);
    sessions.set(transport.sessionId, transport);
    await server.connect(transport);
    await transport.handleRequest(req, res);
  }
});

app.listen(3001, () => console.error("MCP server on http://localhost:3001/mcp"));
```

### Client Config for HTTP

```json
{
  "mcpServers": {
    "remote-server": {
      "url": "http://localhost:3001/mcp"
    }
  }
}
```

### Stateless vs Stateful

| Mode | Use When | Trade-off |
|------|----------|-----------|
| **Stateless** | Simple tools, no state between calls | Easy to scale, no session cleanup |
| **Stateful** | Multi-step workflows, caching, subscriptions | Need session management and cleanup |

---

## Sampling

Sampling lets your **server ask the client's AI model** to generate text. The server sends a prompt, and the client returns a completion — effectively giving your server access to the LLM.

### Use Cases
- Server-side text summarization
- Data analysis and insight generation
- Code review within a tool pipeline
- Translation during data processing

### TypeScript

```typescript
server.tool(
  "analyze_data",
  "Analyze a dataset and provide AI-generated insights",
  { data: z.string().describe("CSV or JSON data to analyze") },
  async ({ data }, { meta, server: srv }) => {
    // Ask the client's AI to analyze the data
    const response = await srv.server.createMessage({
      messages: [
        {
          role: "user",
          content: {
            type: "text",
            text: `Analyze this dataset and provide 3 key insights:\n\n${data}`,
          },
        },
      ],
      maxTokens: 500,
    });

    return {
      content: [
        {
          type: "text",
          text: `AI Analysis:\n\n${response.content.type === "text" ? response.content.text : "Analysis complete."}`,
        },
      ],
    };
  }
);
```

### Python

```python
from mcp.server.fastmcp import Context

@mcp.tool()
async def analyze_data(data: str, ctx: Context) -> str:
    """Analyze a dataset and provide AI-generated insights."""
    result = await ctx.session.create_message(
        messages=[
            {
                "role": "user",
                "content": {
                    "type": "text",
                    "text": f"Analyze this dataset and provide 3 key insights:\n\n{data}",
                },
            }
        ],
        max_tokens=500,
    )

    if result.content.type == "text":
        return f"AI Analysis:\n\n{result.content.text}"
    return "Analysis complete."
```

### Important Caveats

> [!WARNING]
> - **Not all clients support sampling.** Always handle the case where sampling fails or isn't available.
> - **Cost implications.** Each sampling request consumes tokens on the client side.
> - **No model control.** The server can't choose which model the client uses.

```typescript
// Defensive sampling with fallback
async function trySampling(server, prompt, fallback) {
  try {
    const response = await server.server.createMessage({
      messages: [{ role: "user", content: { type: "text", text: prompt } }],
      maxTokens: 300,
    });
    return response.content.type === "text" ? response.content.text : fallback;
  } catch {
    return fallback; // Client doesn't support sampling
  }
}
```

---

## Elicitation

Elicitation lets the server **ask the user a question directly** during tool execution. Useful for confirmations, preferences, or collecting credentials.

### Form Mode (Non-Sensitive Data)

```typescript
server.tool(
  "deploy",
  "Deploy the application to an environment",
  { environment: z.enum(["staging", "production"]) },
  async ({ environment }, { server: srv }) => {
    if (environment === "production") {
      // Ask the user to confirm
      const confirmation = await srv.server.elicitInput({
        message: `Are you sure you want to deploy to PRODUCTION?`,
        requestedSchema: {
          type: "object",
          properties: {
            confirm: {
              type: "boolean",
              description: "Type true to confirm production deployment",
            },
            reason: {
              type: "string",
              description: "Reason for production deployment",
            },
          },
          required: ["confirm"],
        },
      });

      if (confirmation.action !== "accept" || !confirmation.content?.confirm) {
        return {
          content: [{ type: "text", text: "Production deployment cancelled." }],
        };
      }
    }

    await performDeploy(environment);
    return {
      content: [{ type: "text", text: `Deployed to ${environment} successfully.` }],
    };
  }
);
```

### URL Mode (Sensitive Data)

For OAuth flows, payments, or any operation requiring a web browser:

```typescript
const result = await srv.server.elicitInput({
  message: "Please authorize access to your GitHub account",
  url: `https://github.com/login/oauth/authorize?client_id=${CLIENT_ID}&state=${state}`,
});
```

> [!NOTE]
> Use **form mode** for simple confirmations and preferences. Use **URL mode** for OAuth, payments, and anything requiring secrets.

---

## Progress Notifications

Report progress on operations that take more than a few seconds.

### TypeScript

```typescript
server.tool(
  "bulk_import",
  "Import records from a file",
  { filePath: z.string() },
  async ({ filePath }, { meta, server: srv }) => {
    const records = await loadRecords(filePath);
    const progressToken = meta?.progressToken;
    let imported = 0;

    for (const record of records) {
      await importRecord(record);
      imported++;

      // Report progress every 10 records
      if (progressToken !== undefined && imported % 10 === 0) {
        await srv.server.notification({
          method: "notifications/progress",
          params: {
            progressToken,
            progress: imported,
            total: records.length,
            message: `Imported ${imported}/${records.length} records`,
          },
        });
      }
    }

    return {
      content: [{ type: "text", text: `Imported ${imported} records.` }],
    };
  }
);
```

### Python

```python
from mcp.server.fastmcp import Context

@mcp.tool()
async def bulk_import(file_path: str, ctx: Context) -> str:
    """Import records from a file."""
    records = load_records(file_path)

    for i, record in enumerate(records):
        await import_record(record)

        if i % 10 == 0:
            await ctx.report_progress(i, len(records), f"Imported {i}/{len(records)}")

    return f"Imported {len(records)} records."
```

### Client-Side Progress Handling

```typescript
const result = await client.callTool(
  { name: "bulk_import", arguments: { filePath: "data.csv" } },
  {
    onprogress: ({ progress, total, message }) => {
      console.log(`[${progress}/${total}] ${message}`);
    },
    resetTimeoutOnProgress: true,   // Don't timeout while progress is flowing
    maxTotalTimeout: 600_000,       // 10 min absolute max
  }
);
```

---

## Resource Subscriptions

Clients can subscribe to resources and receive notifications when they change.

### Server Side

```typescript
// Track which resources changed
function onConfigChange(key: string) {
  // Notify all connected clients that resources changed
  server.server.notification({
    method: "notifications/resources/list_changed",
  });
}

// Watch for config file changes
fs.watch("config.json", () => onConfigChange("config"));
```

### Client Side

```typescript
// Subscribe to a resource
await client.subscribeResource({ uri: "config://app" });

// Handle update notifications
client.setNotificationHandler("notifications/resources/updated", async (notification) => {
  const { uri } = notification.params;
  console.log(`Resource updated: ${uri}`);

  // Re-read the resource to get new content
  const { contents } = await client.readResource({ uri });
  console.log("New content:", contents[0].text);
});

// Unsubscribe when done
await client.unsubscribeResource({ uri: "config://app" });
```

### When to Use Subscriptions

- **Config files** — Reload when config changes
- **Database views** — Update when underlying data changes
- **File watching** — Notify when files are modified
- **Live dashboards** — Stream metrics to the AI

---

## Completions

Servers can provide auto-completion suggestions for tool and prompt arguments.

### TypeScript

```typescript
import { completable } from "@modelcontextprotocol/sdk/server/completable.js";

server.prompt(
  "query-table",
  "Generate a query for a specific table",
  {
    table: completable(
      z.string().describe("Table name"),
      async (partial) => {
        // Return matching table names as the user types
        const tables = await getTableNames();
        return tables.filter((t) => t.startsWith(partial));
      }
    ),
    columns: completable(
      z.string().optional().describe("Columns to select"),
      async (partial) => {
        return ["id", "name", "email", "created_at"]
          .filter((c) => c.startsWith(partial));
      }
    ),
  },
  ({ table, columns }) => ({
    messages: [
      {
        role: "user",
        content: {
          type: "text",
          text: `Write a SELECT query for the ${table} table${columns ? ` with columns: ${columns}` : ""}.`,
        },
      },
    ],
  })
);
```

### When to Use Completions

| Argument Type | Auto-Complete? | Example Values |
|---------------|---------------|----------------|
| Free-form text | No | User messages, queries |
| Enum-like values | Yes | Table names, file types, categories |
| File paths | Yes | Paths within a directory |
| Known identifiers | Yes | User IDs, project names |

---

## OAuth / Authorization

MCP supports OAuth 2.1 for authenticating with external services.

### Common Auth Patterns

```
Pattern 1: API Key (simplest)
├── Pass via environment variable
└── Add to request headers in your API client

Pattern 2: Bearer Token
├── Client provides token at connection time
└── Server uses it for downstream API calls

Pattern 3: OAuth 2.1 (full flow)
├── Server redirects user to auth provider
├── User authorizes in browser
├── Server receives access token
└── Server uses token for API calls
```

### Pattern 1: API Key via Environment

```typescript
const API_KEY = process.env.API_KEY;
if (!API_KEY) throw new Error("API_KEY required");

// Use in API calls
const data = await fetch("https://api.example.com/data", {
  headers: { Authorization: `Bearer ${API_KEY}` },
});
```

### Pattern 2: OAuth Client Credentials (Service-to-Service)

For servers that need to authenticate themselves (not a user) with an API:

```typescript
import { ClientCredentialsProvider } from "@modelcontextprotocol/sdk/client/auth.js";

const auth = new ClientCredentialsProvider({
  clientId: process.env.OAUTH_CLIENT_ID!,
  clientSecret: process.env.OAUTH_CLIENT_SECRET!,
  tokenEndpoint: "https://auth.example.com/oauth/token",
});

const token = await auth.getToken();
```

### Security Reminders

> [!IMPORTANT]
> - Never hardcode credentials in source code
> - Use environment variables or the client config's `env` section
> - Store tokens securely; never log them
> - Implement token refresh for long-running servers
> - Use the minimum required OAuth scopes

---

## Feature Support Matrix

Not all clients support all features. Design your server to degrade gracefully.

| Feature | Claude Desktop | Claude Code | Cursor | VS Code |
|---------|---------------|-------------|--------|---------|
| Tools | Yes | Yes | Yes | Yes |
| Resources | Yes | Yes | Partial | Partial |
| Prompts | Yes | Yes | No | No |
| Sampling | Yes | Yes | No | No |
| Elicitation | Partial | Yes | No | No |
| Subscriptions | Yes | Yes | No | No |
| Completions | Yes | Partial | No | No |
| Progress | Yes | Yes | Partial | Partial |

> This matrix changes as clients add features. Always test with your target client.

### Defensive Pattern

```typescript
// Always provide fallbacks for optional features
async function withSamplingOrFallback(server, prompt, fallback) {
  try {
    const result = await server.server.createMessage({
      messages: [{ role: "user", content: { type: "text", text: prompt } }],
      maxTokens: 200,
    });
    return result.content.type === "text" ? result.content.text : fallback;
  } catch {
    // Client doesn't support sampling — use the fallback
    return fallback;
  }
}
```

## What's Next

You've now covered every major MCP feature. Go build something:

- **[Examples](../examples/)** — See these concepts in action
- **[Recipes](../recipes/)** — Copy-paste solutions for common tasks
- **[Templates](../templates/)** — Start building from a working base
