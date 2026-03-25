// MCP Server Example in Go
//
// A minimal MCP server using the mark3labs/mcp-go SDK.
// Demonstrates tools, resources, and connecting to AI clients.
//
// Run: go run main.go
// Test: npx @modelcontextprotocol/inspector go run main.go

package main

import (
	"context"
	"fmt"
	"strings"

	"github.com/mark3labs/mcp-go/mcp"
	"github.com/mark3labs/mcp-go/server"
)

func main() {
	s := server.NewMCPServer(
		"hello-server",
		"1.0.0",
		server.WithToolCapabilities(true),
		server.WithResourceCapabilities(true, false),
	)

	// --- Tools ---

	// Word counter tool
	wordCountTool := mcp.NewTool("count_words",
		mcp.WithDescription("Count the number of words in a text"),
		mcp.WithString("text",
			mcp.Required(),
			mcp.Description("The text to count words in"),
		),
	)

	s.AddTool(wordCountTool, func(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
		text, _ := req.Params.Arguments["text"].(string)
		words := strings.Fields(text)
		count := len(words)

		return mcp.NewToolResultText(fmt.Sprintf("The text contains %d words.", count)), nil
	})

	// Reverse text tool
	reverseTool := mcp.NewTool("reverse_text",
		mcp.WithDescription("Reverse a string of text"),
		mcp.WithString("text",
			mcp.Required(),
			mcp.Description("The text to reverse"),
		),
	)

	s.AddTool(reverseTool, func(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
		text, _ := req.Params.Arguments["text"].(string)
		runes := []rune(text)
		for i, j := 0, len(runes)-1; i < j; i, j = i+1, j-1 {
			runes[i], runes[j] = runes[j], runes[i]
		}

		return mcp.NewToolResultText(string(runes)), nil
	})

	// --- Resources ---

	s.AddResource(mcp.NewResource(
		"info://server",
		"Server information",
		mcp.WithResourceDescription("Information about this MCP server"),
		mcp.WithMIMEType("text/plain"),
	), func(ctx context.Context, req mcp.ReadResourceRequest) ([]mcp.ResourceContents, error) {
		return []mcp.ResourceContents{
			mcp.TextResourceContents{
				URI:      "info://server",
				MIMEType: "text/plain",
				Text:     "Hello Server v1.0.0 — A minimal MCP server example in Go.",
			},
		}, nil
	})

	// --- Start via stdio ---

	if err := server.ServeStdio(s); err != nil {
		fmt.Printf("Server error: %v\n", err)
	}
}
