# Error Handling

> **Time:** 15 minutes | **Level:** Intermediate | **Prerequisites:** [Guide 02](02-core-concepts.md)

Good error handling is the difference between an MCP server that's useful in demos and one that's reliable in daily use. This guide covers how to handle errors so the AI can recover gracefully.

## The Golden Rule

> **Return helpful error messages, not stack traces.** The AI needs to understand what went wrong and what to try next.

```
BAD:  "Error: SQLITE_ERROR: no such table: usres"
GOOD: "Table 'usres' not found. Available tables: users, orders, products. Did you mean 'users'?"
```

## Error Response Pattern

MCP tools can signal errors via the `isError` flag. This tells the AI the operation failed:

### TypeScript

```typescript
server.tool("my_tool", "Does something", { input: z.string() }, async ({ input }) => {
  try {
    const result = await doSomething(input);
    return {
      content: [{ type: "text", text: result }],
    };
  } catch (error) {
    return {
      content: [
        {
          type: "text",
          text: `Failed to process "${input}": ${error instanceof Error ? error.message : "Unknown error"}. Try checking the input format.`,
        },
      ],
      isError: true,
    };
  }
});
```

### Python

```python
@mcp.tool()
def my_tool(input: str) -> str:
    """Does something."""
    try:
        return do_something(input)
    except ValueError as e:
        raise McpError(f'Invalid input "{input}": {e}. Expected format: ...')
    except ConnectionError:
        raise McpError("Service unavailable. Try again in a few seconds.")
```

## Error Categories

Different errors need different handling:

```
Error Type          → Strategy              → Example
───────────────────────────────────────────────────────────
Input validation    → Reject immediately     → "Name cannot be empty"
                      with fix suggestion      "Expected a number, got 'abc'"

Not found           → Suggest alternatives   → "User 'jon' not found. Did you mean 'john'?"

Permission denied   → Explain clearly        → "Cannot write to /etc. Server is read-only."

External service    → Retry or degrade       → "GitHub API rate limited. Retry after 60s."
  failure             gracefully

Timeout             → Report with context    → "Query timed out after 10s. Try a simpler query."

Internal error      → Generic message +      → "Internal error processing request. Details
                      log details               logged for debugging."
```

## Timeouts

Always set timeouts on external calls. An MCP server that hangs is worse than one that fails fast.

### TypeScript

```typescript
async function fetchWithTimeout(url: string, timeoutMs = 10_000): Promise<Response> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, { signal: controller.signal });
    return response;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(`Request to ${url} timed out after ${timeoutMs}ms`);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
```

### Python

```python
import urllib.request

def fetch_with_timeout(url: str, timeout_seconds: int = 10) -> str:
    try:
        with urllib.request.urlopen(url, timeout=timeout_seconds) as response:
            return response.read().decode("utf-8")
    except urllib.error.URLError as e:
        if "timed out" in str(e.reason):
            raise TimeoutError(f"Request to {url} timed out after {timeout_seconds}s")
        raise
```

## Partial Results

When an operation partially succeeds, return what you have with a clear note:

```typescript
server.tool("fetch_all_repos", "Fetch info about multiple repos", {
  repos: z.array(z.string()),
}, async ({ repos }) => {
  const results: string[] = [];
  const errors: string[] = [];

  for (const repo of repos) {
    try {
      const info = await fetchRepo(repo);
      results.push(`${repo}: ${info.stars} stars`);
    } catch {
      errors.push(repo);
    }
  }

  let text = results.join("\n");
  if (errors.length > 0) {
    text += `\n\nFailed to fetch: ${errors.join(", ")}`;
  }

  return { content: [{ type: "text", text }] };
});
```

## Error Handling Checklist

- [ ] Every tool has a try/catch (or try/except) wrapping its core logic
- [ ] Error messages are human-readable, not stack traces
- [ ] Error messages suggest what to do next when possible
- [ ] External calls have timeouts
- [ ] `isError: true` is set on error responses (TypeScript)
- [ ] Partial results are returned when possible
- [ ] Sensitive details (API keys, internal paths) are never in error messages

## What's Next

- **[Guide 05: Testing](05-testing.md)** — How to test your error handling (and everything else)
- **[Guide 06: Security](06-security.md)** — Security-specific error considerations
