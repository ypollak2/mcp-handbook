# Recipes

Quick, copy-paste solutions for common MCP development tasks. Each recipe is self-contained and can be dropped into any MCP server.

## Index

### Server Setup
| Recipe | What It Solves |
|--------|---------------|
| [Environment Config](environment-config.md) | Load and validate environment variables at startup |
| [Graceful Shutdown](graceful-shutdown.md) | Clean up resources when the server stops |
| [Structured Logging](structured-logging.md) | Log to stderr without breaking MCP protocol |

### Tool Patterns
| Recipe | What It Solves |
|--------|---------------|
| [Paginated Results](paginated-results.md) | Return large datasets in pages |
| [Long-Running Operations](long-running-operations.md) | Report progress on slow tasks |
| [Confirmation Pattern](confirmation-pattern.md) | Two-step tools for dangerous operations |
| [Batch Operations](batch-operations.md) | Process multiple items with partial failure handling |

### Data & APIs
| Recipe | What It Solves |
|--------|---------------|
| [REST API Client](rest-api-client.md) | Reusable fetch wrapper with auth, retries, timeouts |
| [Database Connection](database-connection.md) | Connection pooling with health checks |
| [Caching Layer](caching-layer.md) | In-memory TTL cache for expensive operations |

### Security
| Recipe | What It Solves |
|--------|---------------|
| [Input Sanitization](input-sanitization.md) | Validate and clean all tool inputs |
| [Rate Limiter](rate-limiter.md) | Throttle requests to external services |
| [Path Sandboxing](path-sandboxing.md) | Restrict file access to allowed directories |
