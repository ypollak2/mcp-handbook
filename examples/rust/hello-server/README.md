# Hello Server — MCP Example in Rust

A minimal MCP server in Rust using the [rmcp](https://crates.io/crates/rmcp) SDK.

## What It Demonstrates

- **Tools**: `count_words`, `reverse_text`
- **Pattern**: Trait-based server implementation, stdio transport

## Quick Start

```bash
cargo run
```

## Test with Inspector

```bash
npx @modelcontextprotocol/inspector cargo run
```

## Connect to Claude Desktop

Build first, then point to the binary:

```bash
cargo build --release
```

```json
{
  "mcpServers": {
    "hello-rust": {
      "command": "/absolute/path/to/target/release/mcp-hello-server"
    }
  }
}
```

## Rust MCP Ecosystem

The Rust MCP ecosystem is newer but growing. Key crates:

| Crate | Description |
|-------|-------------|
| [rmcp](https://crates.io/crates/rmcp) | Most popular Rust MCP SDK |
| [mcp-server](https://crates.io/crates/mcp-server) | Alternative SDK |

## Why Rust for MCP?

- **Zero-overhead startup** — compiled binary starts in milliseconds
- **Memory safety** — no null pointer crashes or data races
- **Tiny binaries** — single static binary, no runtime needed
- **Performance** — ideal for compute-heavy tools (data processing, parsing)
- **WASM potential** — compile to WebAssembly for browser-based MCP

## Note

The Rust MCP SDK is evolving rapidly. Check the [rmcp repository](https://github.com/anthropics/rmcp) for the latest API. The code in this example may need minor adjustments for newer SDK versions.
