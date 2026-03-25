from mcp.server.fastmcp import FastMCP

mcp = FastMCP("my-mcp-server")


# --- Add your tools here ---

@mcp.tool()
def hello(name: str) -> str:
    """Say hello to someone."""
    return f"Hello, {name}!"


# --- Add your resources here ---

@mcp.resource("info://server")
def server_info() -> str:
    """Information about this server."""
    return "My MCP Server v1.0.0"


# --- Start the server ---

if __name__ == "__main__":
    mcp.run()
