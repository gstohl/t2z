/**
 * Utility functions for t2z examples
 */
package com.zcash.t2z.examples

import com.zcash.t2z.TransparentInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import java.io.File

// Spent UTXO tracking file
private val DATA_DIR = File(System.getProperty("user.dir"), "data")
private val SPENT_UTXOS_FILE = File(DATA_DIR, "spent-utxos.json")

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

/**
 * Load spent UTXOs from file
 */
fun loadSpentUtxos(): MutableSet<String> {
    return try {
        val content = SPENT_UTXOS_FILE.readText()
        json.decodeFromString<List<String>>(content).toMutableSet()
    } catch (e: Exception) {
        mutableSetOf()
    }
}

/**
 * Save spent UTXOs to file
 */
fun saveSpentUtxos(spent: Set<String>) {
    DATA_DIR.mkdirs()
    SPENT_UTXOS_FILE.writeText(json.encodeToString(ListSerializer(String.serializer()), spent.toList()))
}

/**
 * Mark UTXOs as spent (call after successful broadcast)
 */
fun markUtxosSpent(inputs: List<TransparentInput>) {
    val spent = loadSpentUtxos()
    for (input in inputs) {
        val key = "${bytesToHex(input.txid)}:${input.vout}"
        spent.add(key)
    }
    saveSpentUtxos(spent)
}

/**
 * Clear spent UTXOs tracking (call on setup)
 */
fun clearSpentUtxos() {
    saveSpentUtxos(emptySet())
}

/**
 * Convert zatoshis to ZEC string
 */
fun zatoshiToZec(zatoshi: ULong): String {
    return "%.8f".format(zatoshi.toDouble() / 100_000_000.0)
}

/**
 * Convert ZEC to zatoshis
 */
fun zecToZatoshi(zec: Double): ULong {
    return (zec * 100_000_000).toULong()
}

/**
 * Reverse a hex string (for txid endianness)
 */
fun reverseHex(hex: String): String {
    return hexToBytes(hex).reversedArray().let { bytesToHex(it) }
}

/**
 * Parse Zcash transaction hex to extract outputs
 */
private fun parseTxOutputs(txHex: String): List<Pair<ULong, ByteArray>> {
    val tx = hexToBytes(txHex)
    var offset = 0

    // Skip header (4 bytes version + 4 bytes version group id)
    offset += 8

    // Read vin count (varint - simplified, assuming single byte)
    val vinCount = tx[offset].toInt() and 0xFF
    offset += 1

    // Skip all inputs
    repeat(vinCount) {
        offset += 32 // prev txid
        offset += 4  // prev vout
        val scriptLen = tx[offset].toInt() and 0xFF
        offset += 1 + scriptLen // script length + script
        offset += 4 // sequence
    }

    // Read vout count
    val voutCount = tx[offset].toInt() and 0xFF
    offset += 1

    val outputs = mutableListOf<Pair<ULong, ByteArray>>()

    repeat(voutCount) {
        // Read value (8 bytes, little-endian)
        var value = 0UL
        for (i in 0 until 8) {
            value = value or ((tx[offset + i].toULong() and 0xFFUL) shl (i * 8))
        }
        offset += 8

        // Read script length and script
        val scriptLen = tx[offset].toInt() and 0xFF
        offset += 1
        val scriptPubKey = tx.copyOfRange(offset, offset + scriptLen)
        offset += scriptLen

        outputs.add(value to scriptPubKey)
    }

    return outputs
}

/**
 * Compute txid from raw transaction hex
 */
private fun computeTxid(txHex: String): String {
    val tx = hexToBytes(txHex)
    val hash = doubleSha256(tx)
    return bytesToHex(hash.reversedArray())
}

/**
 * Get coinbase UTXO from a block
 */
suspend fun getCoinbaseUtxo(
    client: ZebraClient,
    blockHeight: Int,
    keypair: ZcashKeypair
): TransparentInput? {
    return try {
        val blockHash = client.getBlockHash(blockHeight)
        val block = client.getBlock(blockHash, 2) // verbosity 2 for tx data

        val txArray = block["tx"]?.jsonArray ?: return null
        if (txArray.isEmpty()) return null

        val coinbaseTx = txArray[0].jsonObject

        // Check for Zebra format (has hex field)
        val txHex = coinbaseTx["hex"]?.jsonPrimitive?.contentOrNull
        if (txHex != null) {
            val outputs = parseTxOutputs(txHex)
            val expectedPubkeyHash = hash160(keypair.publicKey)

            for ((index, output) in outputs.withIndex()) {
                val (value, scriptPubKey) = output
                // Check if this is a P2PKH output matching our pubkey
                // P2PKH: OP_DUP OP_HASH160 <20 bytes> OP_EQUALVERIFY OP_CHECKSIG
                if (scriptPubKey.size == 25 &&
                    scriptPubKey[0] == 0x76.toByte() &&
                    scriptPubKey[1] == 0xa9.toByte() &&
                    scriptPubKey[2] == 0x14.toByte() &&
                    scriptPubKey[23] == 0x88.toByte() &&
                    scriptPubKey[24] == 0xac.toByte()
                ) {
                    val pubkeyHashInScript = scriptPubKey.copyOfRange(3, 23)
                    if (pubkeyHashInScript.contentEquals(expectedPubkeyHash)) {
                        val txid = hexToBytes(reverseHex(computeTxid(txHex)))
                        return TransparentInput(
                            pubkey = keypair.publicKey,
                            txid = txid,
                            vout = index,
                            amount = value,
                            scriptPubKey = scriptPubKey
                        )
                    }
                }
            }
        }

        null
    } catch (e: Exception) {
        println("Error getting coinbase UTXO at height $blockHeight: ${e.message}")
        null
    }
}

