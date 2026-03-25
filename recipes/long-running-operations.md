# Recipe: Long-Running Operations

Report progress on operations that take more than a few seconds. MCP supports progress notifications via `_meta.progressToken`.

## TypeScript

```typescript
server.tool(
  "process_files",
  "Process all files in a directory. Sends progress updates for long operations.",
  {
    directory: z.string().describe("Directory to process"),
  },
  async ({ directory }, { meta }) => {
    const files = await listFiles(directory);
    const results: string[] = [];
    const progressToken = meta?.progressToken;

    for (let i = 0; i < files.length; i++) {
      // Send progress notification if the client supports it
      if (progressToken !== undefined) {
        await server.server.notification({
          method: "notifications/progress",
          params: {
            progressToken,
            progress: i,
            total: files.length,
            message: `Processing ${files[i]}...`,
          },
        });
      }

      const result = await processFile(files[i]);
      results.push(`${files[i]}: ${result}`);
    }

    return {
      content: [
        {
          type: "text",
          text: `Processed ${files.length} files:\n\n${results.join("\n")}`,
        },
      ],
    };
  }
);
```

## Python

```python
from mcp.server.fastmcp import Context

@mcp.tool()
async def process_files(directory: str, ctx: Context) -> str:
    """Process all files in a directory. Sends progress updates for long operations."""
    files = list_files(directory)
    results = []

    for i, file in enumerate(files):
        await ctx.report_progress(i, len(files), f"Processing {file}...")
        result = await process_file(file)
        results.append(f"{file}: {result}")

    return f"Processed {len(files)} files:\n\n" + "\n".join(results)
```

## When to Use Progress

| Duration | Strategy |
|----------|----------|
| < 1s | No progress needed |
| 1–5s | Optional progress |
| 5–30s | Progress strongly recommended |
| > 30s | Consider breaking into smaller steps |

## Fallback for Clients Without Progress Support

Not all clients support progress notifications. Always return a complete result even if progress wasn't sent — the progress is a UX enhancement, not a requirement.
