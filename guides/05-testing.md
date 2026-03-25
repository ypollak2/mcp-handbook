# Testing MCP Servers

> **Time:** 30 minutes | **Level:** Intermediate | **Prerequisites:** [Guide 02](02-core-concepts.md)

MCP servers interact with AI clients, external APIs, and local resources. Testing them requires strategies for each layer — from fast unit tests to full protocol-level integration tests.

## Testing Layers

```
Layer 1: Unit Tests           — Test your logic in isolation (fastest)
Layer 2: MCP Protocol Tests   — Test tool/resource handlers via in-memory transport
Layer 3: Integration Tests    — Test with real external services
Layer 4: Property-Based Tests — Fuzz inputs to find edge cases
Layer 5: Inspector / E2E      — Manual and CI smoke testing
```

## The Test Harness

Before writing tests, set up a reusable harness that wires a server and client together in-memory. This is the foundation for all protocol-level tests.

### TypeScript — Reusable Test Harness

```typescript
// test/harness.ts
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";

export interface TestHarness {
  server: McpServer;
  client: Client;
  cleanup: () => Promise<void>;
}

/**
 * Creates a wired server+client pair for testing.
 * Pass a setup function to register tools/resources on the server.
 */
export async function createTestHarness(
  setup: (server: McpServer) => void
): Promise<TestHarness> {
  const server = new McpServer({
    name: "test-server",
    version: "0.0.1",
  });

  setup(server);

  const [clientTransport, serverTransport] =
    InMemoryTransport.createLinkedPair();

  await server.connect(serverTransport);

  const client = new Client({ name: "test-client", version: "0.0.1" });
  await client.connect(clientTransport);

  return {
    server,
    client,
    cleanup: async () => {
      await client.close();
      await server.close();
    },
  };
}

// Response builders for cleaner test assertions
export function textResult(text: string) {
  return { content: [{ type: "text" as const, text }] };
}

export function errorResult(message: string) {
  return { content: [{ type: "text" as const, text: message }], isError: true };
}
```

### Python — Reusable Test Harness

```python
# tests/conftest.py
import pytest
import anyio
from mcp.server import Server
from mcp.client.session import ClientSession
from mcp.server.session import ServerSession
from mcp.types import JSONRPCMessage, Tool, TextContent


class MCPTestHarness:
    """Encapsulates a connected server+client pair for testing."""
    def __init__(self, client: ClientSession, server: Server):
        self.client = client
        self.server = server


@pytest.fixture
async def harness(request):
    """
    Creates an in-process server+client connected via memory streams.

    Usage:
        @pytest.mark.parametrize("harness", [setup_fn], indirect=True)
        async def test_something(harness):
            result = await harness.client.call_tool(...)
    """
    setup_fn = getattr(request, "param", None)
    server = Server(name="test-server")

    if setup_fn:
        setup_fn(server)

    s2c_send, s2c_recv = anyio.create_memory_object_stream[JSONRPCMessage](0)
    c2s_send, c2s_recv = anyio.create_memory_object_stream[JSONRPCMessage](0)

    async with anyio.create_task_group() as tg:
        session = ServerSession(c2s_recv, s2c_send, server)
        tg.start_soon(session.run)

        async with ClientSession(s2c_recv, c2s_send) as client:
            await client.initialize()
            yield MCPTestHarness(client=client, server=server)

        tg.cancel_scope.cancel()


# Helper factories
def text_result(text: str) -> list[TextContent]:
    return [TextContent(type="text", text=text)]


def tool_factory(name: str, description: str = "A test tool", **props) -> Tool:
    return Tool(
        name=name,
        description=description,
        inputSchema={
            "type": "object",
            "properties": {k: {"type": "string"} for k in props},
            "required": list(props.keys()),
        },
    )
```

> **Why in-memory?** `InMemoryTransport` (TypeScript) and `anyio` memory streams (Python) give you 95% of the confidence of E2E tests at 1% of the execution time. No subprocesses, no network, deterministic results.

---

## Layer 1: Unit Tests

Test your business logic independently from MCP. This is the fastest and most reliable layer.

### TypeScript (Vitest)

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

### Python (pytest)

