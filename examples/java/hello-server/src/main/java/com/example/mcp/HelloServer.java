package com.example.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP Server Example in Java
 *
 * A minimal MCP server using the official Java SDK.
 * Demonstrates tools and the stdio transport.
 *
 * Build: mvn package
 * Run: java -jar target/mcp-hello-server-1.0.0.jar
 * Test: npx @modelcontextprotocol/inspector java -jar target/mcp-hello-server-1.0.0.jar
 */
public class HelloServer {

    public static void main(String[] args) {
        var transportProvider = new StdioServerTransportProvider();

        var server = McpServer.sync(transportProvider)
            .serverInfo("hello-server", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build())
            .tool(
                new McpServerFeatures.SyncToolSpecification(
                    new McpSchema.Tool(
                        "count_words",
                        "Count the number of words in a text",
                        new McpSchema.JsonSchema(
                            "object",
                            Map.of("text", Map.of(
                                "type", "string",
                                "description", "The text to count words in"
                            )),
                            List.of("text")
                        )
                    ),
                    (exchange, args2) -> {
                        String text = (String) args2.get("text");
                        int count = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("The text contains " + count + " words.")),
                            false
                        );
                    }
                )
            )
            .tool(
                new McpServerFeatures.SyncToolSpecification(
                    new McpSchema.Tool(
                        "reverse_text",
                        "Reverse a string of text",
                        new McpSchema.JsonSchema(
                            "object",
                            Map.of("text", Map.of(
                                "type", "string",
                                "description", "The text to reverse"
                            )),
                            List.of("text")
                        )
                    ),
                    (exchange, args2) -> {
                        String text = (String) args2.get("text");
                        String reversed = new StringBuilder(text).reverse().toString();
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(reversed)),
                            false
                        );
                    }
                )
            )
            .build();

        // Server runs until stdin is closed
    }
}
