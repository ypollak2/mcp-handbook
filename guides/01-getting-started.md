# Getting Started with MCP

> **Time:** 15 minutes | **Level:** Beginner | **Prerequisites:** Node.js 18+ or Python 3.10+

## What is MCP?

MCP (Model Context Protocol) is an open protocol that lets AI assistants connect to external tools and data sources. Think of it as **USB for AI** — a standardized way to plug any tool into any AI assistant.

```
┌──────────────┐         MCP          ┌──────────────┐
│  AI Client   │ ◄──── protocol ────► │  MCP Server  │
│              │                      │              │
│ Claude Code  │   JSON-RPC over      │ Your tool,   │
│ VS Code      │   stdio or HTTP      │ API, database│
│ Cursor       │                      │ file system  │
└──────────────┘                      └──────────────┘
```

**Without MCP:** Each AI tool builds custom integrations. N tools × M data sources = N×M integrations.

**With MCP:** Each tool speaks MCP. N tools + M servers = N+M integrations. Build once, connect everywhere.

## Key Concepts (30-Second Version)

| Concept | What It Is | Example |
|---------|-----------|---------|
| **Server** | A program that exposes capabilities via MCP | A database server, a GitHub server |
| **Client** | An AI application that connects to servers | Claude Desktop, VS Code, Cursor |
| **Tool** | A function the AI can call | `run_query`, `create_file`, `send_email` |
| **Resource** | Read-only data the AI can access | `file://config.json`, `db://users/123` |
| **Prompt** | A reusable prompt template | "Analyze this codebase", "Review this PR" |

> We'll go deeper on Tools, Resources, and Prompts in [Guide 02](02-core-concepts.md).

## Build Your First Server

### Option A: TypeScript

**1. Set up the project:**

```bash
mkdir my-first-mcp && cd my-first-mcp
npm init -y
npm install @modelcontextprotocol/sdk zod
```

**2. Create `server.ts`:**

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

// Create the server
const server = new McpServer({
  name: "word-counter",
  version: "1.0.0",
});

// Register a tool — this is what the AI assistant can call
server.tool(
  "count_words",
  "Count the number of words in a text",
  {
    text: z.string().describe("The text to count words in"),
  },
  async ({ text }) => {
    const wordCount = text.trim().split(/\s+/).length;
    return {
      content: [
        {
          type: "text",
          text: `The text contains ${wordCount} words.`,
        },
      ],
    };
  }
);

// Register a resource — read-only data the AI can access
server.resource("server-info", "info://server", async (uri) => ({
  contents: [
    {
      uri: uri.href,
      mimeType: "text/plain",
      text: "Word Counter MCP Server v1.0.0 — counts words in text.",
    },
  ],
}));

// Start the server
const transport = new StdioServerTransport();
await server.connect(transport);
```

**3. Test with MCP Inspector:**

```bash
npx @modelcontextprotocol/inspector npx tsx server.ts
```

The Inspector opens in your browser. You can see your tool, call it with test input, and see the response.

### Option B: Python

**1. Set up the project:**

```bash
mkdir my-first-mcp && cd my-first-mcp
python -m venv .venv && source .venv/bin/activate
pip install mcp
```

**2. Create `server.py`:**

```python
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("word-counter")


@mcp.tool()
def count_words(text: str) -> str:
    """Count the number of words in a text."""
    word_count = len(text.strip().split())
    return f"The text contains {word_count} words."


@mcp.resource("info://server")
def server_info() -> str:
    """Information about this server."""
    return "Word Counter MCP Server v1.0.0 — counts words in text."


if __name__ == "__main__":
    mcp.run()
```

**3. Test with MCP Inspector:**

```bash
npx @modelcontextprotocol/inspector python server.py
```

## Connect to a Client

Now let's connect your server to a real AI assistant.

### Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "word-counter": {
      "command": "npx",
      "args": ["tsx", "/absolute/path/to/server.ts"]
    }
  }
}
```

Restart Claude Desktop. You'll see the tool icon indicating your server is connected.

### Claude Code

Add to your project's `.mcp.json`:

```json
{
  "mcpServers": {
    "word-counter": {
      "command": "npx",
      "args": ["tsx", "/absolute/path/to/server.ts"]
    }
  }
}
```

Or add it globally in `~/.claude.json`.

### VS Code (Copilot)

Add to `.vscode/mcp.json` in your workspace:

```json
{
  "servers": {
    "word-counter": {
      "command": "npx",
      "args": ["tsx", "${workspaceFolder}/server.ts"]
    }
  }
}
```

### Cursor

Add to `.cursor/mcp.json` in your project:

```json
{
  "mcpServers": {
    "word-counter": {
      "command": "npx",
      "args": ["tsx", "/absolute/path/to/server.ts"]
    }
  }
}
```

## Common Gotchas

| Problem | Cause | Fix |
|---------|-------|-----|
| Server not showing up | Config path is wrong | Use absolute paths, not relative |
| "Connection refused" | Server crashed on startup | Run server manually first to see errors |
| Tool not appearing | Client doesn't support tool listing | Try a different client or update |
| "Module not found" | Dependencies not installed | Run `npm install` or `pip install` in server directory |

## What's Next

You've built a working MCP server and connected it to a client. Here's where to go from here:

- **[Guide 02: Core Concepts](02-core-concepts.md)** — Deep dive into Tools, Resources, and Prompts
- **[Guide 03: Architecture Patterns](03-architecture-patterns.md)** — Design patterns for real-world servers
- **[Examples](../examples/)** — Learn from complete, production-style servers
