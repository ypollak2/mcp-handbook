# Recipe: Database Connection

Manage database connections with proper lifecycle handling for MCP servers.

## SQLite (TypeScript)

```typescript
import Database from "better-sqlite3";

// Open once at module level — SQLite is single-file, no pooling needed
const db = new Database(process.env.DB_PATH || "data.db", {
  readonly: true,        // Read-only by default (safety)
  fileMustExist: true,   // Fail fast if database doesn't exist
});

// Enable WAL mode for better concurrent read performance
db.pragma("journal_mode = WAL");

// Set a busy timeout (wait up to 5s if db is locked)
db.pragma("busy_timeout = 5000");
```

## PostgreSQL (TypeScript)

```typescript
import pg from "pg";

const pool = new pg.Pool({
  connectionString: process.env.DATABASE_URL,
  max: 5,              // Max connections in pool
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
});

// Health check
pool.on("error", (err) => {
  console.error("[db] Unexpected pool error:", err.message);
});

// Use in tools
async function query<T>(sql: string, params: unknown[] = []): Promise<T[]> {
  const client = await pool.connect();
  try {
    const result = await client.query(sql, params);
    return result.rows as T[];
  } finally {
    client.release(); // Always release back to pool
  }
}

// Cleanup on shutdown
process.on("SIGTERM", async () => {
  await pool.end();
  process.exit(0);
});
```

## SQLite (Python)

```python
import sqlite3
import os


def open_database(readonly: bool = True) -> sqlite3.Connection:
    db_path = os.environ.get("DB_PATH", "data.db")

    if not os.path.exists(db_path):
        raise FileNotFoundError(f"Database not found: {db_path}")

    uri = f"file:{db_path}?mode=ro" if readonly else f"file:{db_path}"
    conn = sqlite3.connect(uri, uri=True)
    conn.row_factory = sqlite3.Row  # Dict-like access to columns
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA busy_timeout=5000")

    return conn


db = open_database()
```

## PostgreSQL (Python)

```python
import psycopg2
from psycopg2 import pool
import os

db_pool = psycopg2.pool.ThreadedConnectionPool(
    minconn=1,
    maxconn=5,
    dsn=os.environ["DATABASE_URL"],
)


def query(sql: str, params: tuple = ()) -> list[dict]:
    conn = db_pool.getconn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, params)
            return cur.fetchall()
    finally:
        db_pool.putconn(conn)
```

## Key Points

- **Open connections at module level**, not per-request
- **Use connection pools** for PostgreSQL/MySQL (not SQLite — it's single-file)
- **Always release connections** back to the pool (use `finally` blocks)
- **Set timeouts** — don't let queries hang forever
- **Close on shutdown** — register SIGTERM/SIGINT handlers