```python
# test_validators.py
import pytest
from server import validate_path

def test_valid_path(tmp_path):
    """Paths within root should be accepted."""
    file = tmp_path / "test.txt"
    file.write_text("hello")
    validate_path(str(file))  # Should not raise

def test_path_traversal(tmp_path):
    """Paths outside root should be rejected."""
    with pytest.raises(ValueError, match="outside the allowed directory"):
        validate_path("/etc/passwd")
```

---

## Layer 2: MCP Protocol Tests

Test your tool and resource handlers through the full MCP protocol using the test harness. This catches serialization issues, schema validation bugs, and protocol-level errors that unit tests miss.

### TypeScript — Testing Tool Handlers

```typescript
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { createTestHarness, type TestHarness } from "./harness.js";
import { z } from "zod";

describe("database-explorer tools", () => {
  let harness: TestHarness;

  beforeAll(async () => {
    harness = await createTestHarness((server) => {
      server.tool(
        "query",
        "Execute a SQL query",
        { sql: z.string() },
        async ({ sql }) => {
          if (sql.toUpperCase().startsWith("DROP")) {
            return {
              content: [{ type: "text", text: "Write operations not allowed" }],
              isError: true,
            };
          }
          return { content: [{ type: "text", text: `Result of: ${sql}` }] };
        }
      );

      server.tool(
        "describe_table",
        "Describe a database table",
        { table: z.string() },
        async ({ table }) => ({
          content: [{ type: "text", text: `Schema for ${table}: id, name` }],
        })
      );
    });
  });

  afterAll(() => harness.cleanup());

  it("lists available tools", async () => {
    const { tools } = await harness.client.listTools();
    const names = tools.map((t) => t.name);
    expect(names).toContain("query");
    expect(names).toContain("describe_table");
  });

  it("executes a valid query", async () => {
    const result = await harness.client.callTool({
      name: "query",
      arguments: { sql: "SELECT 1 + 1 AS result" },
    });
    expect(result.isError).toBeFalsy();
    expect(result.content[0].text).toContain("SELECT 1 + 1");
  });

  it("rejects write operations", async () => {
    const result = await harness.client.callTool({
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
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("test-server")

@mcp.tool()
def add(a: int, b: int) -> str:
    """Add two numbers."""
    return str(a + b)

# Direct unit test — no transport needed
def test_add_direct():
    assert add(2, 3) == "5"
    assert add(-1, 1) == "0"

# Protocol-level test — via in-process client
@pytest.mark.anyio
async def test_add_via_protocol(harness):
    result = await harness.client.call_tool("add", {"a": 2, "b": 3})
    assert result.content[0].text == "5"
```

### Testing Resources

```typescript
describe("resource tests", () => {
  let harness: TestHarness;

  beforeAll(async () => {
    harness = await createTestHarness((server) => {
      server.resource("config://app", "App configuration", async () => ({
        contents: [{
          uri: "config://app",
          text: JSON.stringify({ debug: false, version: "1.0.0" }),
          mimeType: "application/json",
        }],
      }));
    });
  });

  afterAll(() => harness.cleanup());

  it("reads a resource", async () => {
    const { contents } = await harness.client.readResource({ uri: "config://app" });
    const config = JSON.parse(contents[0].text);
    expect(config.version).toBe("1.0.0");
  });

  it("lists resources", async () => {
    const { resources } = await harness.client.listResources();
    expect(resources.some((r) => r.uri === "config://app")).toBe(true);
  });
});
```

---

## Layer 3: Mocking External Services

When your server wraps an external API, mock it to keep tests fast and deterministic.

### TypeScript — Vitest Mocking

```typescript
import { vi, describe, it, expect, beforeEach } from "vitest";

// Mock the external service module
vi.mock("../services/weather-api.js", () => ({
  getWeather: vi.fn(),
}));

import * as weatherApi from "../services/weather-api.js";

describe("weather tool with mocked API", () => {
  let harness: TestHarness;

  beforeEach(async () => {
    harness = await createTestHarness((server) => {
      server.tool("get_weather", "Get weather", { city: z.string() }, async ({ city }) => {
        const weather = await weatherApi.getWeather(city);
        return { content: [{ type: "text", text: JSON.stringify(weather) }] };
      });
    });
  });

  afterEach(() => harness.cleanup());

  it("returns weather data from mocked API", async () => {
    vi.mocked(weatherApi.getWeather).mockResolvedValue({
      temp: 72,
      condition: "sunny",
    });

    const result = await harness.client.callTool({
      name: "get_weather",
      arguments: { city: "Austin" },
    });

    expect(JSON.parse(result.content[0].text)).toEqual({
      temp: 72,
      condition: "sunny",
    });
    expect(weatherApi.getWeather).toHaveBeenCalledWith("Austin");
  });

  it("handles API errors gracefully", async () => {
    vi.mocked(weatherApi.getWeather).mockRejectedValue(
      new Error("API rate limited")
    );

    const result = await harness.client.callTool({
      name: "get_weather",
      arguments: { city: "Austin" },
    });

    expect(result.isError).toBe(true);
    // Error message should NOT leak internal details
    expect(result.content[0].text).not.toContain("rate limited");
  });
});
```

