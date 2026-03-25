# Performance & Optimization

> **Time:** 15 minutes | **Level:** Advanced | **Prerequisites:** [Guide 03](03-architecture-patterns.md)

MCP server performance directly affects the AI experience. Slow responses break the conversation flow. This guide covers practical optimizations.

## Response Time Targets

| Operation | Target | User Impact |
|-----------|--------|-------------|
| Simple tool call | < 200ms | Feels instant |
| Database query | < 1s | Acceptable |
| External API call | < 3s | Noticeable delay |
| Complex operation | < 10s | Should show progress |
| Anything > 10s | Avoid | Feels broken |

## Caching

Cache expensive operations to avoid re-fetching:

### In-Memory Cache

```typescript
class SimpleCache<T> {
  private cache = new Map<string, { value: T; expires: number }>();

  constructor(private ttlMs: number) {}

  get(key: string): T | undefined {
    const entry = this.cache.get(key);
    if (!entry || Date.now() > entry.expires) {
      this.cache.delete(key);
      return undefined;
    }
    return entry.value;
  }

  set(key: string, value: T): void {
    this.cache.set(key, { value, expires: Date.now() + this.ttlMs });
  }
}

// Cache API responses for 5 minutes
const apiCache = new SimpleCache<string>(5 * 60 * 1000);

server.tool("get_data", "Fetch data", { key: z.string() }, async ({ key }) => {
  const cached = apiCache.get(key);
  if (cached) return { content: [{ type: "text", text: cached }] };

  const data = await fetchData(key);
  apiCache.set(key, data);
  return { content: [{ type: "text", text: data }] };
});
```

### When to Cache

```
CACHE:                           DON'T CACHE:
├── Database schemas             ├── User-specific data
├── API metadata                 ├── Real-time data
├── File directory listings      ├── Write operation results
├── Static reference data        ├── Security-sensitive data
└── Expensive computations       └── Frequently changing data
```

## Pagination

Never return unbounded result sets:

```typescript
server.tool(
  "list_items",
  "List items with pagination",
  {
    page: z.number().optional().default(1),
    pageSize: z.number().optional().default(20).describe("Items per page (max 100)"),
  },
  async ({ page, pageSize }) => {
    const size = Math.min(pageSize, 100);
    const offset = (page - 1) * size;

    const items = db.prepare("SELECT * FROM items LIMIT ? OFFSET ?").all(size, offset);
    const total = db.prepare("SELECT COUNT(*) as count FROM items").get() as { count: number };

    const totalPages = Math.ceil(total.count / size);

    return {
      content: [
        {
          type: "text",
          text: [
            formatTable(items),
            "",
            `Page ${page} of ${totalPages} (${total.count} total items)`,
            page < totalPages ? `Use page: ${page + 1} to see more.` : "",
          ].join("\n"),
        },
      ],
    };
  }
);
```

## Connection Pooling

For database servers, reuse connections:

```typescript
// BAD — new connection per request
server.tool("query", "Run query", { sql: z.string() }, async ({ sql }) => {
  const db = new Database("data.db"); // Opens new connection every time
  const result = db.prepare(sql).all();
  db.close();
  return { content: [{ type: "text", text: formatResult(result) }] };
});

// GOOD — shared connection
const db = new Database("data.db", { readonly: true });

server.tool("query", "Run query", { sql: z.string() }, async ({ sql }) => {
  const result = db.prepare(sql).all();
  return { content: [{ type: "text", text: formatResult(result) }] };
});
```

## Response Formatting

The AI processes structured text faster than raw data:

```typescript
// BAD — raw JSON dump (the AI has to parse and interpret it)
return JSON.stringify(rows);

// GOOD — formatted, summarized output
return `Found ${rows.length} users in the "admin" role:\n\n${formatTable(rows)}`;
```

**Why this matters for performance:** A formatted response reduces follow-up tool calls. If the AI can understand the result in one pass, it doesn't need to call another tool to clarify.

## Parallel Requests

When a tool needs data from multiple sources, fetch in parallel:

```typescript
// BAD — sequential (3x slower)
const users = await fetchUsers();
const orders = await fetchOrders();
const products = await fetchProducts();

// GOOD — parallel
const [users, orders, products] = await Promise.all([
  fetchUsers(),
  fetchOrders(),
  fetchProducts(),
]);
```

## Lazy Loading

Don't load everything at startup. Load resources on demand:

```typescript
// BAD — loads all data at server start
const allData = await loadEntireDatabase();

// GOOD — loads on first request, then caches
let schemaCache: string | null = null;

server.resource("schema", "db://schema", async (uri) => {
  if (!schemaCache) {
    schemaCache = await loadSchema();
  }
  return { contents: [{ uri: uri.href, mimeType: "text/plain", text: schemaCache }] };
});
```

## Performance Checklist

- [ ] External API calls have timeouts (< 10s)
- [ ] Expensive data is cached with appropriate TTL
- [ ] Query results are paginated or bounded
- [ ] Database connections are reused (pooled)
- [ ] Responses are formatted and summarized (reduce follow-up calls)
- [ ] Independent operations run in parallel
- [ ] Heavy data is loaded lazily, not at startup

---

**Congratulations!** You've completed the MCP Handbook guides. You now know how to build, test, secure, deploy, and optimize MCP servers.

Go build something great. And when you do, consider [contributing](../CONTRIBUTING.md) what you've learned back to this handbook.
