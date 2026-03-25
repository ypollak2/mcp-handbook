# REST API Wrapper — MCP Server Example in Kotlin

An MCP server in Kotlin that wraps the [HackerNews API](https://github.com/HackerNews/API), giving AI assistants the ability to browse stories, inspect comments, and look up users. No API key needed.

This is the Kotlin version of the **API Wrapper** pattern from [Guide 03](../../../guides/03-architecture-patterns.md). Swap HackerNews for any REST API.

## What It Demonstrates

- **Tools**: `top_stories`, `get_story`, `search_user`
- **Patterns**: Ktor `HttpClient`, JSON schemas for tool inputs, read-only/open-world tool annotations, response formatting
- **Kotlin SDK usage**: coroutine-friendly server setup, stdio transport, Ktor-backed API calls

## Quick Start

```bash
gradle run
```

No API key or database needed — uses the public HackerNews API.

## Test with Inspector

```bash
npx @modelcontextprotocol/inspector gradle run
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "hackernews-kotlin": {
      "command": "gradle",
      "args": ["run"],
      "cwd": "/absolute/path/to/examples/kotlin/rest-api-wrapper"
    }
  }
}
```

## Adapting for Your API

To wrap a different REST API, modify these parts:

1. The `BaseAddress` and headers in `createHttpClient()`
2. The tool names, descriptions, and input schemas in `registerNewsTools()`
3. The `@Serializable` data models for your API responses
4. Any auth or retry behavior you need in the Ktor client

## Key Design Decisions

1. **One tool per action** — each API capability is exposed as a focused tool
2. **Ktor client** — keeps HTTP, JSON, and coroutine flow in one Kotlin-native stack
3. **Explicit input schemas** — clients can infer how to call each tool correctly
4. **Friendly text output** — responses are optimized for AI consumption, not raw API dumps
