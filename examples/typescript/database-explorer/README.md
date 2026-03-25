# Database Explorer — MCP Server Example

A read-only MCP server that gives AI assistants access to query a SQLite database. Demonstrates the **Database Explorer** pattern from [Guide 03](../../../guides/03-architecture-patterns.md).

## What It Demonstrates

- **Tools**: `query` (run SQL SELECTs), `describe_table` (show table schema)
- **Resources**: `db://schema` (full database schema)
- **Prompts**: `explore-data` (guided database exploration)
- **Security**: Read-only enforcement, SQL keyword blocking, input validation
- **UX**: Formatted table output, row limits, helpful error messages

## Quick Start

```bash
npm install
npm run seed      # Create sample database with products & orders
npm run inspect   # Open in MCP Inspector
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "database-explorer": {
      "command": "npx",
      "args": ["tsx", "/absolute/path/to/src/index.ts"]
    }
  }
}
```

## Custom Database

Point to any SQLite database:

```json
{
  "mcpServers": {
    "database-explorer": {
      "command": "npx",
      "args": ["tsx", "/absolute/path/to/src/index.ts"],
      "env": {
        "DB_PATH": "/path/to/your/database.db"
      }
    }
  }
}
```

## Architecture

```
AI Client
    │
    ├── Tool: query(sql, limit?)
    │   └── Validates → Adds LIMIT → Executes → Formats table
    │
    ├── Tool: describe_table(table)
    │   └── Validates name → PRAGMA table_info → Schema + row count
    │
    ├── Resource: db://schema
    │   └── All tables and their columns
    │
    └── Prompt: explore-data
        └── Guides AI through: schema → describe → sample queries → summary
```

## Key Design Decisions

1. **Read-only by default** — Database opened with `readonly: true`, SQL keywords blocked
2. **Bounded results** — MAX_ROWS=100 prevents runaway queries
3. **Formatted output** — ASCII table format is easier for the AI to parse than raw JSON
4. **Schema as resource** — The AI can read the schema without a tool call, reducing round trips
