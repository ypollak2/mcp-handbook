# Hello Server — MCP Example in Go

A minimal MCP server in Go using the [mcp-go](https://github.com/mark3labs/mcp-go) SDK.

## What It Demonstrates

- **Tools**: `count_words`, `reverse_text`
- **Resources**: `info://server`
- **Pattern**: Stdio transport, tool registration with typed parameters

## Quick Start

```bash
go mod tidy
go run main.go
```

## Test with Inspector

```bash
npx @modelcontextprotocol/inspector go run main.go
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "hello-go": {
      "command": "go",
      "args": ["run", "/absolute/path/to/main.go"]
    }
  }
}
```

## Go MCP SDK

The Go MCP ecosystem uses [mark3labs/mcp-go](https://github.com/mark3labs/mcp-go), the most popular Go SDK for MCP.

Key patterns:
- `server.NewMCPServer()` — create a server
- `mcp.NewTool()` — define a tool with typed parameters
- `s.AddTool(tool, handler)` — register a tool handler
- `s.AddResource(resource, handler)` — register a resource
- `server.ServeStdio(s)` — start the stdio transport

## Why Go for MCP?

- **Fast startup** — compiled binary starts instantly (no runtime overhead)
- **Single binary** — distribute one file, no dependency installation needed
- **Concurrency** — goroutines handle multiple tool calls efficiently
- **Cross-compilation** — build for Linux/macOS/Windows from one machine
