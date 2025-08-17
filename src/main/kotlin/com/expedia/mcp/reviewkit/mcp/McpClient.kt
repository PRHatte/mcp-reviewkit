package com.expedia.mcp.reviewkit.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.Url
import io.ktor.serialization.jackson.jackson
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

class McpClient(private val serverUrl: String, private val authToken: String? = null) : AutoCloseable {
    private val client = HttpClient(CIO) { install(WebSockets); install(ContentNegotiation) { jackson() } }
    private var session: WebSocketSession? = null
    private var initialized: Boolean = false
    private val idCounter = AtomicLong(1)
    private val mapper = jacksonObjectMapper()

    suspend fun connect() {
        if (session != null) return
        session = client.webSocketSession {
            url(Url(serverUrl))
            if (!authToken.isNullOrBlank()) headers.append("Authorization", "Bearer $authToken")
        }
    }

    suspend fun initializeIfNeeded() {
        if (initialized) return
        val params = mapOf(
            "clientInfo" to mapOf("name" to "mcp-reviewkit", "version" to "0.1.0"),
            "capabilities" to mapOf("tools" to mapOf("callTool" to true))
        )
        request("initialize", params)
        notify("initialized", emptyMap<String, Any>())
        initialized = true
    }

    suspend fun callTool(tool: String, arguments: Map<String, Any?> = emptyMap()): JsonNode {
        connect(); initializeIfNeeded()
        val params = mapOf("name" to tool, "arguments" to arguments)
        val response = request("tools/call", params)
        val resultNode = response.get("result") ?: response
        val content = resultNode.get("content")
        if (content != null && content.isArray && content.size() > 0) {
            val first = content[0]
            if (first.has("json")) return first.get("json")
            if (first.has("text")) {
                val text = first.get("text").asText()
                return try { mapper.readTree(text) } catch (_: Exception) {
                    mapper.createObjectNode().put("text", text)
                }
            }
        }
        return resultNode
    }

    private suspend fun notify(method: String, params: Any) {
        val obj = mapper.createObjectNode()
        obj.put("jsonrpc", "2.0")
        obj.put("method", method)
        obj.set<JsonNode>("params", mapper.valueToTree(params))
        send(obj)
    }

    private suspend fun request(method: String, params: Any): JsonNode {
        val id = idCounter.getAndIncrement()
        val obj = mapper.createObjectNode()
        obj.put("jsonrpc", "2.0")
        obj.put("id", id)
        obj.put("method", method)
        obj.set<JsonNode>("params", mapper.valueToTree(params))
        send(obj)
        return awaitResponse(id)
    }

    private suspend fun send(node: JsonNode) {
        val s = session ?: error("Not connected")
        s.send(Frame.Text(mapper.writeValueAsString(node)))
    }

    private suspend fun awaitResponse(id: Long): JsonNode {
        val s = session ?: error("Not connected")
        return withTimeout(30_000) {
            while (true) {
                val frame = try { s.incoming.receive() } catch (e: ClosedReceiveChannelException) { error("Connection closed") }
                if (frame is Frame.Text) {
                    val node = mapper.readTree(frame.readText())
                    if (node.has("id") && node.get("id").asLong() == id) {
                        if (node.has("error")) error("MCP error: ${node.get("error")}")
                        return@withTimeout node
                    }
                }
            }
        }
    }

    override fun close() { try { session?.close() } catch (_: Exception) {}; try { client.close() } catch (_: Exception) {} }
}
