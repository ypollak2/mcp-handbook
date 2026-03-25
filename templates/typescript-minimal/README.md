# Minimal MCP Server (TypeScript)

A bare-bones starting point for building MCP servers in TypeScript.

## Quick Start

```bash
npm install
npm start
```

## Test with Inspector

```bash
npm run inspect
```

## Connect to Claude Desktop

Add to your Claude Desktop config:

```json
{
  "mcpServers": {
    "my-server": {
      "command": "npx",
      "args": ["tsx", "/absolute/path/to/src/index.ts"]
    }
  }
}
```

## What to Do Next

1. Add tools for your use case (see `src/index.ts`)
2. Add resources for data you want to expose
3. Test with the MCP Inspector
4. Connect to your AI client of choice

See the [full handbook](../../README.md) for patterns and best practices.
