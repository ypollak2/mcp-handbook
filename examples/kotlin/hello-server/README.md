# Hello Server — MCP Example in Kotlin

A minimal MCP server in Kotlin using the [official MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk).

## What It Demonstrates

- **Tools**: `count_words`, `reverse_text`
- **Pattern**: Coroutine-friendly server setup, stdio transport, typed Kotlin project with Gradle

## Quick Start

```bash
gradle run
```

## Test with Inspector

```bash
npx @modelcontextprotocol/inspector gradle run
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "hello-kotlin": {
      "command": "gradle",
      "args": ["run"],
      "cwd": "/absolute/path/to/examples/kotlin/hello-server"
    }
  }
}
```

## Kotlin MCP SDK

The official `io.modelcontextprotocol:kotlin-sdk-server` package provides:

| Feature | API |
|---------|-----|
| Server setup | `Server(Implementation(...), ServerOptions(...))` |
| Tool registration | `server.addTool(...)` |
| Stdio transport | `StdioServerTransport(...)` |
| HTTP transport | Ktor-based streamable HTTP and SSE support |

## Why Kotlin for MCP?

- **Official SDK** — maintained in collaboration with JetBrains
- **Coroutines** — natural fit for async tools and I/O-heavy servers
- **JVM ecosystem** — integrates cleanly with existing Java/Kotlin backends
- **Multiplatform path** — same SDK family also targets JS, Wasm, and native
