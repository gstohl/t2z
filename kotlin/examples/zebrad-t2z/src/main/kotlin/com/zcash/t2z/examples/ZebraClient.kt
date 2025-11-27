/**
 * Zebra RPC Client
 *
 * Pure Java HttpClient-based JSON-RPC client for Zebra - minimal dependencies.
 */
package com.zcash.t2z.examples

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

@Serializable
data class BlockchainInfo(
    val chain: String,
    val blocks: Int,
    val headers: Int? = null,
    val bestblockhash: String,
    val difficulty: Double? = null,
    val verificationprogress: Double? = null
)

@Serializable
data class UTXO(
    val txid: String,
    val vout: Int,
    val address: String,
    val scriptPubKey: String,
    val amount: Long, // in zatoshis
    val height: Int
)

class ZebraClient(
    host: String? = null,
    port: Int? = null
) {
    private val url: String
    private val authHeader: String?
    private var idCounter = 0
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newHttpClient()

    init {
        val h = host ?: System.getenv("ZEBRA_HOST") ?: "localhost"
        val p = port ?: System.getenv("ZEBRA_PORT")?.toIntOrNull() ?: 18232
        url = "http://$h:$p"

        val user = System.getenv("RPC_USER")
        val pass = System.getenv("RPC_PASSWORD")
        authHeader = if (user != null && pass != null) {
            "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
        } else null
    }

    /**
     * Make a raw JSON-RPC call
     */
    private inline fun <reified T> rawCall(method: String, params: JsonArray = buildJsonArray {}): T {
        val requestBody = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
            put("id", ++idCounter)
        }.toString()

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))

        authHeader?.let { requestBuilder.header("Authorization", it) }

        val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw Exception("HTTP error: ${response.statusCode()}")
        }

        val jsonResponse = json.parseToJsonElement(response.body()).jsonObject

        val error = jsonResponse["error"]
        if (error != null && error != JsonNull) {
            throw Exception("RPC error: $error")
        }

        return json.decodeFromJsonElement(jsonResponse["result"]!!)
    }

    /**
     * Wait for the node to be ready
     */
    suspend fun waitForReady(maxAttempts: Int = 30, delayMs: Long = 1000) {
        repeat(maxAttempts) { i ->
            try {
                getBlockchainInfo()
                return
            } catch (e: Exception) {
                if (i == maxAttempts - 1) {
                    throw Exception("Node not ready after $maxAttempts attempts: ${e.message}")
                }
                delay(delayMs)
            }
        }
    }

    /**
     * Get blockchain info
     */
    fun getBlockchainInfo(): BlockchainInfo = rawCall("getblockchaininfo")

    /**
     * Get current block count
     */
    fun getBlockCount(): Int = rawCall("getblockcount")

    /**
     * Get block hash by height
     */
    fun getBlockHash(height: Int): String = rawCall("getblockhash", buildJsonArray { add(height) })

    /**
     * Get block by hash
     */
    fun getBlock(hash: String, verbosity: Int = 1): JsonObject =
        rawCall("getblock", buildJsonArray { add(hash); add(verbosity) })

    /**
     * Send a raw transaction
     */
    fun sendRawTransaction(hexString: String): String =
        rawCall("sendrawtransaction", buildJsonArray { add(hexString) })

    /**
     * Get raw transaction
     */
    fun getRawTransaction(txid: String, verbose: Boolean = false): JsonElement =
        rawCall("getrawtransaction", buildJsonArray { add(txid); add(if (verbose) 1 else 0) })

    /**
     * Wait for blocks to reach target height
     */
    suspend fun waitForBlocks(targetHeight: Int, timeoutMs: Long = 120000): Int {
        val startTime = System.currentTimeMillis()
        var lastHeight = 0

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val info = getBlockchainInfo()
            if (info.blocks >= targetHeight) {
                return info.blocks
            }
            if (info.blocks != lastHeight) {
                lastHeight = info.blocks
                println("  Block height: ${info.blocks}")
            }
            delay(1000)
        }

        throw Exception("Timeout waiting for height $targetHeight")
    }

    fun close() {
        // HttpClient doesn't need explicit close
    }
}
