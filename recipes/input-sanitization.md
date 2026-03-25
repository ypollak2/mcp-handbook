# Recipe: Input Sanitization

Validate and clean all tool inputs at the boundary. Never trust data from the AI — it may be manipulated by prompt injection.

## TypeScript — Validation Helpers

```typescript
/** Validate a table/column name (prevent SQL injection in identifiers) */
function validateIdentifier(name: string, label: string): string {
  if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name)) {
    throw new Error(`Invalid ${label}: "${name}". Only letters, numbers, and underscores allowed.`);
  }
  if (name.length > 64) {
    throw new Error(`${label} too long (max 64 characters).`);
  }
  return name;
}

/** Validate and resolve a file path within a sandbox */
function validatePath(inputPath: string, allowedRoot: string): string {
  const resolved = resolve(allowedRoot, inputPath);
  if (!resolved.startsWith(resolve(allowedRoot))) {
    throw new Error(`Path "${inputPath}" is outside the allowed directory.`);
  }
  return resolved;
}

/** Validate a URL (prevent SSRF) */
function validateUrl(url: string): URL {
  const parsed = new URL(url);

  if (!["http:", "https:"].includes(parsed.protocol)) {
    throw new Error("Only HTTP/HTTPS URLs allowed.");
  }

  const forbidden = ["localhost", "127.0.0.1", "0.0.0.0", "[::1]"];
  if (
    forbidden.includes(parsed.hostname) ||
    parsed.hostname.startsWith("10.") ||
    parsed.hostname.startsWith("192.168.") ||
    parsed.hostname.startsWith("172.16.") ||
    parsed.hostname.endsWith(".local") ||
    parsed.hostname.endsWith(".internal")
  ) {
    throw new Error("Internal/private network URLs are not allowed.");
  }

  return parsed;
}

/** Truncate a string to prevent oversized inputs */
function truncate(input: string, maxLength: number, label: string): string {
  if (input.length > maxLength) {
    throw new Error(`${label} too long: ${input.length} chars (max ${maxLength}).`);
  }
  return input;
}
```

## Python — Validation Helpers

```python
import re
from pathlib import Path
from urllib.parse import urlparse


def validate_identifier(name: str, label: str) -> str:
    """Validate a SQL identifier (table/column name)."""
    if not re.match(r"^[a-zA-Z_]\w*$", name):
        raise ValueError(f'Invalid {label}: "{name}". Only letters, numbers, and underscores.')
    if len(name) > 64:
        raise ValueError(f"{label} too long (max 64 characters).")
    return name


def validate_path(input_path: str, allowed_root: str) -> Path:
    """Validate a file path is within the allowed root."""
    resolved = Path(allowed_root, input_path).resolve()
    if not str(resolved).startswith(str(Path(allowed_root).resolve())):
        raise ValueError(f'Path "{input_path}" is outside the allowed directory.')
    return resolved


def validate_url(url: str) -> str:
    """Validate a URL to prevent SSRF."""
    parsed = urlparse(url)

    if parsed.scheme not in ("http", "https"):
        raise ValueError("Only HTTP/HTTPS URLs allowed.")

    hostname = parsed.hostname or ""
    forbidden = {"localhost", "127.0.0.1", "0.0.0.0", "[::1]"}
    if (
        hostname in forbidden
        or hostname.startswith(("10.", "192.168.", "172.16."))
        or hostname.endswith((".local", ".internal"))
    ):
        raise ValueError("Internal/private network URLs are not allowed.")

    return url
```

## Usage Pattern

Apply validators at the top of every tool handler:

```typescript
server.tool("query_table", "Query a database table", {
  table: z.string(),
  filter: z.string().optional(),
  limit: z.number().max(100).optional().default(20),
}, async ({ table, filter, limit }) => {
  // Validate FIRST, before any logic
  const safeTable = validateIdentifier(table, "table name");
  const safeFilter = filter ? truncate(filter, 500, "filter") : undefined;

  // Now use the validated values
  // ...
});
```
