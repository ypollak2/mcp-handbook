/**
 * HackerNews MCP Server
 *
 * Wraps the public HackerNews API as an MCP server.
 * No auth needed — perfect for learning the API Wrapper pattern.
 *
 * Pattern: API Wrapper (see guides/03-architecture-patterns.md)
 */
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const server = new McpServer({
  name: "hackernews",
  version: "1.0.0",
});

const BASE_URL = "https://hacker-news.firebaseio.com/v0";
const TIMEOUT_MS = 10_000;

// --- API Helper ---

async function hnFetch<T>(path: string): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const response = await fetch(`${BASE_URL}${path}.json`, {
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`HN API error: ${response.status} ${response.statusText}`);
    }

    return (await response.json()) as T;
  } finally {
    clearTimeout(timeout);
  }
}

// --- Types ---

interface HNItem {
  id: number;
  type: string;
  title?: string;
  text?: string;
  by?: string;
  url?: string;
  score?: number;
  descendants?: number;
  time?: number;
  kids?: number[];
}

// --- Tools ---

server.tool(
  "top_stories",
  "Get the current top stories on HackerNews",
  {
    count: z
      .number()
      .min(1)
      .max(30)
      .optional()
      .default(10)
      .describe("Number of stories to return (max 30)"),
  },
  async ({ count }) => {
    const ids = await hnFetch<number[]>("/topstories");
    const topIds = ids.slice(0, count);

    const stories = await Promise.all(
      topIds.map((id) => hnFetch<HNItem>(`/item/${id}`))
    );

    const formatted = stories
      .map(
        (s, i) =>
          `${i + 1}. ${s.title} (${s.score} points)\n   ${s.url || `https://news.ycombinator.com/item?id=${s.id}`}\n   by ${s.by} | ${s.descendants || 0} comments`
      )
      .join("\n\n");

    return {
      content: [{ type: "text", text: formatted }],
    };
  }
);

server.tool(
  "get_story",
  "Get details about a specific HackerNews story including its top comments",
  {
    id: z.number().describe("HackerNews story ID"),
    commentCount: z
      .number()
      .optional()
      .default(5)
      .describe("Number of top comments to include"),
  },
  async ({ id, commentCount }) => {
    const item = await hnFetch<HNItem>(`/item/${id}`);

    if (!item) {
      return {
        content: [{ type: "text", text: `Story ${id} not found.` }],
        isError: true,
      };
    }

    let text = `# ${item.title}\n\n`;
    text += `Score: ${item.score} | By: ${item.by} | Comments: ${item.descendants || 0}\n`;
    text += `URL: ${item.url || "N/A"}\n`;
    text += `HN: https://news.ycombinator.com/item?id=${item.id}\n`;

    if (item.text) {
      text += `\n${stripHtml(item.text)}\n`;
    }

    // Fetch top comments
    if (item.kids && item.kids.length > 0) {
      const commentIds = item.kids.slice(0, commentCount);
      const comments = await Promise.all(
        commentIds.map((cid) => hnFetch<HNItem>(`/item/${cid}`))
      );

      text += "\n--- Top Comments ---\n\n";
      for (const comment of comments) {
        if (comment && comment.text) {
          text += `[${comment.by}]:\n${stripHtml(comment.text)}\n\n`;
        }
      }
    }

    return {
      content: [{ type: "text", text }],
    };
  }
);

server.tool(
  "search_user",
  "Get information about a HackerNews user",
  {
    username: z.string().describe("HackerNews username"),
  },
  async ({ username }) => {
    try {
      const user = await hnFetch<{
        id: string;
        created: number;
        karma: number;
        about?: string;
        submitted?: number[];
      }>(`/user/${username}`);

      if (!user) {
        return {
          content: [{ type: "text", text: `User "${username}" not found.` }],
          isError: true,
        };
      }

      const created = new Date(user.created * 1000).toLocaleDateString();
      let text = `User: ${user.id}\n`;
      text += `Karma: ${user.karma}\n`;
      text += `Member since: ${created}\n`;
      text += `Submissions: ${user.submitted?.length || 0}\n`;

      if (user.about) {
        text += `\nAbout:\n${stripHtml(user.about)}`;
      }

      return {
        content: [{ type: "text", text }],
      };
    } catch {
      return {
        content: [{ type: "text", text: `User "${username}" not found.` }],
        isError: true,
      };
    }
  }
);

// --- Resources ---

server.resource("status", "hn://status", async (uri) => {
  const [topIds, newIds] = await Promise.all([
    hnFetch<number[]>("/topstories"),
    hnFetch<number[]>("/newstories"),
  ]);

  return {
    contents: [
      {
        uri: uri.href,
        mimeType: "text/plain",
        text: `HackerNews Status\n  Top stories: ${topIds.length}\n  New stories: ${newIds.length}`,
      },
    ],
  };
});

// --- Helpers ---

function stripHtml(html: string): string {
  return html
    .replace(/<[^>]*>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#x27;/g, "'");
}

// --- Start ---

const transport = new StdioServerTransport();
await server.connect(transport);
