package com.example.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun main(): Unit = runBlocking {
    val server = Server(
        serverInfo = Implementation(
            name = "hello-server",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )

    server.addTool(
        name = "count_words",
        description = "Count the number of words in a text.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("text", buildJsonObject { put("type", "string") })
            },
            required = listOf("text"),
        ),
    ) { request ->
        val text = request.arguments?.get("text")?.jsonPrimitive?.content.orEmpty()
        val count = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size

        CallToolResult(
            content = listOf(TextContent("The text contains $count words.")),
        )
    }

    server.addTool(
        name = "reverse_text",
        description = "Reverse a string of text.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("text", buildJsonObject { put("type", "string") })
            },
            required = listOf("text"),
        ),
    ) { request ->
        val text = request.arguments?.get("text")?.jsonPrimitive?.content.orEmpty()

        CallToolResult(
            content = listOf(TextContent(text.reversed())),
        )
    }

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )

    val session = server.createSession(transport)
    val done = Job()
    session.onClose {
        done.complete()
    }
    done.join()
}
