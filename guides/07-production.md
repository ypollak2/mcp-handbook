# Production Deployment

> **Time:** 20 minutes | **Level:** Advanced | **Prerequisites:** [Guide 06](06-security.md)

Taking an MCP server from local development to production requires attention to reliability, observability, and deployment mechanics.

## Deployment Options

### Option 1: Local Process (Most Common)

MCP servers typically run as local processes, started by the AI client:

```json
{
  "mcpServers": {
    "my-server": {
      "command": "node",
      "args": ["dist/index.js"],
      "env": { "NODE_ENV": "production" }
    }
  }
}
```

**Best for:** Personal tools, team-internal servers, development workflows.

### Option 2: Docker Container

For isolated, reproducible deployments:

```dockerfile
FROM node:22-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --production
COPY dist/ ./dist/
USER node
CMD ["node", "dist/index.js"]
```

```json
{
  "mcpServers": {
    "my-server": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "my-mcp-server:latest"],
      "env": { "API_KEY": "..." }
    }
  }
}
```

**Best for:** Servers with external dependencies, team distribution, security isolation.

### Option 3: Remote Server (SSE/HTTP Transport)

For shared servers that multiple clients connect to:

```typescript
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import express from "express";

const app = express();

app.get("/sse", async (req, res) => {
  const transport = new SSEServerTransport("/messages", res);
  await server.connect(transport);
});

app.post("/messages", async (req, res) => {
  // Handle incoming messages
});

app.listen(3001);
```

**Best for:** Shared team servers, servers behind authentication, cloud-hosted services.

## Logging

Good logs are essential for debugging MCP servers since you can't see the AI's perspective.

### Structured Logging

```typescript
import { stderr } from "process";

function log(level: string, message: string, data?: Record<string, unknown>) {
  const entry = {
    timestamp: new Date().toISOString(),
    level,
    message,
    ...data,
  };

  // Log to stderr — stdout is reserved for MCP protocol messages
  stderr.write(JSON.stringify(entry) + "\n");
}

// Usage
log("info", "Tool called", { tool: "query", input: sql });
log("error", "Query failed", { tool: "query", error: error.message });
```

> **Critical:** Always log to stderr. MCP uses stdout for protocol communication — any stdout logging will corrupt the protocol.

### Python Logging

```python
import logging
import sys

# Configure logging to stderr
logging.basicConfig(
    stream=sys.stderr,
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
logger = logging.getLogger("mcp-server")

@mcp.tool()
def query(sql: str) -> str:
    logger.info("Query executed", extra={"sql": sql})
    # ...
```

## Health Monitoring

Expose server health as a resource:

```typescript
const startTime = Date.now();
let toolCallCount = 0;
let errorCount = 0;

server.resource("health", "server://health", async (uri) => ({
  contents: [
    {
      uri: uri.href,
      mimeType: "application/json",
      text: JSON.stringify({
        status: "healthy",
        uptime_seconds: Math.floor((Date.now() - startTime) / 1000),
        tool_calls: toolCallCount,
        errors: errorCount,
        memory_mb: Math.floor(process.memoryUsage().heapUsed / 1024 / 1024),
      }),
    },
  ],
}));
```

## Build for Production

### TypeScript

```json
{
  "scripts": {
    "build": "tsc",
    "start": "node dist/index.js",
    "start:dev": "tsx src/index.ts"
  }
}
```

Compile to JavaScript for production — `tsx` is great for development but adds startup overhead.

### Python

```bash
# Use a virtual environment
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Or use uv for faster dependency management
uv pip install -r requirements.txt
```

## Graceful Shutdown

Handle shutdown signals to clean up resources:

```typescript
process.on("SIGINT", async () => {
  log("info", "Shutting down...");
  db.close();
  await server.close();
  process.exit(0);
});

process.on("SIGTERM", async () => {
  log("info", "Received SIGTERM, shutting down...");
  db.close();
  await server.close();
  process.exit(0);
});
```

## Production Checklist

- [ ] **Compiled** — TypeScript built to JS, Python dependencies locked
- [ ] **Logging** — Structured logging to stderr, not stdout
- [ ] **Error recovery** — Graceful degradation, no unhandled exceptions
- [ ] **Shutdown handling** — SIGINT/SIGTERM handlers clean up resources
- [ ] **Health monitoring** — Server health exposed as resource
- [ ] **Dependencies locked** — `package-lock.json` or `requirements.txt` committed
- [ ] **Security reviewed** — See [Guide 06](06-security.md) checklist
- [ ] **Tested** — See [Guide 05](05-testing.md) checklist
- [ ] **Documented** — README with setup, config, and connection instructions

## What's Next

- **[Guide 08: Performance](08-performance.md)** — Optimize for speed and efficiency