### Python — Dependency Injection Pattern (Preferred)

```python
# server.py — Design for testability
class WeatherServer:
    def __init__(self, weather_client=None):
        self.weather_client = weather_client or RealWeatherClient()

    async def get_weather(self, city: str) -> str:
        data = await self.weather_client.fetch(city)
        return f"{data['condition']}, {data['temp']}F"


# tests/test_weather.py — Test with a fake
class FakeWeatherClient:
    def __init__(self, responses: dict):
        self.responses = responses
        self.calls: list[str] = []

    async def fetch(self, city: str) -> dict:
        self.calls.append(city)
        if city not in self.responses:
            raise ConnectionError(f"Unknown city: {city}")
        return self.responses[city]


@pytest.mark.anyio
async def test_weather_success():
    fake = FakeWeatherClient({"Austin": {"temp": 72, "condition": "sunny"}})
    server = WeatherServer(weather_client=fake)
    result = await server.get_weather("Austin")
    assert "72" in result
    assert fake.calls == ["Austin"]


@pytest.mark.anyio
async def test_weather_api_failure():
    fake = FakeWeatherClient({})  # No responses → all cities fail
    server = WeatherServer(weather_client=fake)
    with pytest.raises(ConnectionError):
        await server.get_weather("Unknown")
```

---

## Layer 4: Property-Based Testing

Property-based tests generate hundreds of random inputs to find edge cases you'd never think to test manually. Essential for input validation.

### TypeScript — fast-check

```bash
npm install -D fast-check
```

```typescript
import * as fc from "fast-check";
import { it, expect } from "vitest";

it("add tool handles any numeric input without crashing", async () => {
  await fc.assert(
    fc.asyncProperty(fc.double(), fc.double(), async (a, b) => {
      const result = await harness.client.callTool({
        name: "add",
        arguments: { a, b },
      });

      // Must never crash — either succeeds or returns error
      expect(result).toBeDefined();

      if (!result.isError) {
        const value = Number(result.content[0].text);
        if (!Number.isNaN(a) && !Number.isNaN(b)) {
          expect(value).toBeCloseTo(a + b, 10);
        }
      }
    }),
    { numRuns: 200 }
  );
});

it("rejects all non-SELECT queries", async () => {
  const sqlVerb = fc.constantFrom(
    "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE"
  );

  await fc.assert(
    fc.asyncProperty(sqlVerb, fc.string(), async (verb, rest) => {
      const result = await harness.client.callTool({
        name: "query",
        arguments: { sql: `${verb} ${rest}` },
      });
      expect(result.isError).toBe(true);
    }),
    { numRuns: 100 }
  );
});

it("tool handles adversarial string input", async () => {
  await fc.assert(
    fc.asyncProperty(fc.string(), async (input) => {
      const result = await harness.client.callTool({
        name: "echo",
        arguments: { text: input },
      });
      // Must not crash, regardless of input
      expect(result).toBeDefined();
    }),
    { numRuns: 500 }
  );
});
```

### Python — Hypothesis

```bash
pip install hypothesis hypothesis-jsonschema
```

