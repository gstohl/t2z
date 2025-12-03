/**
 * Interactive Send - Reads wallet from .env, prompts for recipients, sends transaction
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Recipient(val address: String, val amount: ULong, val memo: String = "")

fun main() {
    val env = loadEnv()
    val zebraRPC = "http://${env["ZEBRA_HOST"]}:${env["ZEBRA_PORT"]}"

    val privKey = env["PRIVATE_KEY"]!!.hexToBytes()
    val pubkey = Secp256k1.pubkeyCreate(privKey)
    val address = env["ADDRESS"]!!

    println("\n${"=".repeat(60)}")
    println("  t2z Mainnet Send")
    println("=".repeat(60))
    println("\nYour address: $address\n")

    print("Fetching balance... ")
    val utxos = getUTXOs(zebraRPC, address)
    val totalSats = utxos.sumOf { it.jsonObject["satoshis"]!!.jsonPrimitive.long }
    println("done")

    if (utxos.isEmpty()) {
        println("\nNo UTXOs found. Send ZEC to this address first.")
        return
    }

    val suffix = if (utxos.size == 1) "" else "s"
    println("\nBalance: ${totalSats.toDouble() / 1e8} ZEC (${utxos.size} UTXO$suffix)\n")

    println("Enter recipients (shielded addresses starting with 'u' recommended)")
    println("Press Enter with empty address to finish.\n")

    val recipients = mutableListOf<Recipient>()
    while (true) {
        print("Recipient ${recipients.size + 1} address: ")
        val addr = readLine()?.trim() ?: ""
        if (addr.isEmpty()) break

        print("Amount in ZEC: ")
        val amountZec = readLine()?.trim()?.toDoubleOrNull() ?: continue
        if (amountZec <= 0) {
            println("Invalid amount, skipping.\n")
            continue
        }

        val amountSats = (amountZec * 1e8).toULong()

        var memo = ""
        if (!addr.startsWith("t")) {
            print("Memo (optional, press Enter to skip): ")
            memo = readLine()?.trim() ?: ""
        }

        recipients.add(Recipient(addr, amountSats, memo))
        val memoInfo = if (memo.isNotEmpty()) " [memo: \"${memo.take(20)}\"]" else ""
        println("Added: ${"%.8f".format(amountZec)} ZEC → ${addr.take(30)}...$memoInfo\n")
    }

    if (recipients.isEmpty()) {
        println("\nNo recipients entered. Exiting.")
        return
    }

    val numTransparent = recipients.count { it.address.startsWith("t") }
    val numShielded = recipients.count { !it.address.startsWith("t") }
    val fee = calculateFee(utxos.size, numTransparent + 1, numShielded).toULong()

    val totalSend = recipients.sumOf { it.amount }
    val totalNeeded = totalSend + fee

    println("\n--- Transaction Summary ---")
    for (r in recipients) {
        val memoInfo = if (r.memo.isNotEmpty()) " [memo]" else ""
        println("  ${(r.amount.toDouble() / 1e8)} ZEC → ${r.address.take(40)}...$memoInfo")
    }
    println("  Fee: ${fee.toDouble() / 1e8} ZEC")
    println("  Total: ${totalNeeded.toDouble() / 1e8} ZEC")

    if (totalNeeded > totalSats.toULong()) {
        println("\nInsufficient balance! Need ${totalNeeded.toDouble() / 1e8} ZEC")
        return
    }

    // Build inputs
    val pkh = hash160(pubkey)
    val script = byteArrayOf(0x76, 0xa9.toByte(), 0x14) + pkh + byteArrayOf(0x88.toByte(), 0xac.toByte())

    val inputs = mutableListOf<TransparentInput>()
    var inputTotal = 0UL
    for (utxo in utxos) {
        val txidHex = utxo.jsonObject["txid"]!!.jsonPrimitive.content
        val txid = txidHex.hexToBytes().reversedArray()  // Reverse for internal format
        val vout = utxo.jsonObject["outputIndex"]!!.jsonPrimitive.int.toUInt()
        val amount = utxo.jsonObject["satoshis"]!!.jsonPrimitive.long.toULong()

        inputs.add(TransparentInput(pubkey, txid, vout, amount, script))
        inputTotal += amount
        if (inputTotal >= totalNeeded) break
    }

    // Build payments
    val payments = recipients.map { Payment(it.address, it.amount, it.memo) }

    val blockHeight = getBlockHeight(zebraRPC)

    println("\nBuilding transaction...")

    TransactionRequest(payments).use { request ->
        request.setTargetHeight(blockHeight + 10)

        print("  Proposing... ")
        val pczt = proposeTransaction(inputs, request)
        println("done")

        print("  Proving... ")
        val proved = proveTransaction(pczt)
        println("done")

        print("  Signing... ")
        var signed = proved
        for (i in inputs.indices) {
            val sighash = getSighash(signed, i)
            val sig = Secp256k1.sign(sighash, privKey).copyOf(64)
            signed = appendSignature(signed, i, sig)
        }
        println("done")

        print("  Finalizing... ")
        val txBytes = finalizeAndExtract(signed)
        println("done")

        print("  Broadcasting... ")
        val txid = broadcast(zebraRPC, txBytes.toHex())
        println("done")

        println("\nTransaction sent!")
        println("TXID: $txid")
        println("\nView: https://zcashblockexplorer.com/transactions/$txid")
    }
}

private fun getUTXOs(rpcURL: String, addr: String): JsonArray {
    val body = """{"jsonrpc":"2.0","method":"getaddressutxos","params":[{"addresses":["$addr"]}],"id":1}"""
    return Json.parseToJsonElement(post(rpcURL, body)).jsonObject["result"]!!.jsonArray
}

private fun getBlockHeight(rpcURL: String): Int {
    val body = """{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}"""
    return Json.parseToJsonElement(post(rpcURL, body)).jsonObject["result"]!!.jsonObject["blocks"]!!.jsonPrimitive.int
}

private fun broadcast(rpcURL: String, txHex: String): String {
    val body = """{"jsonrpc":"2.0","method":"sendrawtransaction","params":["$txHex"],"id":1}"""
    return Json.parseToJsonElement(post(rpcURL, body)).jsonObject["result"]!!.jsonPrimitive.content
}

private fun post(url: String, body: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.outputStream.write(body.toByteArray())
    return conn.inputStream.bufferedReader().readText()
}

private fun hash160(data: ByteArray): ByteArray {
    val sha = MessageDigest.getInstance("SHA-256").digest(data)
    val ripemd = org.bouncycastle.crypto.digests.RIPEMD160Digest()
    ripemd.update(sha, 0, sha.size)
    val out = ByteArray(20)
    ripemd.doFinal(out, 0)
    return out
}

private fun loadEnv(): Map<String, String> {
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

private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
