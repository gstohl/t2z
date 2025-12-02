/**
 * Testnet Demo: Mixed T→T+Z
 * Usage: ./gradlew run --args="<privateKeyHex>"
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val ZEBRA = "http://localhost:18232"
const val SHIELDED = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("t2z Testnet Demo - Mixed T→T+Z\n")
        println("Usage: ./gradlew run --args=\"<privateKeyHex>\"")
        println("\nThe demo will automatically fetch UTXOs from your Zebra node.")
        return
    }

    val privKey = args[0].hexToBytes()
    val pubkey = Secp256k1.pubkeyCreate(privKey)
    val address = pubkeyToTestnetAddress(pubkey)

    println("Address: $address")
    print("Fetching UTXOs... ")

    val utxos = getUTXOs(address)
    if (utxos.isEmpty()) {
        println("✗ No UTXOs. Get testnet ZEC from faucet.zecpages.com")
        return
    }
    println("✓ Found ${utxos.size} UTXO(s)\n")

    val utxo = utxos[0]
    val txid = utxo.jsonObject["txid"]!!.jsonPrimitive.content.hexToBytes()
    val vout = utxo.jsonObject["outputIndex"]!!.jsonPrimitive.int
    val amount = utxo.jsonObject["satoshis"]!!.jsonPrimitive.long.toULong()

    val pkh = hash160(pubkey)
    val script = byteArrayOf(0x76, 0xa9.toByte(), 0x14) + pkh + byteArrayOf(0x88.toByte(), 0xac.toByte())

    val input = TransparentInput(pubkey, txid, vout.toUInt(), amount, script)

    val fee = calculateFee(1, 2, 1).toULong()
    val available = amount - fee
    val tAmt = available * 40UL / 100UL
    val zAmt = available * 40UL / 100UL

    println("Input:       ${amount.toDouble() / 1e8} ZEC")
    println("Transparent: ${tAmt.toDouble() / 1e8} ZEC → ${address.take(20)}...")
    println("Shielded:    ${zAmt.toDouble() / 1e8} ZEC → ${SHIELDED.take(20)}...")
    println("Fee:         ${fee.toDouble() / 1e8} ZEC\n")

    TransactionRequest(listOf(Payment(address, tAmt), Payment(SHIELDED, zAmt))).use { request ->
        request.setUseMainnet(false)
        request.setTargetHeight(3_000_000)

        print("Proposing... ")
        val pczt = proposeTransaction(listOf(input), request)
        println("✓")

        print("Proving... ")
        val proved = proveTransaction(pczt)
        println("✓")

        print("Signing... ")
        val sighash = getSighash(proved, 0)
        val sig = Secp256k1.sign(sighash, privKey).copyOf(64)
        val signed = appendSignature(proved, 0, sig)
        println("✓")

        print("Finalizing... ")
        val txBytes = finalizeAndExtract(signed)
        println("✓")

        print("Broadcasting... ")
        val txidResult = broadcast(txBytes.toHex())
        println("✓\n\nTXID: $txidResult")
    }
}

fun getUTXOs(addr: String): JsonArray {
    val body = """{"jsonrpc":"2.0","method":"getaddressutxos","params":[{"addresses":["$addr"]}],"id":1}"""
    return Json.parseToJsonElement(post(body)).jsonObject["result"]!!.jsonArray
}

fun broadcast(txHex: String): String {
    val body = """{"jsonrpc":"2.0","method":"sendrawtransaction","params":["$txHex"],"id":1}"""
    return Json.parseToJsonElement(post(body)).jsonObject["result"]!!.jsonPrimitive.content
}

fun post(body: String): String {
    val conn = URL(ZEBRA).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.outputStream.write(body.toByteArray())
    return conn.inputStream.bufferedReader().readText()
}

fun hash160(data: ByteArray): ByteArray {
    val sha = MessageDigest.getInstance("SHA-256").digest(data)
    val ripemd = org.bouncycastle.crypto.digests.RIPEMD160Digest()
    ripemd.update(sha, 0, sha.size)
    val out = ByteArray(20)
    ripemd.doFinal(out, 0)
    return out
}

fun pubkeyToTestnetAddress(pubkey: ByteArray): String {
    val pkh = hash160(pubkey)
    val data = byteArrayOf(0x1d, 0x25) + pkh
    val check = MessageDigest.getInstance("SHA-256").digest(
        MessageDigest.getInstance("SHA-256").digest(data)
    ).take(4).toByteArray()
    return base58Encode(data + check)
}

fun base58Encode(data: ByteArray): String {
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    var num = java.math.BigInteger(1, data)
    val sb = StringBuilder()
    while (num > java.math.BigInteger.ZERO) {
        sb.insert(0, alphabet[num.mod(java.math.BigInteger.valueOf(58)).toInt()])
        num = num.divide(java.math.BigInteger.valueOf(58))
    }
    data.takeWhile { it == 0.toByte() }.forEach { sb.insert(0, '1') }
    return sb.toString()
}

fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
