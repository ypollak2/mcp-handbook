# Recipe: Caching Layer

In-memory TTL cache to avoid re-fetching expensive data on repeated tool calls.

## TypeScript

```typescript
class Cache<T> {
  private store = new Map<string, { value: T; expiresAt: number }>();

  constructor(private defaultTtlMs: number = 5 * 60 * 1000) {}

  get(key: string): T | undefined {
    const entry = this.store.get(key);
    if (!entry) return undefined;

    if (Date.now() > entry.expiresAt) {
      this.store.delete(key);
      return undefined;
    }

    return entry.value;
  }

  set(key: string, value: T, ttlMs?: number): void {
    this.store.set(key, {
      value,
      expiresAt: Date.now() + (ttlMs ?? this.defaultTtlMs),
    });
  }

  invalidate(key: string): void {
    this.store.delete(key);
  }

  clear(): void {
    this.store.clear();
  }

  get size(): number {
    return this.store.size;
  }

  /** Wraps an async function with caching */
  async wrap<A extends unknown[]>(
    key: string,
    fn: (...args: A) => Promise<T>,
    ...args: A
  ): Promise<T> {
    const cached = this.get(key);
    if (cached !== undefined) return cached;

    const result = await fn(...args);
    this.set(key, result);
    return result;
  }
}

// Usage in MCP server
const cache = new Cache<string>(5 * 60 * 1000); // 5-minute TTL

server.tool("get_user", "Get user profile", { id: z.string() }, async ({ id }) => {
  const data = await cache.wrap(`user:${id}`, fetchUser, id);
  return { content: [{ type: "text", text: data }] };
});

// Invalidate when data changes
server.tool("update_user", "Update user", { id: z.string(), name: z.string() }, async ({ id, name }) => {
  await updateUser(id, name);
  cache.invalidate(`user:${id}`);
  return { content: [{ type: "text", text: `User ${id} updated.` }] };
});
```

## Python

```python
import time
from typing import TypeVar, Callable, Any

T = TypeVar("T")


class Cache:
    def __init__(self, default_ttl: float = 300):  # 5 minutes
        self._store: dict[str, tuple[Any, float]] = {}
        self._default_ttl = default_ttl

    def get(self, key: str) -> Any | None:
        entry = self._store.get(key)
        if entry is None:
            return None
        value, expires_at = entry
        if time.time() > expires_at:
            del self._store[key]
            return None
        return value

    def set(self, key: str, value: Any, ttl: float | None = None) -> None:
        self._store[key] = (value, time.time() + (ttl or self._default_ttl))

    def invalidate(self, key: str) -> None:
        self._store.pop(key, None)

    def wrap(self, key: str, fn: Callable[..., T], *args: Any) -> T:
        cached = self.get(key)
        if cached is not None:
            return cached
        result = fn(*args)
        self.set(key, result)
        return result


# Usage
cache = Cache(ttl=300)

@mcp.tool()
def get_user(id: str) -> str:
    """Get user profile."""
    return cache.wrap(f"user:{id}", fetch_user, id)
```

## TTL Guidelines

| Data Type | Suggested TTL | Reason |
|-----------|--------------|--------|
| Database schema | 1 hour | Changes rarely |
| API metadata | 15 minutes | Changes occasionally |
| User data | 5 minutes | Changes moderately |
| Search results | 1 minute | Freshness matters |
| Real-time data | Don't cache | Stale data is wrong data |
