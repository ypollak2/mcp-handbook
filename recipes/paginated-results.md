# Recipe: Paginated Results

Return large datasets in pages so the AI can request more as needed.

## TypeScript

```typescript
server.tool(
  "list_items",
  "List items with pagination. Use page parameter to navigate results.",
  {
    page: z.number().min(1).optional().default(1).describe("Page number (starts at 1)"),
    pageSize: z.number().min(1).max(100).optional().default(20).describe("Items per page"),
    filter: z.string().optional().describe("Optional filter term"),
  },
  async ({ page, pageSize, filter }) => {
    const offset = (page - 1) * pageSize;

    let query = "SELECT * FROM items";
    const params: unknown[] = [];

    if (filter) {
      query += " WHERE name LIKE ?";
      params.push(`%${filter}%`);
    }

    const countQuery = query.replace("SELECT *", "SELECT COUNT(*) as total");
    const { total } = db.prepare(countQuery).get(...params) as { total: number };

    query += " ORDER BY id LIMIT ? OFFSET ?";
    params.push(pageSize, offset);
    const rows = db.prepare(query).all(...params);

    const totalPages = Math.ceil(total / pageSize);
    const hasMore = page < totalPages;

    let text = formatTable(rows);
    text += `\n\nPage ${page}/${totalPages} (${total} total)`;
    if (hasMore) {
      text += `\nNext: call with page: ${page + 1}`;
    }

    return { content: [{ type: "text", text }] };
  }
);
```

## Python

```python
@mcp.tool()
def list_items(page: int = 1, page_size: int = 20, filter: str = "") -> str:
    """List items with pagination. Use page parameter to navigate results.

    Args:
        page: Page number (starts at 1)
        page_size: Items per page (max 100)
        filter: Optional filter term
    """
    page_size = min(page_size, 100)
    offset = (page - 1) * page_size

    query = "SELECT * FROM items"
    params: list = []

    if filter:
        query += " WHERE name LIKE ?"
        params.append(f"%{filter}%")

    total = db.execute(
        query.replace("SELECT *", "SELECT COUNT(*) as total"), params
    ).fetchone()["total"]

    rows = db.execute(f"{query} LIMIT ? OFFSET ?", [*params, page_size, offset]).fetchall()
    total_pages = -(-total // page_size)  # ceil division

    result = format_table(rows)
    result += f"\n\nPage {page}/{total_pages} ({total} total)"
    if page < total_pages:
        result += f"\nNext: call with page: {page + 1}"

    return result
```

## Why This Pattern Works

- The AI sees "Page 1/5 — Next: call with page: 2" and knows how to continue
- Bounded result sets prevent overwhelming context windows
- Filters reduce data before pagination for faster navigation