/**
 * Get mature coinbase UTXOs (100+ confirmations)
 */
suspend fun getMatureCoinbaseUtxos(
    client: ZebraClient,
    keypair: ZcashKeypair,
    maxCount: Int = 10,
    startFromRecent: Boolean = true
): List<TransparentInput> {
    val info = client.getBlockchainInfo()
    val currentHeight = info.blocks
    val matureHeight = currentHeight - 100

    val spentUtxos = loadSpentUtxos()
    val utxos = mutableListOf<TransparentInput>()

    if (startFromRecent) {
        // Scan from most recent mature blocks backwards
        for (height in matureHeight downTo 1) {
            if (utxos.size >= maxCount) break
            val utxo = getCoinbaseUtxo(client, height, keypair)
            if (utxo != null) {
                val key = "${bytesToHex(utxo.txid)}:${utxo.vout}"
                if (key !in spentUtxos) {
                    utxos.add(utxo)
                }
            }
        }
    } else {
        // Start from block 1
        for (height in 1..matureHeight) {
            if (utxos.size >= maxCount) break
            val utxo = getCoinbaseUtxo(client, height, keypair)
            if (utxo != null) {
                val key = "${bytesToHex(utxo.txid)}:${utxo.vout}"
                if (key !in spentUtxos) {
                    utxos.add(utxo)
                }
            }
        }
    }

    return utxos
}

/**
 * Print workflow summary
 */
fun printWorkflowSummary(
    title: String,
    inputs: List<TransparentInput>,
    outputs: List<Pair<String, ULong>>,
    fee: ULong
) {
    println()
    println("=".repeat(70))
    println(title)
    println("=".repeat(70))

    val totalInput = inputs.fold(0UL) { sum, input -> sum + input.amount }
    val totalOutput = outputs.fold(0UL) { sum, output -> sum + output.second }

    println("\nInputs: ${inputs.size}")
    inputs.forEachIndexed { i, input ->
        println("  [$i] ${zatoshiToZec(input.amount)} ZEC")
    }
    println("  Total: ${zatoshiToZec(totalInput)} ZEC")

    println("\nOutputs: ${outputs.size}")
    outputs.forEachIndexed { i, (address, amount) ->
        println("  [$i] ${address.take(20)}... -> ${zatoshiToZec(amount)} ZEC")
    }
    println("  Total: ${zatoshiToZec(totalOutput)} ZEC")

    println("\nFee: ${zatoshiToZec(fee)} ZEC")
    println("=".repeat(70))
    println()
}

/**
 * Print broadcast result
 */
fun printBroadcastResult(txid: String, txHex: String? = null) {
    println()
    println("=".repeat(70))
    println("TRANSACTION BROADCAST SUCCESSFUL")
    println("=".repeat(70))
    println("\nTXID: $txid")
    txHex?.let {
        println("\nRaw Transaction (${it.length / 2} bytes):")
        println(it.take(100) + "...")
    }
    println()
    println("=".repeat(70))
    println()
}

/**
 * Print error
 */
fun printError(title: String, error: Exception) {
    println()
    println("=".repeat(70))
    println("ERROR: $title")
    println("=".repeat(70))
    println("\nError: ${error.message}")
    error.printStackTrace()
    println()
    println("=".repeat(70))
    println()
}

@Serializable
data class TestData(
    val transparent: TransparentData,
    val network: String? = null,
    val setupHeight: Int? = null,
    val setupAt: String? = null
)

@Serializable
data class TransparentData(
    val address: String,
    val publicKey: String,
    val privateKey: String,
    val wif: String
)

/**
 * Load test data from file
 */
fun loadTestData(): TestData {
    val file = File(DATA_DIR, "test-addresses.json")
    return json.decodeFromString<TestData>(file.readText())
}

/**
 * Save test data to file
 */
fun saveTestData(testData: TestData) {
    DATA_DIR.mkdirs()
    val file = File(DATA_DIR, "test-addresses.json")
    file.writeText(json.encodeToString(TestData.serializer(), testData))
}
