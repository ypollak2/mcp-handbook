# REST API Wrapper — MCP Server Example in C#

An MCP server in C# that wraps the [HackerNews API](https://github.com/HackerNews/API), giving AI assistants the ability to browse stories, inspect comments, and look up users. No API key needed.

This is the C# version of the **API Wrapper** pattern from [Guide 03](../../../guides/03-architecture-patterns.md). Swap HackerNews for any REST API.

## What It Demonstrates

- **Tools**: `top_stories`, `get_story`, `search_user`
- **Patterns**: `HttpClient` dependency injection, timeout handling, HTML stripping, response formatting
- **C# SDK usage**: hosted server setup, attribute-based tools, stdio transport

## Quick Start

```bash
dotnet restore
dotnet run
```

No API key or database needed — uses the public HackerNews API.

## Test with Inspector

```bash
npx @modelcontextprotocol/inspector dotnet run --project HackerNewsServer.csproj
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "hackernews-csharp": {
      "command": "dotnet",
      "args": ["run", "--project", "/absolute/path/to/HackerNewsServer.csproj"]
    }
  }
}
```

## Adapting for Your API

To wrap a different REST API, modify these parts:

1. `BaseAddress` in `Program.cs`
2. `NewsTools` method names and descriptions
3. JSON record types in `Tools/NewsTools.cs`
4. Auth headers and client setup for your provider

## Key Design Decisions

1. **One tool per action** — each API capability is exposed as a specific tool
2. **Injected `HttpClient`** — transport and auth setup live in one place
3. **Friendly text output** — responses are formatted for AI consumption instead of dumping raw JSON
4. **No external dependencies** — uses the official MCP SDK plus standard .NET libraries
