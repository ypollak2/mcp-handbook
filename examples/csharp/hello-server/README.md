# Hello Server — MCP Example in C#

A minimal MCP server in C# using the [official MCP C# SDK](https://github.com/modelcontextprotocol/csharp-sdk).

## What It Demonstrates

- **Tools**: `count_words`, `reverse_text`
- **Pattern**: Hosted server setup, attribute-based tool registration, stdio transport

## Quick Start

```bash
dotnet restore
dotnet run
```

## Test with Inspector

```bash
npx @modelcontextprotocol/inspector dotnet run --project HelloServer.csproj
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "hello-csharp": {
      "command": "dotnet",
      "args": ["run", "--project", "/absolute/path/to/HelloServer.csproj"]
    }
  }
}
```

## C# MCP SDK

The official `ModelContextProtocol` package provides:

| Feature | API |
|---------|-----|
| Hosted server | `builder.Services.AddMcpServer()` |
| Tool registration | `[McpServerToolType]` + `[McpServerTool]` |
| Stdio transport | `.WithStdioServerTransport()` |
| ASP.NET transport | `ModelContextProtocol.AspNetCore` |

## Why C# for MCP?

- **.NET ecosystem** — easy integration with existing services, workers, and internal tooling
- **Official SDK** — maintained in collaboration with Microsoft
- **Hosting model** — uses familiar dependency injection and logging patterns
- **Enterprise fit** — strong option for Windows shops and backend teams already on .NET
