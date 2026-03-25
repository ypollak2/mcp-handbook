# Web Scraper — MCP Server Example

A Python MCP server that lets AI assistants fetch and extract content from web pages. Uses only the standard library (no BeautifulSoup or requests needed).

## What It Demonstrates

- **Tools**: `get_page_text`, `get_page_links`, `get_page_title`
- **Patterns**: Timeout handling, content size limits, HTML parsing, URL resolution
- **Zero dependencies** beyond `mcp` — uses `urllib` and `html.parser` from stdlib

## Quick Start

```bash
pip install mcp
npx @modelcontextprotocol/inspector python server.py
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "web-scraper": {
      "command": "python",
      "args": ["/absolute/path/to/server.py"]
    }
  }
}
```

## Safety Features

- URL validation (http/https only)
- 1 MB download limit
- 15-second timeout
- Graceful encoding handling
- Script/style tag stripping

## Key Design Decisions

1. **stdlib only** — No external dependencies beyond `mcp`, making it easy to deploy
2. **Text extraction** — Returns clean text, not raw HTML (AI processes text better)
3. **Separate tools** — `get_page_text` vs `get_page_links` vs `get_page_title` let the AI choose what it needs
4. **Content limits** — Prevents overwhelming the AI with huge pages
