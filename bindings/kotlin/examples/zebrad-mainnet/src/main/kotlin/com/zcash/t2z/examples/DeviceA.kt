/**
 * Device A - Online Device (Hardware Wallet Simulation)
 * Builds transaction, outputs sighash, waits for signature, broadcasts
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

fun main() {
    val env = loadEnvA()
    val zebraRPC = "http://${env["ZEBRA_HOST"]}:${env["ZEBRA_PORT"]}"

    val pubkey = env["PUBLIC_KEY"]!!.hexToBytesA()
    val address = env["ADDRESS"]!!

    println("\n${"=".repeat(60)}")
    println("  DEVICE A - ONLINE DEVICE (Hardware Wallet Simulation)")
    println("=".repeat(60))
    println("\nThis device builds transactions but NEVER sees the private key!")
    println("\nYour address: $address\n")

    print("Fetching balance... ")
    val utxos = getUTXOsA(zebraRPC, address)
    val totalSats = utxos.sumOf { it.jsonObject["satoshis"]!!.jsonPrimitive.long }
    println("done")

    if (utxos.isEmpty()) {
        println("\nNo UTXOs found. Send ZEC to this address first.")
        return
    }

    println("\nBalance: ${totalSats.toDouble() / 1e8} ZEC\n")

    print("Recipient address: ")
    val recipientAddr = readLine()?.trim() ?: ""
    if (recipientAddr.isEmpty()) {
        println("No address entered. Exiting.")
        return
    }

    print("Amount in ZEC: ")
    val amountZec = readLine()?.trim()?.toDoubleOrNull()
    if (amountZec == null || amountZec <= 0) {
        println("Invalid amount. Exiting.")
        return
    }
    val amountSats = (amountZec * 1e8).toULong()

    var memo = ""
    if (!recipientAddr.startsWith("t")) {
        print("Memo (optional, press Enter to skip): ")
        memo = readLine()?.trim() ?: ""
    }

    val isShielded = !recipientAddr.startsWith("t")
    val fee = if (isShielded) calculateFee(1, 1, 1) else calculateFee(1, 2, 0)
    val totalNeeded = amountSats + fee.toULong()

    if (totalNeeded > totalSats.toULong()) {
        println("\nInsufficient balance! Need ${totalNeeded.toDouble() / 1e8} ZEC")
        return
    }

    println("\n--- Transaction Summary ---")
    println("  To: $recipientAddr")
    println("  Amount: ${"%.8f".format(amountZec)} ZEC")
    if (memo.isNotEmpty()) println("  Memo: \"$memo\"")
    println("  Fee: ${fee.toDouble() / 1e8} ZEC")

    // Build input
    val pkh = hash160A(pubkey)
    val script = byteArrayOf(0x76, 0xa9.toByte(), 0x14) + pkh + byteArrayOf(0x88.toByte(), 0xac.toByte())

    val utxo = utxos[0].jsonObject
    val txid = utxo["txid"]!!.jsonPrimitive.content.hexToBytesA().reversedArray()
    val vout = utxo["outputIndex"]!!.jsonPrimitive.int.toUInt()
    val amount = utxo["satoshis"]!!.jsonPrimitive.long.toULong()

    val input = TransparentInput(pubkey, txid, vout, amount, script)
    val payment = Payment(recipientAddr, amountSats, memo)

    val blockHeight = getBlockHeightA(zebraRPC)

    println("\nBuilding transaction...")

    TransactionRequest(listOf(payment)).use { request ->
        request.setTargetHeight(blockHeight + 10)

        print("  Proposing... ")
        val pczt = proposeTransaction(listOf(input), request)
        println("done")

        print("  Proving... ")
        val proved = proveTransaction(pczt)
        println("done")

        val sighash = getSighash(proved, 0)
        val sighashHex = sighash.toHexA()

        // Serialize PCZT
        val psztBytes = serializePczt(proved)
        val tempFile = File(System.getProperty("user.dir"), ".pczt-temp")
        tempFile.writeText(psztBytes.toHexA())

        println("\n${"=".repeat(60)}")
        println("  SIGHASH READY FOR OFFLINE SIGNING")
        println("=".repeat(60))
        println("\nCopy this sighash to Device B:\n")
        println("SIGHASH: $sighashHex")
        println("\n${"=".repeat(60)}")

        println("\nRun Device B with the sighash, then paste the signature here.\n")
        print("Paste signature from Device B: ")
        val sigHex = readLine()?.trim() ?: ""

        if (sigHex.length != 128) {
            println("\nInvalid signature (expected 64 bytes / 128 hex chars). Exiting.")
            return
        }

        val sig = sigHex.hexToBytesA()

        println("\nFinalizing transaction...")
        val loadedPczt = parsePczt(tempFile.readText().hexToBytesA())
        val signed = appendSignature(loadedPczt, 0, sig)

        print("  Extracting... ")
        val txBytes = finalizeAndExtract(signed)
        println("done")

        print("  Broadcasting... ")
        val txidResult = broadcastA(zebraRPC, txBytes.toHexA())
        println("done")

        tempFile.delete()

        println("\n${"=".repeat(60)}")
        println("  TRANSACTION BROADCAST SUCCESSFUL!")
        println("=".repeat(60))
        println("\nTXID: $txidResult")
        println("\nThe private key NEVER touched this device!")
    }
}

private fun getUTXOsA(rpcURL: String, addr: String): JsonArray {
    val body = """{"jsonrpc":"2.0","method":"getaddressutxos","params":[{"addresses":["$addr"]}],"id":1}"""
    return Json.parseToJsonElement(postA(rpcURL, body)).jsonObject["result"]!!.jsonArray
}

private fun getBlockHeightA(rpcURL: String): Int {
    val body = """{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}"""
    return Json.parseToJsonElement(postA(rpcURL, body)).jsonObject["result"]!!.jsonObject["blocks"]!!.jsonPrimitive.int
}

private fun broadcastA(rpcURL: String, txHex: String): String {
    val body = """{"jsonrpc":"2.0","method":"sendrawtransaction","params":["$txHex"],"id":1}"""
    return Json.parseToJsonElement(postA(rpcURL, body)).jsonObject["result"]!!.jsonPrimitive.content
}

private fun postA(url: String, body: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.outputStream.write(body.toByteArray())
    return conn.inputStream.bufferedReader().readText()
}

private fun hash160A(data: ByteArray): ByteArray {
    val sha = MessageDigest.getInstance("SHA-256").digest(data)
    val ripemd = org.bouncycastle.crypto.digests.RIPEMD160Digest()
    ripemd.update(sha, 0, sha.size)
    val out = ByteArray(20)
    ripemd.doFinal(out, 0)
    return out
}

private fun loadEnvA(): Map<String, String> {
    val envFile = File(System.getProperty("user.dir"), ".env")
    if (!envFile.exists()) {
        println("No .env file found. Run GenerateWallet first.")
        System.exit(1)
    }
    val env = mutableMapOf("ZEBRA_HOST" to "localhost", "ZEBRA_PORT" to "8232")
    envFile.readLines().filter { !it.startsWith("#") && it.contains("=") }.forEach { line ->
        val (key, value) = line.split("=", limit = 2)
        env[key.trim()] = value.trim().trim('"', '\'')
    }
    return env
}

private fun String.hexToBytesA() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun ByteArray.toHexA() = joinToString("") { "%02x".format(it) }
