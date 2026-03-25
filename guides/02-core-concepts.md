# Tools, Resources & Prompts

> **Time:** 20 minutes | **Level:** Beginner | **Prerequisites:** [Guide 01](01-getting-started.md)

MCP servers expose three types of capabilities. Choosing the right one for each situation is the most important design decision you'll make.

## The Three Primitives

```
MCP Server Capabilities
│
├── Tools ──────── Functions the AI can call (read + write)
│   "Do something"
│
├── Resources ──── Data the AI can read (read-only)
│   "Look at something"
│
└── Prompts ────── Templates that guide the AI's behavior
    "Think about something this way"
```

## Tools

Tools are **functions with side effects** that the AI can invoke. They're the most common MCP primitive.

### When to Use Tools
- Performing actions (create, update, delete)
- Running computations
- Calling external APIs
- Anything that takes input and returns a result

### Anatomy of a Tool

Every tool has four parts:

```
┌─────────────────────────────────────────┐
│ Tool: "run_query"                       │
├─────────────────────────────────────────┤
│ Name         │ Unique identifier        │
│ Description  │ What the tool does       │
│ Input Schema │ Parameters (JSON Schema) │
│ Handler      │ The function that runs   │
└─────────────────────────────────────────┘
```

### TypeScript Example

```typescript
import { z } from "zod";

server.tool(
  // Name — the AI uses this to decide when to call the tool
  "search_files",

  // Description — helps the AI understand when this tool is useful
  "Search for files matching a glob pattern in the project directory",

  // Input schema — validated automatically by the SDK
  {
    pattern: z.string().describe("Glob pattern like '**/*.ts' or 'src/**/*.json'"),
    maxResults: z.number().optional().default(20).describe("Maximum results to return"),
  },

  // Handler — the actual logic
  async ({ pattern, maxResults }) => {
    const files = await glob(pattern, { maxResults });
    return {
      content: [
        {
          type: "text",
          text: files.length > 0
            ? files.join("\n")
            : `No files found matching "${pattern}"`,
        },
      ],
    };
  }
);
```

### Python Example

```python
@mcp.tool()
def search_files(pattern: str, max_results: int = 20) -> str:
    """Search for files matching a glob pattern in the project directory.

    Args:
        pattern: Glob pattern like '**/*.ts' or 'src/**/*.json'
        max_results: Maximum results to return
    """
    import glob as g
    files = g.glob(pattern, recursive=True)[:max_results]
    return "\n".join(files) if files else f'No files found matching "{pattern}"'
```

### Tool Design Tips

| Do | Don't |
|----|-------|
| Write clear descriptions — the AI reads them | Use vague names like `do_thing` |
| Validate inputs with schemas | Trust input blindly |
| Return structured, readable output | Return raw JSON dumps without context |
| Handle errors gracefully with helpful messages | Let exceptions propagate as stack traces |
| Keep tools focused — one action per tool | Build mega-tools that do everything |

## Resources

Resources are **read-only data** identified by URIs. They let the AI access information without side effects.

### When to Use Resources
- Exposing files, configs, or documentation
- Providing database records or API data
- Sharing state or context information
- Anything the AI needs to _read_ but not _modify_

### Resource URIs

Resources use URI schemes to identify data:

```
file://path/to/file.txt         — File contents
db://users/123                  — Database record
config://app/settings           — Configuration data
docs://api/endpoints            — Documentation
```

### TypeScript Example

```typescript
// Static resource — fixed URI, always available
server.resource(
  "app-config",
  "config://app/settings",
  async (uri) => ({
    contents: [
      {
        uri: uri.href,
        mimeType: "application/json",
        text: JSON.stringify(await loadConfig(), null, 2),
      },
    ],
  })
);

// Dynamic resources — URI pattern with parameters
server.resource(
  "user-profile",
  "db://users/{userId}",
  async (uri, { userId }) => {
    const user = await db.users.findById(userId);
    return {
      contents: [
        {
          uri: uri.href,
          mimeType: "application/json",
          text: JSON.stringify(user, null, 2),
        },
      ],
    };
  }
);
```

### Python Example

```python
@mcp.resource("config://app/settings")
def app_config() -> str:
    """Application configuration settings."""
    import json
    config = load_config()
    return json.dumps(config, indent=2)


@mcp.resource("db://users/{user_id}")
def user_profile(user_id: str) -> str:
    """User profile data."""
    import json
    user = db.users.find_by_id(user_id)
    return json.dumps(user, indent=2)
```

### Resources vs Tools — The Decision

```
Does the operation modify data or have side effects?
│
├── YES → Use a Tool
│   Examples: send_email, create_file, run_query
│
└── NO → Use a Resource
    Examples: read config, get user profile, list files
```

**Why this matters:** Clients can freely read resources without asking user permission. Tools require explicit approval because they can change things. Getting this right improves both security and UX.

## Prompts

Prompts are **reusable templates** that encode domain expertise. They guide how the AI approaches a task.

### When to Use Prompts
- Encoding best practices for common tasks
- Providing multi-step workflows
- Injecting domain context the AI wouldn't know
- Standardizing how the AI handles specific situations

### TypeScript Example

```typescript
server.prompt(
  "review-sql",
  "Review a SQL query for performance and security issues",
  {
    query: z.string().describe("The SQL query to review"),
    dialect: z.enum(["postgres", "mysql", "sqlite"]).optional().default("postgres"),
  },
  ({ query, dialect }) => ({
    messages: [
      {
        role: "user",
        content: {
          type: "text",
          text: [
            `Review this ${dialect} SQL query for performance and security issues:`,
            "",
            "```sql",
            query,
            "```",
            "",
            "Check for:",
            "1. SQL injection vulnerabilities",
            "2. Missing indexes (suggest based on WHERE/JOIN clauses)",
            "3. N+1 query patterns",
            "4. Unnecessary SELECT * usage",
            "5. Missing LIMIT clauses on unbounded queries",
          ].join("\n"),
        },
      },
    ],
  })
);
```

### Python Example

```python
from mcp.server.fastmcp import FastMCP
from mcp.server.fastmcp.prompts import base

@mcp.prompt()
def review_sql(query: str, dialect: str = "postgres") -> list[base.Message]:
    """Review a SQL query for performance and security issues."""
    return [
        base.UserMessage(
            content=f"""Review this {dialect} SQL query for performance and security issues:

```sql
{query}
```

Check for:
1. SQL injection vulnerabilities
2. Missing indexes (suggest based on WHERE/JOIN clauses)
3. N+1 query patterns
4. Unnecessary SELECT * usage
5. Missing LIMIT clauses on unbounded queries"""
        )
    ]
```

## Putting It All Together

A well-designed MCP server often combines all three primitives:

```
Database MCP Server
│
├── Tools
│   ├── run_query      — Execute a SQL query
│   ├── create_table   — Create a new table
│   └── insert_record  — Insert data
│
├── Resources
│   ├── db://schema           — Full database schema
│   ├── db://tables/{name}    — Table structure and sample data
│   └── db://stats            — Query statistics and performance
│
└── Prompts
    ├── review-sql     — Review a query for issues
    └── optimize-query — Suggest optimizations for slow queries
```

## What's Next

- **[Guide 03: Architecture Patterns](03-architecture-patterns.md)** — Common server designs for real-world use cases
- **[Examples](../examples/)** — See these concepts applied in complete servers