```python
from hypothesis import given, strategies as st, settings

# Test with valid inputs
add_args = st.fixed_dictionaries({
    "a": st.floats(allow_nan=False, allow_infinity=False),
    "b": st.floats(allow_nan=False, allow_infinity=False),
})

@given(args=add_args)
@settings(max_examples=200)
@pytest.mark.anyio
async def test_add_any_numbers(harness, args):
    result = await harness.client.call_tool("add", args)
    value = float(result.content[0].text)
    assert abs(value - (args["a"] + args["b"])) < 1e-9


# Fuzz with garbage inputs — tool must not crash
bad_input = st.one_of(
    st.none(),
    st.text(),
    st.integers(),
    st.lists(st.integers()),
    st.dictionaries(st.text(), st.text()),
)

@given(args=st.dictionaries(st.text(), bad_input))
@settings(max_examples=100)
@pytest.mark.anyio
async def test_tool_survives_garbage(harness, args):
    """Tool should return an error, not crash, on bad input."""
    result = await harness.client.call_tool("add", args)
    assert result is not None  # Must not crash


# Schema-driven fuzzing
from hypothesis_jsonschema import from_schema

@given(args=from_schema({
    "type": "object",
    "properties": {
        "a": {"type": "number"},
        "b": {"type": "number"},
    },
    "required": ["a", "b"],
}))
@settings(max_examples=200)
@pytest.mark.anyio
async def test_schema_conforming_inputs(harness, args):
    result = await harness.client.call_tool("add", args)
    assert not result.isError
```

---

## Layer 5: Snapshot Testing

Lock down tool response formats so regressions are caught immediately.

### TypeScript — Vitest Snapshots

```typescript
it("describe_table output matches snapshot", async () => {
  const result = await harness.client.callTool({
    name: "describe_table",
    arguments: { table: "users" },
  });

  // Inline snapshot — stored right in the test file
  expect(result).toMatchInlineSnapshot(`
    {
      "content": [
        {
          "text": "Schema for users: id, name, email, created_at",
          "type": "text",
        },
      ],
    }
  `);
});

// For dynamic fields, strip them before snapshotting
it("API response snapshot (stable fields only)", async () => {
  const result = await harness.client.callTool({
    name: "fetch_data",
    arguments: { id: "123" },
  });

  const content = JSON.parse(result.content[0].text);
  const { timestamp, requestId, ...stable } = content;
  expect(stable).toMatchSnapshot();
});
```

### Python — syrupy

```bash
pip install syrupy
```

```python
# Automatically generates and compares .ambr snapshot files
@pytest.mark.anyio
async def test_schema_output_snapshot(harness, snapshot):
    result = await harness.client.call_tool("describe_table", {"table": "users"})
    assert [{"type": c.type, "text": c.text} for c in result.content] == snapshot
```

---

## Error Handling Tests

Systematically test every error path. MCP servers must never crash — they should always return structured error responses.

### Error Taxonomy

```typescript
describe("error handling", () => {
  // 1. Unknown tool
  it("returns error for unknown tools", async () => {
    const result = await harness.client.callTool({
      name: "nonexistent_tool",
      arguments: {},
    });
    expect(result.isError).toBe(true);
  });

  // 2. Missing required arguments
  it("returns error for missing arguments", async () => {
    const result = await harness.client.callTool({
      name: "query",
      arguments: {}, // missing 'sql'
    });
    expect(result.isError).toBe(true);
  });

  // 3. Wrong argument types
  it("returns error for wrong types", async () => {
    const result = await harness.client.callTool({
      name: "add",
      arguments: { a: "not-a-number", b: 2 },
    });
    expect(result.isError).toBe(true);
  });

  // 4. Downstream service failure
  it("handles external service timeout", async () => {
    vi.mocked(externalApi.fetch).mockRejectedValue(new Error("ETIMEDOUT"));

    const result = await harness.client.callTool({
      name: "fetch_data",
      arguments: { id: "123" },
    });

    expect(result.isError).toBe(true);
    expect(result.content[0].text).not.toContain("ETIMEDOUT");
    expect(result.content[0].text).toContain("temporarily unavailable");
  });

  // 5. Large payloads
  it("handles very large input", async () => {
    const result = await harness.client.callTool({
      name: "echo",
      arguments: { text: "x".repeat(10_000_000) },
    });
    expect(result).toBeDefined(); // Must not crash
  });

  // 6. Concurrent requests
  it("handles concurrent tool calls", async () => {
    const results = await Promise.all(
      Array.from({ length: 50 }, (_, i) =>
        harness.client.callTool({ name: "add", arguments: { a: i, b: 1 } })
      )
    );

    results.forEach((r, i) => {
      expect(r.content[0].text).toBe(String(i + 1));
    });
  });
});
```

### Python Error Tests

