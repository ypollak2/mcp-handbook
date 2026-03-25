# Recipe: Structured Logging

Log server activity to stderr with structured JSON. Never log to stdout — MCP uses it for protocol messages.

## TypeScript

```typescript
type LogLevel = "debug" | "info" | "warn" | "error";

const LOG_LEVEL = (process.env.LOG_LEVEL || "info") as LogLevel;
const LEVELS: Record<LogLevel, number> = { debug: 0, info: 1, warn: 2, error: 3 };

function log(level: LogLevel, message: string, data?: Record<string, unknown>) {
  if (LEVELS[level] < LEVELS[LOG_LEVEL]) return;

  const entry = JSON.stringify({
    ts: new Date().toISOString(),
    level,
    msg: message,
    ...data,
  });

  process.stderr.write(entry + "\n");
}

// Convenience methods
const logger = {
  debug: (msg: string, data?: Record<string, unknown>) => log("debug", msg, data),
  info: (msg: string, data?: Record<string, unknown>) => log("info", msg, data),
  warn: (msg: string, data?: Record<string, unknown>) => log("warn", msg, data),
  error: (msg: string, data?: Record<string, unknown>) => log("error", msg, data),
};

// Usage
logger.info("Tool called", { tool: "query", input_length: sql.length });
logger.error("Query failed", { tool: "query", error: err.message, sql });
```

## Python

```python
import json
import logging
import sys


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "ts": self.formatTime(record),
            "level": record.levelname.lower(),
            "msg": record.getMessage(),
        }
        if hasattr(record, "data"):
            entry.update(record.data)  # type: ignore
        if record.exc_info and record.exc_info[1]:
            entry["error"] = str(record.exc_info[1])
        return json.dumps(entry)


# Configure once at module level
handler = logging.StreamHandler(sys.stderr)
handler.setFormatter(JsonFormatter())

logger = logging.getLogger("mcp")
logger.addHandler(handler)
logger.setLevel(logging.INFO)

# Usage
logger.info("Tool called", extra={"data": {"tool": "query"}})
logger.error("Query failed", extra={"data": {"tool": "query", "error": str(e)}})
```

## What to Log

| Event | Level | Data to Include |
|-------|-------|-----------------|
| Server started | info | version, config (no secrets) |
| Tool called | info | tool name, input summary |
| Tool succeeded | debug | tool name, duration_ms |
| Tool failed | error | tool name, error message, input |
| Resource accessed | debug | resource URI |
| External API call | info | url, method, status, duration_ms |
| Rate limited | warn | endpoint, retry_after_s |
| Shutdown | info | reason, uptime_s |

## Viewing Logs

```bash
# Pipe stderr to a file while running
npx tsx server.ts 2>server.log

# Pretty-print JSON logs
tail -f server.log | python -m json.tool

# Filter errors only
tail -f server.log | grep '"level":"error"'
```
