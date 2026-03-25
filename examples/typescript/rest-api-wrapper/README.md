# REST API Wrapper — MCP Server Example

An MCP server that wraps the [HackerNews API](https://github.com/HackerNews/API), giving AI assistants the ability to browse stories, read comments, and look up users. No API key needed.

This is a template for the **API Wrapper** pattern from [Guide 03](../../../guides/03-architecture-patterns.md). Swap HackerNews for any REST API.

## What It Demonstrates

- **Tools**: `top_stories`, `get_story`, `search_user` — one tool per logical action
- **Resources**: `hn://status` — read-only API status
- **Patterns**: Request timeouts, error translation, HTML stripping, response formatting

## Quick Start

```bash
npm install
npm run inspect   # Open in MCP Inspector
```

No API key or database needed — uses the public HackerNews API.

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "hackernews": {
      "command": "npx",
      "args": ["tsx", "/absolute/path/to/src/index.ts"]
    }
  }
}
```

## Adapting for Your API

To wrap a different REST API, modify these parts:

1. **`BASE_URL`** — Point to your API
2. **`hnFetch()`** — Rename and add your auth headers (Bearer token, API key, etc.)
3. **Tools** — Replace with your API's actions (one tool per endpoint)
4. **Resources** — Expose read-only data (API status, configs, schemas)

### Adding Authentication

```typescript
// Environment variable for the API key
const API_KEY = process.env.MY_API_KEY;
if (!API_KEY) throw new Error("MY_API_KEY environment variable is required");

async function apiFetch<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      "Content-Type": "application/json",
    },
  });
  // ...
}
```

## Key Design Decisions

1. **One tool per action** — `top_stories`, `get_story`, `search_user` instead of a generic `call_api` tool
2. **Timeouts on every request** — 10s timeout prevents hanging connections
3. **Formatted text output** — AI processes formatted text better than raw JSON
4. **HTML stripping** — HN returns HTML in comments; we clean it for the AI
