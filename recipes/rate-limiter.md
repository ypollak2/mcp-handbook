# Recipe: Rate Limiter

Throttle outgoing requests to protect external services from being overwhelmed by AI tool calls.

## TypeScript

```typescript
class RateLimiter {
  private timestamps: number[] = [];

  constructor(
    private maxRequests: number,
    private windowMs: number,
  ) {}

  /** Throws if rate limit exceeded. Call before each request. */
  check(): void {
    const now = Date.now();
    this.timestamps = this.timestamps.filter((t) => now - t < this.windowMs);

    if (this.timestamps.length >= this.maxRequests) {
      const oldestInWindow = this.timestamps[0];
      const waitMs = this.windowMs - (now - oldestInWindow);
      throw new Error(
        `Rate limited: ${this.maxRequests} requests per ${this.windowMs / 1000}s. ` +
        `Try again in ${Math.ceil(waitMs / 1000)} seconds.`
      );
    }

    this.timestamps.push(now);
  }

  /** Wait until a slot is available, then proceed. */
  async waitForSlot(): Promise<void> {
    while (true) {
      try {
        this.check();
        return;
      } catch {
        await new Promise((r) => setTimeout(r, 1000));
      }
    }
  }
}

// Usage
const apiLimiter = new RateLimiter(30, 60_000); // 30 req/min

server.tool("search", "Search external API", { query: z.string() }, async ({ query }) => {
  apiLimiter.check(); // Throws with helpful message if limited
  const results = await externalApi.search(query);
  return { content: [{ type: "text", text: formatResults(results) }] };
});
```

## Python

```python
import time


class RateLimiter:
    def __init__(self, max_requests: int, window_seconds: float):
        self.max_requests = max_requests
        self.window = window_seconds
        self._timestamps: list[float] = []

    def check(self) -> None:
        now = time.time()
        self._timestamps = [t for t in self._timestamps if now - t < self.window]

        if len(self._timestamps) >= self.max_requests:
            wait = self.window - (now - self._timestamps[0])
            raise RuntimeError(
                f"Rate limited: {self.max_requests} requests per {self.window}s. "
                f"Try again in {int(wait) + 1} seconds."
            )

        self._timestamps.append(now)


# Usage
limiter = RateLimiter(max_requests=30, window_seconds=60)

@mcp.tool()
def search(query: str) -> str:
    """Search external API."""
    limiter.check()
    return external_api.search(query)
```

## Per-Endpoint Limiting

Different endpoints may have different limits:

```typescript
const limiters = {
  search: new RateLimiter(30, 60_000),    // 30/min
  create: new RateLimiter(10, 60_000),    // 10/min
  delete: new RateLimiter(5, 60_000),     // 5/min
};
```

## Why Rate Limiting Matters for MCP

The AI doesn't inherently understand API rate limits. Without a limiter, a prompt like "search for 50 different topics" will fire 50 requests as fast as possible. A rate limiter:
- Prevents hitting external API limits (which can cause temporary bans)
- Returns a clear message the AI can understand ("try again in X seconds")
- Protects your API keys from abuse
