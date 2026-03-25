# Recipe: Confirmation Pattern

Use a two-step approach for dangerous operations. First preview what will happen, then execute with an explicit confirmation flag.

## TypeScript

```typescript
server.tool(
  "delete_records",
  "Delete records matching a filter. Call with dryRun: true first to preview, then dryRun: false to execute.",
  {
    filter: z.string().describe("SQL WHERE clause for records to delete"),
    table: z.string().describe("Table to delete from"),
    dryRun: z
      .boolean()
      .optional()
      .default(true)
      .describe("If true, preview what would be deleted without executing"),
  },
  async ({ filter, table, dryRun }) => {
    // Always validate the table name
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(table)) {
      return { content: [{ type: "text", text: "Invalid table name." }], isError: true };
    }

    // Count affected records
    const { count } = db
      .prepare(`SELECT COUNT(*) as count FROM ${table} WHERE ${filter}`)
      .get() as { count: number };

    if (count === 0) {
      return { content: [{ type: "text", text: "No records match the filter." }] };
    }

    if (dryRun) {
      // Preview mode: show what would be deleted
      const sample = db
        .prepare(`SELECT * FROM ${table} WHERE ${filter} LIMIT 5`)
        .all();

      return {
        content: [
          {
            type: "text",
            text: [
              `⚠️ DRY RUN — ${count} records would be deleted from "${table}"`,
              "",
              `Sample (first 5):`,
              formatTable(sample),
              "",
              `To execute: call again with dryRun: false`,
            ].join("\n"),
          },
        ],
      };
    }

    // Execute the deletion
    const result = db.prepare(`DELETE FROM ${table} WHERE ${filter}`).run();

    return {
      content: [
        { type: "text", text: `Deleted ${result.changes} records from "${table}".` },
      ],
    };
  }
);
```

## Python

```python
@mcp.tool()
def delete_records(table: str, filter: str, dry_run: bool = True) -> str:
    """Delete records matching a filter.

    Call with dry_run=True first to preview, then dry_run=False to execute.

    Args:
        table: Table to delete from
        filter: SQL WHERE clause for records to delete
        dry_run: If True, preview only. If False, execute the deletion.
    """
    if not re.match(r"^[a-zA-Z_]\w*$", table):
        raise ValueError("Invalid table name")

    count = db.execute(f"SELECT COUNT(*) FROM {table} WHERE {filter}").fetchone()[0]

    if count == 0:
        return "No records match the filter."

    if dry_run:
        sample = db.execute(f"SELECT * FROM {table} WHERE {filter} LIMIT 5").fetchall()
        return (
            f"DRY RUN — {count} records would be deleted from '{table}'\n\n"
            f"Sample (first 5):\n{format_table(sample)}\n\n"
            f"To execute: call again with dry_run=False"
        )

    result = db.execute(f"DELETE FROM {table} WHERE {filter}")
    return f"Deleted {result.rowcount} records from '{table}'."
```

## Why This Pattern

- The AI naturally calls with `dryRun: true` first (it's the default)
- The preview shows exactly what will happen
- The user sees the preview in the conversation and can approve or reject
- Even if the AI calls with `dryRun: false` immediately, it's a conscious choice visible in the tool call
