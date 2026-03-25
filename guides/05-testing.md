# Testing MCP Servers

> **Time:** 20 minutes | **Level:** Intermediate | **Prerequisites:** [Guide 02](02-core-concepts.md)

MCP servers interact with AI clients, external APIs, and local resources. Testing them requires strategies for each layer.

## Testing Layers

```
Layer 1: Unit Tests           — Test your logic in isolation
Layer 2: MCP Protocol Tests   — Test tool/resource handlers directly
Layer 3: Integration Tests    — Test with real external services
Layer 4: Inspector Testing    — Manual testing with MCP Inspector
```

## Layer 1: Unit Tests

Test your business logic independently from MCP. This is the fastest and most reliable layer.

### TypeScript (with Vitest)

```typescript
// src/query-validator.ts
export function isSafeQuery(sql: string): boolean {
  const upper = sql.toUpperCase().trim();
  const forbidden = ["INSERT", "UPDATE", "DELETE", "DROP", "ALTER"];
  return !forbidden.some((kw) => upper.startsWith(kw));
}

// src/query-validator.test.ts
import { describe, it, expect } from "vitest";
import { isSafeQuery } from "./query-validator.js";

describe("isSafeQuery", () => {
  it("allows SELECT queries", () => {
    expect(isSafeQuery("SELECT * FROM users")).toBe(true);
    expect(isSafeQuery("select count(*) from orders")).toBe(true);
  });

  it("rejects write operations", () => {
    expect(isSafeQuery("DROP TABLE users")).toBe(false);
    expect(isSafeQuery("DELETE FROM users WHERE id = 1")).toBe(false);
    expect(isSafeQuery("INSERT INTO users VALUES (1, 'test')")).toBe(false);
  });

  it("rejects write operations hidden in mixed case", () => {
    expect(isSafeQuery("dRoP TABLE users")).toBe(false);
  });
});
```

### Python (with pytest)

```python
# test_validators.py
import pytest
from server import validate_path

def test_valid_path(tmp_path):
    """Paths within root should be accepted."""
    file = tmp_path / "test.txt"
    file.write_text("hello")
    # Should not raise
    validate_path(str(file))

def test_path_traversal(tmp_path):
    """Paths outside root should be rejected."""
    with pytest.raises(ValueError, match="outside the allowed directory"):
        validate_path("/etc/passwd")
```

## Layer 2: MCP Protocol Tests

Test your tool and resource handlers directly using the SDK's testing utilities.

### TypeScript — Testing Tool Handlers

```typescript
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createServer } from "./index.js"; // Export your server creation

describe("database-explorer tools", () => {
  let client: Client;

  beforeAll(async () => {
    const server = createServer();
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();

    await server.connect(serverTransport);

    client = new Client({ name: "test", version: "1.0.0" });
    await client.connect(clientTransport);
  });

  it("lists available tools", async () => {
    const { tools } = await client.listTools();
    const names = tools.map((t) => t.name);
    expect(names).toContain("query");
    expect(names).toContain("describe_table");
  });

  it("executes a valid query", async () => {
    const result = await client.callTool({
      name: "query",
      arguments: { sql: "SELECT 1 + 1 AS result" },
    });
    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toContain("2");
  });

  it("rejects write operations", async () => {
    const result = await client.callTool({
      name: "query",
      arguments: { sql: "DROP TABLE users" },
    });
    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("not allowed");
  });
});
```

### Python — Testing Tool Handlers

```python
import pytest
from mcp import ClientSession
from mcp.client.stdio import stdio_client

@pytest.mark.asyncio
async def test_search_files_tool():
    """Test that search_files returns results for known patterns."""
    async with stdio_client("python", ["server.py"]) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            result = await session.call_tool("search_files", {"pattern": "*.py"})
            assert not result.isError
            assert "server.py" in result.content[0].text
```

## Layer 3: Integration Tests

Test with real external services when your server wraps an API.

```typescript
describe("hackernews integration", () => {
  it("fetches real top stories", async () => {
    const result = await client.callTool({
      name: "top_stories",
      arguments: { count: 3 },
    });

    expect(result.isError).toBeFalsy();
    // Top stories should have numbered entries
    expect(result.content[0].text).toMatch(/1\./);
    expect(result.content[0].text).toMatch(/2\./);
  });
});
```

> **Tip:** Mark integration tests separately so you can run them only when needed:
> ```bash
> vitest run --grep "integration"    # TypeScript
> pytest -m integration              # Python
> ```

## Layer 4: MCP Inspector

The [MCP Inspector](https://github.com/modelcontextprotocol/inspector) is your best friend for manual testing.

```bash
# TypeScript
npx @modelcontextprotocol/inspector npx tsx src/index.ts

# Python
npx @modelcontextprotocol/inspector python server.py
```

**What to test manually:**
- [ ] All tools appear in the tool list
- [ ] Tool descriptions are clear and accurate
- [ ] Tools handle valid input correctly
- [ ] Tools handle invalid input with helpful errors
- [ ] Resources are readable
- [ ] Prompts produce useful messages

## Testing Checklist

| What to Test | Layer | Priority |
|--------------|-------|----------|
| Input validation | Unit | High |
| Error handling | Unit + Protocol | High |
| Happy path for each tool | Protocol | High |
| Resource content | Protocol | Medium |
| Timeouts | Integration | Medium |
| Rate limiting | Integration | Medium |
| Edge cases (empty input, huge input) | Unit + Protocol | Medium |
| Prompt output format | Protocol | Low |

## What's Next

- **[Guide 06: Security](06-security.md)** — Secure your server before deploying
- **[Guide 07: Production](07-production.md)** — Deploy with confidence
