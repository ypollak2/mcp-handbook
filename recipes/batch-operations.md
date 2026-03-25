# Recipe: Batch Operations

Process multiple items in a single tool call, handling partial failures gracefully.

## TypeScript

```typescript
server.tool(
  "send_notifications",
  "Send notifications to multiple users. Returns results for each user, even if some fail.",
  {
    userIds: z.array(z.string()).min(1).max(50).describe("User IDs to notify"),
    message: z.string().describe("Notification message"),
  },
  async ({ userIds, message }) => {
    const results = await Promise.allSettled(
      userIds.map(async (userId) => {
        await sendNotification(userId, message);
        return userId;
      })
    );

    const succeeded: string[] = [];
    const failed: Array<{ userId: string; error: string }> = [];

    results.forEach((result, i) => {
      if (result.status === "fulfilled") {
        succeeded.push(result.value);
      } else {
        failed.push({ userId: userIds[i], error: result.reason?.message || "Unknown error" });
      }
    });

    let text = `Sent: ${succeeded.length}/${userIds.length}`;

    if (failed.length > 0) {
      text += `\n\nFailed (${failed.length}):\n`;
      text += failed.map((f) => `  - ${f.userId}: ${f.error}`).join("\n");
    }

    return {
      content: [{ type: "text", text }],
      isError: failed.length === userIds.length, // Only error if ALL failed
    };
  }
);
```

## Python

```python
@mcp.tool()
async def send_notifications(user_ids: list[str], message: str) -> str:
    """Send notifications to multiple users. Returns results for each, even if some fail.

    Args:
        user_ids: User IDs to notify (max 50)
        message: Notification message
    """
    if len(user_ids) > 50:
        return "Maximum 50 users per batch. Split into smaller groups."

    succeeded = []
    failed = []

    for user_id in user_ids:
        try:
            await send_notification(user_id, message)
            succeeded.append(user_id)
        except Exception as e:
            failed.append({"user_id": user_id, "error": str(e)})

    text = f"Sent: {len(succeeded)}/{len(user_ids)}"

    if failed:
        text += f"\n\nFailed ({len(failed)}):\n"
        text += "\n".join(f"  - {f['user_id']}: {f['error']}" for f in failed)

    return text
```

## Key Principles

1. **Use `Promise.allSettled`** (not `Promise.all`) — one failure shouldn't abort the batch
2. **Report partial results** — "Sent 8/10" is more useful than "Error: user 9 not found"
3. **Set batch limits** — Prevent the AI from sending 10,000 items in one call
4. **Only set `isError` if everything failed** — Partial success is still a success