```python
@pytest.mark.anyio
async def test_unknown_tool(harness):
    result = await harness.client.call_tool("nonexistent", {})
    assert result.isError

@pytest.mark.anyio
async def test_special_characters(harness):
    """Tool handles unicode, emoji, null bytes, injection attempts."""
    adversarial_inputs = [
        "",
        "  ",
        "\x00\x01\x02",
        "\U0001f600",                       # emoji
        "a" * 1_000_000,                    # very long
        "<script>alert(1)</script>",        # XSS
        "'; DROP TABLE users;--",           # SQL injection
    ]

    for inp in adversarial_inputs:
        result = await harness.client.call_tool("echo", {"text": inp})
        assert result is not None  # Must not crash

@pytest.mark.anyio
async def test_concurrent_calls(harness):
    import anyio

    results = []

    async def call_add(a, b):
        r = await harness.client.call_tool("add", {"a": a, "b": b})
        results.append((a + b, float(r.content[0].text)))

    async with anyio.create_task_group() as tg:
        for i in range(50):
            tg.start_soon(call_add, i, 1)

    for expected, actual in results:
        assert expected == actual
```

---

## CI/CD Pipeline

### GitHub Actions — TypeScript

```yaml
# .github/workflows/test.yml
name: Test MCP Server

on:
  push:
    branches: [main]
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        node-version: [18, 20, 22]

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: ${{ matrix.node-version }}
          cache: npm

      - run: npm ci

      - name: Type check
        run: npx tsc --noEmit

      - name: Unit & protocol tests
        run: npx vitest run --coverage

      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          files: ./coverage/lcov.info
```

### GitHub Actions — Python

```yaml
name: Test MCP Server

on:
  push:
    branches: [main]
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        python-version: ["3.10", "3.11", "3.12"]

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-python@v5
        with:
          python-version: ${{ matrix.python-version }}

      - run: pip install -e ".[dev]"

      - name: Lint
        run: ruff check . && ruff format --check .

      - name: Type check
        run: mypy src/

      - name: Tests with coverage
        run: pytest tests/ --cov=src --cov-report=xml -v

      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          files: ./coverage.xml
```

---

## Coverage Configuration

### TypeScript (vitest.config.ts)

```typescript
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    coverage: {
      provider: "v8",
      reporter: ["text", "lcov", "json-summary"],
      include: ["src/**/*.ts"],
      exclude: ["src/**/*.test.ts", "src/**/*.spec.ts"],
      thresholds: {
        branches: 80,
        functions: 80,
        lines: 80,
        statements: 80,
      },
    },
  },
});
```

### Python (pyproject.toml)

```toml
[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]

[tool.coverage.run]
source = ["src"]
omit = ["*/tests/*", "*/__main__.py"]

[tool.coverage.report]
fail_under = 80
show_missing = true
exclude_lines = ["pragma: no cover", "if TYPE_CHECKING:"]
```

---

## Inspector Smoke Tests

The MCP Inspector is your best friend for manual testing and CI smoke tests.

```bash
# Interactive — opens a web UI
npx @modelcontextprotocol/inspector npx tsx src/index.ts

# Python
npx @modelcontextprotocol/inspector python server.py

# Go / Rust / Java — any binary
npx @modelcontextprotocol/inspector ./my-server
```

**What to verify manually:**
- [ ] All tools appear in the tool list
- [ ] Tool descriptions are clear and accurate
- [ ] Tools handle valid input correctly
- [ ] Tools handle invalid input with helpful errors
- [ ] Resources are readable and return expected content
- [ ] Prompts produce useful messages
- [ ] Raw JSON-RPC messages look correct in the Messages tab

---

## Testing Checklist

| What to Test | Layer | Priority |
|--------------|-------|----------|
| Input validation | Unit | High |
| Happy path for each tool | Protocol | High |
| Error handling | Unit + Protocol | High |
| Missing/wrong arguments | Protocol | High |
| Security edge cases | Property-based | High |
| Resource content | Protocol | Medium |
| Concurrent requests | Protocol | Medium |
| Timeouts | Integration | Medium |
| Rate limiting | Integration | Medium |
| Edge cases (empty, huge, unicode) | Property-based | Medium |
| Snapshot stability | Protocol | Medium |
| Prompt output format | Protocol | Low |

## What's Next

- **[Guide 06: Security](06-security.md)** — Secure your server before deploying
- **[Guide 07: Production](07-production.md)** — Deploy with confidence
- **[Recipes](../recipes/)** — Copy-paste solutions for common patterns
