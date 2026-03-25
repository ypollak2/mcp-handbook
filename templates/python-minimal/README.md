# Minimal MCP Server (Python)

A bare-bones starting point for building MCP servers in Python.

## Quick Start

```bash
pip install mcp
python server.py
```

## Test with Inspector

```bash
npx @modelcontextprotocol/inspector python server.py
```

## Connect to Claude Desktop

Add to your Claude Desktop config:

```json
{
  "mcpServers": {
    "my-server": {
      "command": "python",
      "args": ["/absolute/path/to/server.py"]
    }
  }
}
```

## What to Do Next

1. Add tools for your use case (see `server.py`)
2. Add resources for data you want to expose
3. Test with the MCP Inspector
4. Connect to your AI client of choice

See the [full handbook](../../README.md) for patterns and best practices.
