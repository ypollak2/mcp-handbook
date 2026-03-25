<div align="center">

# The MCP Handbook

### The missing developer guide for the Model Context Protocol

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![MCP Spec](https://img.shields.io/badge/MCP_Spec-2025--03-blue.svg)](https://spec.modelcontextprotocol.io)
[![TypeScript](https://img.shields.io/badge/TypeScript-Examples-3178C6.svg)](examples/typescript/)
[![Python](https://img.shields.io/badge/Python-Examples-3776AB.svg)](examples/python/)

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
| 05 | [Testing MCP Servers](guides/05-testing.md) | Intermediate | Unit, integration, and end-to-end testing strategies |
| 06 | [Security & Auth](guides/06-security.md) | Advanced | Credential management, input validation, sandboxing |
| 07 | [Production Deployment](guides/07-production.md) | Advanced | Docker, monitoring, scaling, and reliability |
| 08 | [Performance & Optimization](guides/08-performance.md) | Advanced | Caching, pagination, streaming large datasets |

## Examples

Real-world MCP servers you can learn from and adapt.

| Example | Language | What It Demonstrates |
|---------|----------|---------------------|
| [Database Explorer](examples/typescript/database-explorer/) | TypeScript | SQL queries as tools, schema as resources |
| [REST API Wrapper](examples/typescript/rest-api-wrapper/) | TypeScript | Turning any REST API into an MCP server |
| [File Search](examples/python/file-search/) | Python | File system access with glob patterns |
| [Web Scraper](examples/python/web-scraper/) | Python | Fetching and parsing web content |

> Each example includes a README, complete source code, tests, and instructions for connecting to Claude Desktop.

## Templates

Copy-paste starters to skip the boilerplate.

| Template | Language | Use Case |
|----------|----------|----------|
| [Minimal Server](templates/typescript-minimal/) | TypeScript | Bare-bones starting point |
| [Minimal Server](templates/python-minimal/) | Python | Bare-bones starting point |
| [API Wrapper](templates/typescript-api-wrapper/) | TypeScript | Wrap any REST API |
| [Database Server](templates/typescript-database/) | TypeScript | Expose a database via MCP |

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

| Feature | TypeScript SDK | Python SDK |
|---------|---------------|------------|
| Package | `@modelcontextprotocol/sdk` | `mcp` |
| Server class | `McpServer` | `FastMCP` |
| Transport | `StdioServerTransport` | `stdio_server()` |
| Tool definition | `server.tool(name, desc, schema, handler)` | `@mcp.tool()` decorator |
| Resource definition | `server.resource(name, uri, handler)` | `@mcp.resource()` decorator |
| Maturity | More mature, more examples | Newer, rapidly improving |
| Best for | Production servers, complex logic | Quick prototypes, simple servers |

## Roadmap

- [x] Quick Start guides (TypeScript + Python)
- [x] Core concept explanations (Tools, Resources, Prompts)
- [x] Architecture patterns guide
- [ ] Security & authentication deep dive
- [ ] Testing framework and helpers
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
