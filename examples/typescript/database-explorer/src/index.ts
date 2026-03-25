import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import Database from "better-sqlite3";
import { resolve } from "path";

// --- Configuration ---

const DB_PATH = process.env.DB_PATH || resolve(import.meta.dirname, "../sample.db");
const MAX_ROWS = 100;

const db = new Database(DB_PATH, { readonly: true });
const server = new McpServer({
  name: "database-explorer",
  version: "1.0.0",
});

// --- Safety ---

const FORBIDDEN_KEYWORDS = [
  "INSERT",
  "UPDATE",
  "DELETE",
  "DROP",
  "ALTER",
  "TRUNCATE",
  "CREATE",
  "GRANT",
  "REVOKE",
];

function assertReadOnly(sql: string): void {
  const upper = sql.toUpperCase().trim();
  for (const keyword of FORBIDDEN_KEYWORDS) {
    if (upper.startsWith(keyword) || upper.includes(` ${keyword} `)) {
      throw new Error(
        `Query rejected: ${keyword} statements are not allowed. This server is read-only.`
      );
    }
  }
}

// --- Tools ---

server.tool(
  "query",
  "Execute a read-only SQL query against the database. Only SELECT statements are allowed.",
  {
    sql: z.string().describe("SQL SELECT query to execute"),
    limit: z
      .number()
      .optional()
      .default(20)
      .describe(`Max rows to return (max ${MAX_ROWS})`),
  },
  async ({ sql, limit }) => {
    assertReadOnly(sql);

    const effectiveLimit = Math.min(limit, MAX_ROWS);

    // Add LIMIT if not already present
    const hasLimit = /\bLIMIT\b/i.test(sql);
    const boundedSql = hasLimit ? sql : `${sql} LIMIT ${effectiveLimit}`;

    try {
      const rows = db.prepare(boundedSql).all();
      const result =
        rows.length === 0
          ? "No results found."
          : formatTable(rows as Record<string, unknown>[]);

      return {
        content: [{ type: "text", text: result }],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Query error: ${error instanceof Error ? error.message : String(error)}`,
          },
        ],
        isError: true,
      };
    }
  }
);

server.tool(
  "describe_table",
  "Show the schema of a specific table including column names, types, and constraints",
  {
    table: z.string().describe("Table name to describe"),
  },
  async ({ table }) => {
    // Validate table name to prevent injection
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(table)) {
      return {
        content: [{ type: "text", text: "Invalid table name." }],
        isError: true,
      };
    }

    try {
      const columns = db.prepare(`PRAGMA table_info(${table})`).all() as Array<{
        name: string;
        type: string;
        notnull: number;
        dflt_value: string | null;
        pk: number;
      }>;

      if (columns.length === 0) {
        return {
          content: [{ type: "text", text: `Table "${table}" not found.` }],
          isError: true,
        };
      }

      const schema = columns
        .map((col) => {
          const parts = [`${col.name} ${col.type}`];
          if (col.pk) parts.push("PRIMARY KEY");
          if (col.notnull) parts.push("NOT NULL");
          if (col.dflt_value !== null) parts.push(`DEFAULT ${col.dflt_value}`);
          return parts.join(" ");
        })
        .join("\n");

      const rowCount = db
        .prepare(`SELECT COUNT(*) as count FROM ${table}`)
        .get() as { count: number };

      return {
        content: [
          {
            type: "text",
            text: `Table: ${table} (${rowCount.count} rows)\n\n${schema}`,
          },
        ],
      };
    } catch (error) {
      return {
        content: [
          {
            type: "text",
            text: `Error: ${error instanceof Error ? error.message : String(error)}`,
          },
        ],
        isError: true,
      };
    }
  }
);

// --- Resources ---

server.resource("schema", "db://schema", async (uri) => {
  const tables = db
    .prepare(
      "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    )
    .all() as Array<{ name: string }>;

  const schema = tables
    .map((t) => {
      const columns = db.prepare(`PRAGMA table_info(${t.name})`).all() as Array<{
        name: string;
        type: string;
        pk: number;
      }>;
      const cols = columns
        .map((c) => `  ${c.name} ${c.type}${c.pk ? " PK" : ""}`)
        .join("\n");
      return `${t.name}\n${cols}`;
    })
    .join("\n\n");

  return {
    contents: [
      {
        uri: uri.href,
        mimeType: "text/plain",
        text: `Database Schema\n${"=".repeat(40)}\n\n${schema}`,
      },
    ],
  };
});

// --- Prompts ---

server.prompt(
  "explore-data",
  "Explore the database: understand schema, find patterns, and answer questions",
  {},
  () => ({
    messages: [
      {
        role: "user",
        content: {
          type: "text",
          text: [
            "I'd like to explore this database. Please:",
            "",
            "1. Read the db://schema resource to understand the tables",
            "2. Use describe_table on each table to see details",
            "3. Run a few queries to understand the data (row counts, sample rows, value distributions)",
            "4. Summarize what you found: what data is here, how tables relate, and any interesting patterns",
          ].join("\n"),
        },
      },
    ],
  })
);

// --- Helpers ---

function formatTable(rows: Record<string, unknown>[]): string {
  if (rows.length === 0) return "No results.";

  const columns = Object.keys(rows[0]);
  const widths = columns.map((col) =>
    Math.max(
      col.length,
      ...rows.map((row) => String(row[col] ?? "NULL").length)
    )
  );

  const header = columns.map((col, i) => col.padEnd(widths[i])).join(" | ");
  const separator = widths.map((w) => "-".repeat(w)).join("-+-");
  const body = rows
    .map((row) =>
      columns
        .map((col, i) => String(row[col] ?? "NULL").padEnd(widths[i]))
        .join(" | ")
    )
    .join("\n");

  return `${header}\n${separator}\n${body}\n\n(${rows.length} rows)`;
}

// --- Start ---

const transport = new StdioServerTransport();
await server.connect(transport);
