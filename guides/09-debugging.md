# Debugging MCP Servers

> **Time:** 15 minutes | **Level:** Intermediate | **Prerequisites:** [Guide 01](01-getting-started.md)

MCP servers run as child processes, communicating over stdio. This makes them harder to debug than typical web servers. This guide covers practical techniques.

## The Debugging Challenge

```
AI Client ──stdio──► MCP Server
    │                    │
    │ You can't:         │ You can't:
    ├─ See the protocol  ├─ Use console.log (breaks protocol)
    ├─ Set breakpoints   ├─ Open a debugger port easily
    └─ Inspect state     └─ See what the AI sends you
```

## Tool #1: MCP Inspector

Your most important debugging tool. The Inspector is a visual client that lets you interact with your server directly.

```bash
# TypeScript
npx @modelcontextprotocol/inspector npx tsx src/index.ts

# Python
npx @modelcontextprotocol/inspector python server.py

# Go
npx @modelcontextprotocol/inspector go run main.go

# Pre-built binary
npx @modelcontextprotocol/inspector ./my-server
```

**What you can do in Inspector:**
- See all registered tools, resources, and prompts
- Call tools with custom input
- Read resources
- See raw JSON-RPC messages
- Verify error responses

## Tool #2: Stderr Logging

Stdout is reserved for MCP protocol messages. **All logging must go to stderr.**

### TypeScript

```typescript
// BAD — breaks the MCP protocol
console.log("Debug info");

// GOOD — goes to stderr, visible in terminal
console.error("[debug] Tool called:", toolName);

// BETTER — structured logging (see recipes/structured-logging.md)
process.stderr.write(JSON.stringify({ level: "debug", msg: "Tool called", tool: toolName }) + "\n");
```

### Python

```python
import sys

# BAD
print("Debug info")

# GOOD
print("Debug info", file=sys.stderr)

# BETTER
import logging
logging.basicConfig(stream=sys.stderr, level=logging.DEBUG)
logger = logging.getLogger("mcp")
logger.debug("Tool called: %s", tool_name)
```

### Capturing Logs

When your server is launched by Claude Desktop or another client:

```bash
# Redirect stderr to a file in your MCP client config
{
  "mcpServers": {
    "my-server": {
      "command": "bash",
      "args": ["-c", "npx tsx server.ts 2>/tmp/mcp-server.log"]
    }
  }
}
```

Then tail the log:

```bash
tail -f /tmp/mcp-server.log
```

## Tool #3: Protocol Tracing

See the exact JSON-RPC messages flowing between client and server.

### Using the Inspector

The Inspector shows raw messages in its "Messages" tab. This is the easiest way to see:
- What the client sends to your server
- What your server responds with
- Error responses and their format

### Manual Tracing (TypeScript)

Wrap the transport to log all messages:

```typescript
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

const transport = new StdioServerTransport();

// Intercept incoming messages
const originalOnMessage = transport.onmessage;
transport.onmessage = (message) => {
  console.error("[recv]", JSON.stringify(message).slice(0, 200));
  originalOnMessage?.(message);
};

await server.connect(transport);
```

## Common Issues and Fixes

### Server Not Starting

**Symptom:** Client says "failed to connect" or "server not found"

```bash
# 1. Test the server runs at all
npx tsx server.ts
# If it crashes, you'll see the error in the terminal

# 2. Check the command works from the path the client uses
# Clients often have different PATH than your terminal
which npx    # Note the full path
which node
which python
```

**Fix:** Use absolute paths in client config:

```json
{
  "command": "/usr/local/bin/npx",
  "args": ["tsx", "/Users/me/projects/server/src/index.ts"]
}
```

### Server Starts But No Tools Appear

**Symptom:** Connected but tool list is empty

```bash
# Check with Inspector
npx @modelcontextprotocol/inspector npx tsx server.ts
# Look at the "Tools" tab — are tools registered?
```

**Common causes:**
- Tools registered after `server.connect()` (register before connecting)
- Capability not declared (TypeScript SDK usually handles this)
- Tool registration threw an error (check stderr)

### Tool Calls Return Errors

**Symptom:** Tool appears but returns unexpected errors

```bash
# Call the tool directly in Inspector with test input
# Compare the error message with your handler code

# Add temporary debug logging
server.tool("my_tool", "...", { input: z.string() }, async ({ input }) => {
  console.error("[debug] my_tool called with:", JSON.stringify(input));
  // ... your logic
});
```

### "Unexpected end of JSON input"

**Symptom:** Server crashes with JSON parse errors

**Cause:** Something is writing to stdout (which corrupts the protocol stream).

**Fix:** Search for any `console.log`, `print()`, or stdout writes and change them to stderr.

```bash
# Find stdout writes in your code
grep -rn "console\.log" src/
grep -rn "^print(" server.py  # Python: print without file=sys.stderr
```

### Server Works in Inspector But Not in Claude Desktop

**Symptom:** Inspector works fine, but Claude Desktop can't connect

**Common causes:**
1. **Different PATH** — Claude Desktop may not have your shell PATH
2. **Relative paths** — Use absolute paths in config
3. **Missing env vars** — Environment variables from your shell aren't inherited
4. **Config location** — Make sure you're editing the right config file

```bash
# macOS Claude Desktop config
cat ~/Library/Application\ Support/Claude/claude_desktop_config.json

# Verify it's valid JSON
python -m json.tool < ~/Library/Application\ Support/Claude/claude_desktop_config.json
```

## Debugging Checklist

When something doesn't work:

1. **Can the server start at all?** Run it directly in the terminal
2. **Does it work in Inspector?** Test tools and resources
3. **Is anything writing to stdout?** Search for `console.log` / `print()`
4. **Are paths absolute?** Relative paths break when the client's CWD differs
5. **Are env vars set?** Add them to the client config's `env` section
6. **Check stderr output** — Redirect to a file and `tail -f` it

## What's Next

- **[Recipes](../recipes/)** — Copy-paste solutions for common patterns
- **[Guide 05: Testing](05-testing.md)** — Catch bugs before they hit production
