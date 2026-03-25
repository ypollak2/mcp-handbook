# File Search — MCP Server Example

A Python MCP server that gives AI assistants sandboxed access to search, read, and explore files on disk. Demonstrates the **File System Server** pattern from [Guide 03](../../../guides/03-architecture-patterns.md).

## What It Demonstrates

- **Tools**: `search_files`, `read_file`, `search_content`, `list_directory`
- **Resources**: `file://overview` — directory statistics
- **Security**: Path validation, sandboxed root directory, file size limits
- **Python patterns**: `@mcp.tool()` decorators, type hints as schema

## Quick Start

```bash
pip install mcp
python server.py                                         # Serve current directory
FILE_ROOT=/path/to/project python server.py              # Serve a specific directory
npx @modelcontextprotocol/inspector python server.py     # Open in Inspector
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "file-search": {
      "command": "python",
      "args": ["/absolute/path/to/server.py"],
      "env": {
        "FILE_ROOT": "/path/to/your/project"
      }
    }
  }
}
```

## Security

The server is sandboxed to `FILE_ROOT` (defaults to current directory):

- All paths are resolved and validated against the root
- Path traversal attacks (`../../etc/passwd`) are blocked
- Files larger than 10 MB are rejected
- Binary files are handled gracefully with `errors="replace"`

## Key Design Decisions

1. **Sandboxed by default** — Cannot access files outside `FILE_ROOT`
2. **Relative paths in output** — Cleaner, more readable for the AI
3. **Content search** — `search_content` acts like `grep`, searching inside files
4. **Size awareness** — Shows file sizes in directory listings, rejects huge files
