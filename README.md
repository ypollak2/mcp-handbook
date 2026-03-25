<div align="center">

# The MCP Handbook


### The missing developer guide for the Model Context Protocol

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![MCP Spec](https://img.shields.io/badge/MCP_Spec-2025--03-blue.svg)](https://spec.modelcontextprotocol.io)
[![TypeScript](https://img.shields.io/badge/TypeScript-Examples-3178C6.svg)](examples/typescript/)
[![Python](https://img.shields.io/badge/Python-Examples-3776AB.svg)](examples/python/)
[![Go](https://img.shields.io/badge/Go-Examples-00ADD8.svg)](examples/go/)
[![Rust](https://img.shields.io/badge/Rust-Examples-DEA584.svg)](examples/rust/)
[![Java](https://img.shields.io/badge/Java-Examples-ED8B00.svg)](examples/java/)

**84,000+ developers** starred an MCP server list. Zero had a guide on how to actually build one.

Until now.

[Get Started](#quick-start) | [Guides](#guides) | [Examples](#examples) | [Templates](#templates) | [Contribute](#contributing)

</div>

---

> [!TIP]
> **New to MCP?** Start with the [Quick Start](#quick-start) below — you'll have a working server in under 5 minutes.
>
> **Already building?** Jump to [Architecture Patterns](guides/03-architecture-patterns.md) for production-ready designs.

## Why This Exists

MCP (Model Context Protocol) is the open standard that connects AI assistants to your tools and data. It's backed by Anthropic, adopted by VS Code, Cursor, Claude Code, and dozens of AI tools.

**The problem?** The ecosystem has 1,000+ MCP servers but almost no guidance on how to build them well. Developers are reverse-engineering patterns from source code, rediscovering the same pitfalls, and shipping insecure servers to production.

**This handbook fixes that.** It's the guide we wished existed when we started building MCP servers — from "hello world" to production, covering patterns, security, testing, and real-world architecture.

> [!NOTE]
> This is a community-driven project. If something is unclear, outdated, or missing — [open an issue](../../issues) or submit a PR. Every contribution helps.

## What You'll Learn

```
You are here
    │
    ├── 🟢 Beginner ──────── What is MCP? Build your first server in 10 minutes
    │
    ├── 🟡 Intermediate ──── Architecture patterns, testing, error handling
    │
    ├── 🔴 Advanced ───────── Security hardening, production deployment, performance
    │
    └── 📋 Reference ──────── Templates, checklists, SDK comparison
```

## Quick Start

Build a working MCP server in under 5 minutes.

### TypeScript

```bash
# Create a new project
mkdir my-mcp-server && cd my-mcp-server
npm init -y
npm install @modelcontextprotocol/sdk zod
```

```typescript
// server.ts — A complete MCP server in 30 lines
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const server = new McpServer({
  name: "my-first-server",
  version: "1.0.0",
});

// Add a tool that AI assistants can call
server.tool(
  "greet",
  "Generate a greeting for someone",
  { name: z.string().describe("Name of the person to greet") },
  async ({ name }) => ({
    content: [{ type: "text", text: `Hello, ${name}! Welcome to MCP.` }],
  })
);

// Connect via stdio transport
const transport = new StdioServerTransport();
await server.connect(transport);
```

```bash
# Test it instantly with the MCP Inspector
npx @modelcontextprotocol/inspector node server.ts
```

### Python

```bash
# Create a new project
mkdir my-mcp-server && cd my-mcp-server
pip install mcp
```

```python
# server.py — A complete MCP server in 20 lines
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("my-first-server")

@mcp.tool()
def greet(name: str) -> str:
    """Generate a greeting for someone."""
    return f"Hello, {name}! Welcome to MCP."

if __name__ == "__main__":
    mcp.run()
```

```bash
# Test it instantly with the MCP Inspector
npx @modelcontextprotocol/inspector python server.py
```

> **Next step:** [Connect your server to Claude Desktop, VS Code, or Cursor →](guides/01-getting-started.md)

## Guides

| # | Guide | Level | What You'll Learn |
|---|-------|-------|-------------------|
| 01 | [Getting Started](guides/01-getting-started.md) | Beginner | MCP concepts, your first server, connecting to clients |
| 02 | [Tools, Resources & Prompts](guides/02-core-concepts.md) | Beginner | The three building blocks of MCP servers |
| 03 | [Architecture Patterns](guides/03-architecture-patterns.md) | Intermediate | Server design patterns for common use cases |
| 04 | [Error Handling](guides/04-error-handling.md) | Intermediate | Graceful failures, retries, and user-friendly errors |
| 05 | [Testing MCP Servers](guides/05-testing.md) | Intermediate | Test harness, mocking, property-based testing, CI/CD pipelines |
| 06 | [Security & Auth](guides/06-security.md) | Advanced | OAuth, RBAC, prompt injection defense, audit logging, SSRF prevention |
| 07 | [Production Deployment](guides/07-production.md) | Advanced | Docker, monitoring, scaling, and reliability |
| 08 | [Performance & Optimization](guides/08-performance.md) | Advanced | Caching, pagination, streaming large datasets |
| 09 | [Debugging](guides/09-debugging.md) | Intermediate | Inspector, logging, common issues and fixes |
| 10 | [Advanced Topics](guides/10-advanced-topics.md) | Advanced | Sampling, elicitation, OAuth, HTTP transport, subscriptions |

## Examples

Real-world MCP servers you can learn from and adapt. Each includes a README, complete source code, and instructions for connecting to AI clients.

### TypeScript

| Example | What It Demonstrates |
|---------|---------------------|
| [Database Explorer](examples/typescript/database-explorer/) | SQL queries as tools, schema as resources, read-only enforcement |
| [REST API Wrapper](examples/typescript/rest-api-wrapper/) | Turning any REST API into an MCP server (HackerNews) |

### Python

| Example | What It Demonstrates |
|---------|---------------------|
| [File Search](examples/python/file-search/) | Sandboxed file access with content search |
| [Web Scraper](examples/python/web-scraper/) | Fetching and parsing web content (stdlib only) |

### Go

| Example | What It Demonstrates |
|---------|---------------------|
| [Hello Server](examples/go/hello-server/) | Tools and resources with mcp-go SDK |

### Rust

| Example | What It Demonstrates |
|---------|---------------------|
| [Hello Server](examples/rust/hello-server/) | Trait-based server with rmcp SDK |

### Java

| Example | What It Demonstrates |
|---------|---------------------|
| [Hello Server](examples/java/hello-server/) | Builder-based server with official Java SDK |

## Recipes

Quick, copy-paste solutions for common MCP tasks. See the [full recipe index →](recipes/)

| Recipe | What It Solves |
|--------|---------------|
| [Environment Config](recipes/environment-config.md) | Load and validate env vars at startup |
| [Graceful Shutdown](recipes/graceful-shutdown.md) | Clean up resources on exit |
| [Structured Logging](recipes/structured-logging.md) | Log to stderr without breaking protocol |
| [Paginated Results](recipes/paginated-results.md) | Return large datasets in pages |
| [Long-Running Ops](recipes/long-running-operations.md) | Report progress on slow tasks |
| [Confirmation Pattern](recipes/confirmation-pattern.md) | Two-step tools for dangerous operations |
| [Batch Operations](recipes/batch-operations.md) | Handle partial failures in bulk ops |
| [REST API Client](recipes/rest-api-client.md) | Fetch wrapper with auth, retries, timeouts |
| [Database Connection](recipes/database-connection.md) | Connection pooling and lifecycle |
| [Caching Layer](recipes/caching-layer.md) | In-memory TTL cache |
| [Rate Limiter](recipes/rate-limiter.md) | Throttle external API calls |
| [Input Sanitization](recipes/input-sanitization.md) | Validate all tool inputs |
| [Path Sandboxing](recipes/path-sandboxing.md) | Restrict file access to allowed dirs |

## Templates

Copy-paste starters to skip the boilerplate.

| Template | Language | Use Case |
|----------|----------|----------|
| [Minimal Server](templates/typescript-minimal/) | TypeScript | Bare-bones starting point |
| [Minimal Server](templates/python-minimal/) | Python | Bare-bones starting point |

## Patterns at a Glance

Quick reference for common decisions:

### When to use Tools vs Resources vs Prompts

```
Use TOOLS when:          Use RESOURCES when:       Use PROMPTS when:
├─ Action has side       ├─ Exposing read-only     ├─ Providing reusable
│  effects (write,       │  data (files, configs,   │  prompt templates
│  send, create)         │  database records)       │  with parameters
├─ Needs input params    ├─ Data has a natural URI  ├─ Guiding the AI's
│  at call time          │  (file://, db://)        │  approach to a task
└─ Returns a result      └─ Client may cache or     └─ Encoding domain
   of an operation          subscribe to updates       expertise
```

### MCP Server Architecture Decision Tree

```
What does your server do?
│
├── Wraps an external API?
│   └── Use the API Wrapper pattern → examples/typescript/rest-api-wrapper/
│
├── Exposes a database?
│   └── Use the Database Explorer pattern → examples/typescript/database-explorer/
│
├── Accesses the file system?
│   └── Use the File Access pattern → examples/python/file-search/
│
├── Combines multiple data sources?
│   └── Use the Aggregator pattern → guides/03-architecture-patterns.md
│
└── Something else?
    └── Start with the Minimal template → templates/typescript-minimal/
```

## SDK Comparison

| | TypeScript | Python | Go | Rust | Java |
|---|---|---|---|---|---|
| **Package** | `@modelcontextprotocol/sdk` | `mcp` | `mcp-go` | `rmcp` | `io.modelcontextprotocol:sdk` |
| **Server setup** | `new McpServer(...)` | `FastMCP(...)` | `server.NewMCPServer(...)` | `impl Server` trait | `McpServer.sync(...)` |
| **Tool definition** | `server.tool()` | `@mcp.tool()` | `s.AddTool()` | `fn list_tools()` | `.tool(new SyncToolSpec)` |
| **Transport** | `StdioServerTransport` | `mcp.run()` | `server.ServeStdio()` | `stdio()` | `StdioServerTransportProvider` |
| **Maturity** | Most mature | Rapidly improving | Active | Early | Active (Spring AI) |
| **Best for** | Production servers | Quick prototypes | Fast binaries | Performance-critical | Enterprise / Spring |

## Roadmap

- [x] Quick Start guides (TypeScript + Python)
- [x] Core concept explanations (Tools, Resources, Prompts)
- [x] Architecture patterns guide
- [x] Security & authentication deep dive
- [x] Testing framework and helpers
- [ ] Production deployment guide (Docker, monitoring)
- [ ] Performance optimization guide
- [ ] Video walkthroughs
- [ ] Interactive playground

See the [full roadmap →](ROADMAP.md)

## Contributing

This handbook is a community effort. We welcome contributions of all sizes.

**Quick ways to contribute:**
- Fix a typo or improve an explanation
- Add a new example server
- Share a pattern you've discovered
- Translate a guide to another language

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

MIT License. See [LICENSE](LICENSE) for details.

---

<div align="center">

**Found this useful? Give it a &#11088; to help other developers find it.**

Built by the community, for the community.

[Report an Issue](../../issues) · [Suggest a Topic](../../issues/new?template=new-topic.md) · [Contribute](CONTRIBUTING.md)

</div>
