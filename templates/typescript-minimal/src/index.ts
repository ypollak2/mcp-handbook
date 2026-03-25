import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const server = new McpServer({
  name: "my-mcp-server",
  version: "1.0.0",
});

// --- Add your tools here ---

server.tool(
  "hello",
  "Say hello to someone",
  { name: z.string().describe("Name to greet") },
  async ({ name }) => ({
    content: [{ type: "text", text: `Hello, ${name}!` }],
  })
);

// --- Add your resources here ---

server.resource("info", "info://server", async (uri) => ({
  contents: [
    {
      uri: uri.href,
      mimeType: "text/plain",
      text: "My MCP Server v1.0.0",
    },
  ],
}));

// --- Start the server ---

const transport = new StdioServerTransport();
await server.connect(transport);
