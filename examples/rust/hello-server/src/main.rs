//! MCP Server Example in Rust
//!
//! A minimal MCP server using the rmcp SDK.
//! Demonstrates tools and the stdio transport.
//!
//! Run: cargo run
//! Test: npx @modelcontextprotocol/inspector cargo run

use rmcp::model::{
    CallToolResult, Content, ServerCapabilities, ServerInfo, Tool, ToolInputSchema,
};
use rmcp::server::Server;
use rmcp::transport::io::stdio;
use serde_json::{json, Value};
use std::collections::HashMap;

#[derive(Clone)]
struct HelloServer;

impl Server for HelloServer {
    fn get_server_info(&self) -> ServerInfo {
        ServerInfo {
            name: "hello-server".into(),
            version: "1.0.0".into(),
        }
    }

    fn get_capabilities(&self) -> ServerCapabilities {
        ServerCapabilities {
            tools: Some(json!({})),
            ..Default::default()
        }
    }

    fn list_tools(&self) -> Vec<Tool> {
        vec![
            Tool {
                name: "count_words".into(),
                description: Some("Count the number of words in a text".into()),
                input_schema: ToolInputSchema {
                    r#type: "object".into(),
                    properties: {
                        let mut props = HashMap::new();
                        props.insert(
                            "text".into(),
                            json!({
                                "type": "string",
                                "description": "The text to count words in"
                            }),
                        );
                        Some(props)
                    },
                    required: Some(vec!["text".into()]),
                },
            },
            Tool {
                name: "reverse_text".into(),
                description: Some("Reverse a string of text".into()),
                input_schema: ToolInputSchema {
                    r#type: "object".into(),
                    properties: {
                        let mut props = HashMap::new();
                        props.insert(
                            "text".into(),
                            json!({
                                "type": "string",
                                "description": "The text to reverse"
                            }),
                        );
                        Some(props)
                    },
                    required: Some(vec!["text".into()]),
                },
            },
        ]
    }

    fn call_tool(&self, name: &str, arguments: Value) -> CallToolResult {
        match name {
            "count_words" => {
                let text = arguments["text"].as_str().unwrap_or("");
                let count = text.split_whitespace().count();
                CallToolResult {
                    content: vec![Content::text(format!(
                        "The text contains {} words.",
                        count
                    ))],
                    is_error: false,
                }
            }
            "reverse_text" => {
                let text = arguments["text"].as_str().unwrap_or("");
                let reversed: String = text.chars().rev().collect();
                CallToolResult {
                    content: vec![Content::text(reversed)],
                    is_error: false,
                }
            }
            _ => CallToolResult {
                content: vec![Content::text(format!("Unknown tool: {}", name))],
                is_error: true,
            },
        }
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let server = HelloServer;
    let transport = stdio();
    rmcp::serve(server, transport).await?;
    Ok(())
}
