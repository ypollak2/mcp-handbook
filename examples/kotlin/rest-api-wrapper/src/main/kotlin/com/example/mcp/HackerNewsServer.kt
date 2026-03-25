package com.example.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun runHackerNewsServer() {
    createHttpClient().use { httpClient ->
        val server = Server(
            serverInfo = Implementation(
                name = "hackernews-kotlin",
                version = "1.0.0",
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
        )

        server.registerNewsTools(httpClient)

        val transport = StdioServerTransport(
            inputStream = System.`in`.asInput(),
            outputStream = System.out.asSink().buffered(),
        )

        runBlocking {
            val session = server.createSession(transport)
            val done = Job()
            session.onClose {
                done.complete()
            }
            done.join()
        }
    }
}

private fun createHttpClient(): HttpClient = HttpClient(CIO) {
    defaultRequest {
        url("https://hacker-news.firebaseio.com/v0")
        headers {
            append("Accept", "application/json")
            append("User-Agent", "mcp-handbook-hackernews-kotlin/1.0")
        }
        contentType(ContentType.Application.Json)
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            },
        )
    }
}

private fun Server.registerNewsTools(httpClient: HttpClient) {
    addTool(
        name = "top_stories",
        description = "Get the current top stories on Hacker News.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("count") {
                    put("type", "integer")
                    put("description", "Number of stories to return (max 30)")
                    put("minimum", 1)
                    put("maximum", 30)
                }
            },
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val count = request.arguments?.get("count")?.jsonPrimitive?.intOrNull ?: 10
        val topCount = count.coerceIn(1, 30)

        hackerNewsResult {
            val ids = httpClient.get("/topstories.json").body<List<Long>>().take(topCount)
            val stories = ids.mapNotNull { httpClient.fetchItem(it) }

            if (stories.isEmpty()) {
                return@hackerNewsResult textResult("No stories found.")
            }

            val text = stories.mapIndexed { index, story ->
                buildString {
                    append("${index + 1}. ${story.title ?: "(untitled)"} (${story.score ?: 0} points)\n")
                    append("   ${story.url ?: "https://news.ycombinator.com/item?id=${story.id}"}\n")
                    append("   by ${story.by ?: "unknown"} | ${story.descendants ?: 0} comments")
                }
            }.joinToString("\n\n")

            textResult(text)
        }
    }

    addTool(
        name = "get_story",
        description = "Get details about a specific Hacker News story including its top comments.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "integer")
                    put("description", "Hacker News story ID")
                }
                putJsonObject("commentCount") {
                    put("type", "integer")
                    put("description", "Number of top comments to include")
                    put("minimum", 0)
                    put("maximum", 20)
                }
            },
            required = listOf("id"),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val id = request.arguments?.get("id")?.jsonPrimitive?.intOrNull
            ?: return@addTool errorResult("The 'id' parameter is required.")
        val commentCount = request.arguments?.get("commentCount")?.jsonPrimitive?.intOrNull ?: 5

        hackerNewsResult {
            val story = httpClient.fetchItem(id.toLong())
                ?: return@hackerNewsResult errorResult("Story $id not found.")

            val lines = mutableListOf(
                "# ${story.title ?: "(untitled)"}",
                "",
                "Score: ${story.score ?: 0} | By: ${story.by ?: "unknown"} | Comments: ${story.descendants ?: 0}",
                "URL: ${story.url ?: "N/A"}",
                "HN: https://news.ycombinator.com/item?id=${story.id}",
            )

            if (!story.text.isNullOrBlank()) {
                lines += ""
                lines += stripHtml(story.text)
            }

            val kidIds = story.kids.orEmpty().take(commentCount.coerceAtLeast(0))
            if (kidIds.isNotEmpty()) {
                lines += ""
                lines += "--- Top Comments ---"
                lines += ""

                kidIds.forEach { kidId ->
                    val comment = httpClient.fetchItem(kidId)
                    if (comment?.text.isNullOrBlank()) {
                        return@forEach
                    }

                    lines += "[${comment?.by ?: "unknown"}]:"
                    lines += stripHtml(comment?.text.orEmpty())
                    lines += ""
                }
            }

            textResult(lines.joinToString("\n").trimEnd())
        }
    }

    addTool(
        name = "search_user",
        description = "Get information about a Hacker News user.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("username") {
                    put("type", "string")
                    put("description", "Hacker News username")
                }
            },
            required = listOf("username"),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val username = request.arguments?.get("username")?.jsonPrimitive?.content
            ?: return@addTool errorResult("The 'username' parameter is required.")

        hackerNewsResult {
            val user = httpClient.fetchUser(username)
                ?: return@hackerNewsResult errorResult("User \"$username\" not found.")

            val lines = mutableListOf(
                "User: ${user.id}",
                "Karma: ${user.karma}",
                "Member since: ${formatUnixDate(user.created)}",
                "Submissions: ${user.submitted?.size ?: 0}",
            )

            if (!user.about.isNullOrBlank()) {
                lines += ""
                lines += "About:"
                lines += stripHtml(user.about)
            }

            textResult(lines.joinToString("\n"))
        }
    }
}

private suspend fun HttpClient.fetchItem(id: Long): HackerNewsItem? {
    return try {
        get("/item/$id.json").body<HackerNewsItem?>()
    } catch (_: Throwable) {
        null
    }
}

private suspend fun HttpClient.fetchUser(username: String): HackerNewsUser? {
    return try {
        get("/user/$username.json").body<HackerNewsUser?>()
    } catch (_: Throwable) {
        null
    }
}

private suspend fun hackerNewsResult(block: suspend () -> CallToolResult): CallToolResult {
    return try {
        block()
    } catch (error: Throwable) {
        errorResult("Hacker News API request failed: ${error.message ?: "unknown error"}")
    }
}

private fun textResult(text: String): CallToolResult {
    return CallToolResult(content = listOf(TextContent(text)))
}

private fun errorResult(text: String): CallToolResult {
    return CallToolResult(content = listOf(TextContent(text)), isError = true)
}

private fun formatUnixDate(seconds: Long): String {
    return java.time.Instant.ofEpochSecond(seconds)
        .atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

private fun stripHtml(html: String): String {
    return html
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
}

@Serializable
private data class HackerNewsItem(
    @SerialName("id") val id: Long,
    @SerialName("type") val type: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("text") val text: String? = null,
    @SerialName("by") val by: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("score") val score: Int? = null,
    @SerialName("descendants") val descendants: Int? = null,
    @SerialName("time") val time: Long? = null,
    @SerialName("kids") val kids: List<Long>? = null,
)

@Serializable
private data class HackerNewsUser(
    @SerialName("id") val id: String,
    @SerialName("created") val created: Long,
    @SerialName("karma") val karma: Int,
    @SerialName("about") val about: String? = null,
    @SerialName("submitted") val submitted: List<Long>? = null,
)
