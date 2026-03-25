# Recipe: Graceful Shutdown

Clean up resources (database connections, file handles, temp files) when the server stops.

## TypeScript

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import Database from "better-sqlite3";

const db = new Database("data.db");
const server = new McpServer({ name: "my-server", version: "1.0.0" });

// Track resources that need cleanup
const cleanup = async () => {
  console.error("[shutdown] Closing database...");
  db.close();

  console.error("[shutdown] Closing MCP server...");
  await server.close();

  console.error("[shutdown] Done.");
  process.exit(0);
};

// Handle all shutdown signals
process.on("SIGINT", cleanup);   // Ctrl+C
process.on("SIGTERM", cleanup);  // Docker stop, process managers
process.on("SIGHUP", cleanup);   // Terminal closed

// Handle unexpected errors
process.on("uncaughtException", (error) => {
  console.error("[fatal] Uncaught exception:", error.message);
  cleanup();
});

process.on("unhandledRejection", (reason) => {
  console.error("[fatal] Unhandled rejection:", reason);
  cleanup();
});
```

## Python

```python
import signal
import sys
import atexit


def create_cleanup(db):
    """Create a cleanup function that closes the given resources."""

    def cleanup(*args):
        print("[shutdown] Closing database...", file=sys.stderr)
        db.close()
        print("[shutdown] Done.", file=sys.stderr)
        sys.exit(0)

    return cleanup


# Register cleanup handlers
db = open_database()
cleanup = create_cleanup(db)

signal.signal(signal.SIGINT, cleanup)
signal.signal(signal.SIGTERM, cleanup)
atexit.register(db.close)  # Fallback for normal exit
```

## Why This Matters

MCP servers run as long-lived processes. Without cleanup:
- Database connections leak (eventually hitting connection limits)
- Temp files accumulate
- Write-ahead logs don't flush (data loss in SQLite)
- File locks aren't released (blocking other processes)
