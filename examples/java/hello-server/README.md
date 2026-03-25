# Hello Server — MCP Example in Java

A minimal MCP server in Java using the [official MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk).

## What It Demonstrates

- **Tools**: `count_words`, `reverse_text`
- **Pattern**: Builder-based server setup, sync tool handlers, stdio transport

## Quick Start

```bash
mvn package
java -jar target/mcp-hello-server-1.0.0.jar
```

## Test with Inspector

```bash
npx @modelcontextprotocol/inspector java -jar target/mcp-hello-server-1.0.0.jar
```

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "hello-java": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/target/mcp-hello-server-1.0.0.jar"]
    }
  }
}
```

## Java MCP SDK

The official Java SDK (`io.modelcontextprotocol:sdk`) provides:

| Feature | API |
|---------|-----|
| Sync server | `McpServer.sync(transport)` |
| Async server | `McpServer.async(transport)` |
| Tool registration | `.tool(new SyncToolSpecification(...))` |
| Resource registration | `.resource(new SyncResourceSpecification(...))` |
| Stdio transport | `new StdioServerTransportProvider()` |
| SSE transport | `new HttpServletSseServerTransportProvider(...)` |

## Why Java for MCP?

- **Enterprise adoption** — Java dominates enterprise backends
- **Spring Boot integration** — `spring-ai-mcp` integrates MCP into Spring applications
- **Mature ecosystem** — access to thousands of libraries for databases, APIs, and protocols
- **Async support** — `McpServer.async()` for non-blocking tool handlers
- **Type safety** — strong typing catches errors at compile time

## Spring Boot Integration

For production Java MCP servers, consider using Spring Boot:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
</dependency>
```

This gives you dependency injection, auto-configuration, and the full Spring ecosystem.
