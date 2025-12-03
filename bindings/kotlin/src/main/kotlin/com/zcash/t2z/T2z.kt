/**
 * t2z Kotlin bindings
 *
 * This module provides Kotlin bindings to the t2z Rust library using JNA.
 * It enables transparent Zcash wallets (including hardware wallets) to send
 * shielded Orchard outputs using PCZT (Partially Constructed Zcash Transactions)
 * as defined in ZIP 374.
 *
 * @example
 * ```kotlin
 * // Create payment request
 * val request = TransactionRequest(listOf(
 *     Payment(address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", amount = 100_000UL)
 * ))
 *
 * // Create PCZT from transparent inputs
 * val pczt = proposeTransaction(listOf(input), request)
 *
 * // Add proofs and sign
 * val proved = proveTransaction(pczt)
 * val sighash = getSighash(proved, 0)
 * val signature = signMessage(privateKey, sighash)
 * val signed = appendSignature(proved, 0, signature)
 *
 * // Finalize
 * val txBytes = finalizeAndExtract(signed)
 * ```
 */
package com.zcash.t2z

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.Closeable
import java.lang.ref.Cleaner
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val lib = T2zLib.INSTANCE
private val cleaner = Cleaner.create()

/**
 * Exception thrown when a t2z operation fails
 */
class T2zException(message: String, val code: ResultCode) : Exception(message)

/**
 * Get the last error message from the native library
 */
private fun getLastError(): String {
    val buffer = ByteArray(512)
    lib.pczt_get_last_error(buffer, NativeLong(buffer.size.toLong()))
    val nullIndex = buffer.indexOf(0)
    return String(buffer, 0, if (nullIndex >= 0) nullIndex else buffer.size, Charsets.UTF_8)
}

/**
 * Check result code and throw exception on error
 */
private fun checkResult(code: Int, operation: String) {
    if (code != ResultCode.SUCCESS.code) {
        val errorMsg = getLastError()
        val resultCode = ResultCode.fromCode(code)
        throw T2zException("$operation failed: ${errorMsg.ifEmpty { "error code $code" }}", resultCode)
    }
}

/**
 * Payment request with address, amount, and optional metadata
 */
data class Payment(
    val address: String,
    val amount: ULong,
    val memo: String? = null,
    val label: String? = null,
    val message: String? = null
)

/**
 * Transparent UTXO input to spend
 */
data class TransparentInput(
    val pubkey: ByteArray,      // 33 bytes compressed
    val txid: ByteArray,        // 32 bytes
    val vout: Int,
    val amount: ULong,
    val scriptPubKey: ByteArray
) {
    init {
        require(pubkey.size == 33) { "Invalid pubkey length: expected 33, got ${pubkey.size}" }
        require(txid.size == 32) { "Invalid txid length: expected 32, got ${txid.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransparentInput) return false
        return pubkey.contentEquals(other.pubkey) &&
                txid.contentEquals(other.txid) &&
                vout == other.vout &&
                amount == other.amount &&
                scriptPubKey.contentEquals(other.scriptPubKey)
    }

    override fun hashCode(): Int {
        var result = pubkey.contentHashCode()
        result = 31 * result + txid.contentHashCode()
        result = 31 * result + vout
        result = 31 * result + amount.hashCode()
        result = 31 * result + scriptPubKey.contentHashCode()
        return result
    }
}

/**
 * Transparent transaction output (for change verification)
 */
data class TransparentOutput(
    val scriptPubKey: ByteArray,
    val value: ULong
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransparentOutput) return false
        return scriptPubKey.contentEquals(other.scriptPubKey) && value == other.value
    }

    override fun hashCode(): Int {
        var result = scriptPubKey.contentHashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

/**
 * Cleaner action for TransactionRequest handles
 */
private class RequestCleanerAction(private var handle: Pointer?) : Runnable {
    override fun run() {
        handle?.let { lib.pczt_transaction_request_free(it) }
        handle = null
    }
}

/**
 * Transaction request containing multiple payments
 */
class TransactionRequest(payments: List<Payment>) : Closeable {
    private var handle: Pointer?
    private var closed = false
    private val cleanable: Cleaner.Cleanable

    init {
        require(payments.isNotEmpty()) { "At least one payment is required" }

        // Build C payment array - must use Structure.toArray for contiguous memory
        val firstPayment = CPayment()
        @Suppress("UNCHECKED_CAST")
        val cPayments = firstPayment.toArray(payments.size) as Array<CPayment>

        payments.forEachIndexed { index, p ->
            cPayments[index].address = p.address
            cPayments[index].amount = p.amount.toLong()
            cPayments[index].memo = p.memo
            cPayments[index].label = p.label
            cPayments[index].message = p.message
        }

        val handleOut = PointerByReference()
        val code = lib.pczt_transaction_request_new(cPayments, NativeLong(cPayments.size.toLong()), handleOut)
        checkResult(code, "Create transaction request")

        handle = handleOut.value
        cleanable = cleaner.register(this, RequestCleanerAction(handle))
    }

    /**
     * Set the target block height for consensus branch ID selection
     */
    fun setTargetHeight(height: Int) {
        check(!closed) { "TransactionRequest already closed" }
        val code = lib.pczt_transaction_request_set_target_height(handle!!, height)
        checkResult(code, "Set target height")
    }

    /**
     * Set whether to use mainnet parameters for consensus branch ID
     *
     * By default, the library uses mainnet parameters. Set this to false for testnet.
     * Regtest networks (like Zebra's regtest) typically use mainnet-like branch IDs,
     * so keep the default (true) for regtest.
     */
    fun setUseMainnet(useMainnet: Boolean) {
        check(!closed) { "TransactionRequest already closed" }
        val code = lib.pczt_transaction_request_set_use_mainnet(handle!!, useMainnet)
        checkResult(code, "Set use mainnet")
    }

    override fun close() {
        if (!closed) {
            cleanable.clean()
            handle = null
            closed = true
        }
    }

    internal fun getHandle(): Pointer {
        check(!closed) { "TransactionRequest already closed" }
        return handle!!
    }
}

/**
 * Cleaner action for PCZT handles
 */
private class PcztCleanerAction(private var handle: Pointer?) : Runnable {
    override fun run() {
        handle?.let { lib.pczt_free(it) }
        handle = null
    }
}

/**
 * Partially Constructed Zcash Transaction (PCZT)
 */
class PCZT internal constructor(handle: Pointer) : Closeable {
    private var handle: Pointer? = handle
    private var closed = false
    private var cleanable: Cleaner.Cleanable? = cleaner.register(this, PcztCleanerAction(handle))

    override fun close() {
        if (!closed) {
            cleanable?.clean()
            handle = null
            closed = true
        }
    }

    internal fun getHandle(): Pointer {
        check(!closed) { "PCZT already closed" }
        return handle!!
    }

    /**
     * Take ownership of handle (transfers ownership, making this instance invalid)
     */
    internal fun takeHandle(): Pointer {
        check(!closed) { "PCZT already closed" }
        cleanable?.let {
            // Create a new cleanable that doesn't free, since ownership is transferred
            cleanable = null
        }
        val h = handle!!
        handle = null
        closed = true
        return h
    }
}

/**
 * Serialize transparent inputs to binary format expected by Rust
 */
private fun serializeTransparentInputs(inputs: List<TransparentInput>): ByteArray {
    // Calculate total size
    var totalSize = 2 // u16 for num_inputs
    for (input in inputs) {
        totalSize += 33 + 32 + 4 + 8 + 2 + input.scriptPubKey.size
    }

    val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

    // Number of inputs (u16 LE)
    buffer.putShort(inputs.size.toShort())

    for (input in inputs) {
        // Pubkey (33 bytes)
        buffer.put(input.pubkey)

        // TXID (32 bytes)
        buffer.put(input.txid)

        // Vout (u32 LE)
        buffer.putInt(input.vout)

        // Amount (u64 LE)
        buffer.putLong(input.amount.toLong())

        // Script length (u16 LE)
        buffer.putShort(input.scriptPubKey.size.toShort())

        // Script pubkey
        buffer.put(input.scriptPubKey)
    }

    return buffer.array()
}

/**
 * Create a PCZT from transparent inputs and transaction request
 */
fun proposeTransaction(inputs: List<TransparentInput>, request: TransactionRequest): PCZT {
    val inputBytes = serializeTransparentInputs(inputs)
    val handleOut = PointerByReference()

    val code = lib.pczt_propose_transaction(
        inputBytes,
        NativeLong(inputBytes.size.toLong()),
        request.getHandle(),
        null,
        handleOut
    )
    checkResult(code, "Propose transaction")

    return PCZT(handleOut.value)
}

/**
 * Create a PCZT with explicit change handling
 */
fun proposeTransactionWithChange(
    inputs: List<TransparentInput>,
    request: TransactionRequest,
    changeAddress: String
): PCZT {
    val inputBytes = serializeTransparentInputs(inputs)
    val handleOut = PointerByReference()

    val code = lib.pczt_propose_transaction(
        inputBytes,
        NativeLong(inputBytes.size.toLong()),
        request.getHandle(),
        changeAddress,
        handleOut
    )
    checkResult(code, "Propose transaction with change")

    return PCZT(handleOut.value)
}

/**
 * Add Orchard proofs to the PCZT.
 *
 * **IMPORTANT:** This function ALWAYS consumes the input PCZT, even on error.
 * On error, the input PCZT is invalidated and cannot be reused.
 * If you need to retry on failure, call [serializePczt] before this function
 * to create a backup that can be restored with [parsePczt].
 */
fun proveTransaction(pczt: PCZT): PCZT {
    val handleOut = PointerByReference()
    val code = lib.pczt_prove_transaction(pczt.takeHandle(), handleOut)
    checkResult(code, "Prove transaction")
    return PCZT(handleOut.value)
}

/**
 * Verify the PCZT before signing
 */
fun verifyBeforeSigning(
    pczt: PCZT,
    request: TransactionRequest,
    expectedChange: List<TransparentOutput>
) {
    val cOutputs = if (expectedChange.isNotEmpty()) {
        expectedChange.map { o ->
            val mem = Memory(o.scriptPubKey.size.toLong())
            mem.write(0, o.scriptPubKey, 0, o.scriptPubKey.size)

            CTransparentOutput().apply {
                script_pub_key = mem
                script_pub_key_len = NativeLong(o.scriptPubKey.size.toLong())
                value = o.value.toLong()
            }
        }.toTypedArray()
    } else {
        null
    }

    val code = lib.pczt_verify_before_signing(
        pczt.getHandle(),
        request.getHandle(),
        cOutputs,
        NativeLong(expectedChange.size.toLong())
    )
    checkResult(code, "Verify before signing")
}

/**
 * Get signature hash for a transparent input
 */
fun getSighash(pczt: PCZT, index: Int): ByteArray {
    val sighash = ByteArray(32)
    val code = lib.pczt_get_sighash(pczt.getHandle(), NativeLong(index.toLong()), sighash)
    checkResult(code, "Get sighash")
    return sighash
}

/**
 * Append an external signature to the PCZT.
 *
 * **IMPORTANT:** This function ALWAYS consumes the input PCZT, even on error.
 * On error, the input PCZT is invalidated and cannot be reused.
 * If you need to retry on failure, call [serializePczt] before this function
 * to create a backup that can be restored with [parsePczt].
 */
fun appendSignature(pczt: PCZT, index: Int, signature: ByteArray): PCZT {
    require(signature.size == 64) { "Invalid signature length: expected 64, got ${signature.size}" }

    val handleOut = PointerByReference()
    val code = lib.pczt_append_signature(pczt.takeHandle(), NativeLong(index.toLong()), signature, handleOut)
    checkResult(code, "Append signature")
    return PCZT(handleOut.value)
}

/**
 * Combine multiple PCZTs into one.
 *
 * **IMPORTANT:** This function ALWAYS consumes ALL input PCZTs, even on error.
 * On error, all input PCZTs are invalidated and cannot be reused.
 * If you need to retry on failure, call [serialize] on each PCZT before
 * this function to create backups that can be restored with [parse].
 */
fun combine(pczts: List<PCZT>): PCZT {
    require(pczts.isNotEmpty()) { "At least one PCZT is required" }

    val handles = pczts.map { it.takeHandle() }.toTypedArray()
    val handleOut = PointerByReference()

    val code = lib.pczt_combine(handles, NativeLong(handles.size.toLong()), handleOut)
    checkResult(code, "Combine PCZTs")

    return PCZT(handleOut.value)
}

/**
 * Finalize the PCZT and extract transaction bytes.
 *
 * **IMPORTANT:** This function ALWAYS consumes the input PCZT, even on error.
 * On error, the input PCZT is invalidated and cannot be reused.
 * If you need to retry on failure, call [serializePczt] before this function
 * to create a backup that can be restored with [parsePczt].
 */
fun finalizeAndExtract(pczt: PCZT): ByteArray {
    val bytesOut = PointerByReference()
    val lenOut = NativeLongByReference()

    val code = lib.pczt_finalize_and_extract(pczt.takeHandle(), bytesOut, lenOut)
    checkResult(code, "Finalize and extract")

    val len = lenOut.getValue().toInt()
    val ptr = bytesOut.value
    val result = ptr.getByteArray(0, len)
    lib.pczt_free_bytes(ptr, NativeLong(len.toLong()))

    return result
}

/**
 * Serialize PCZT to bytes
 */
fun serializePczt(pczt: PCZT): ByteArray {
    val bytesOut = PointerByReference()
    val lenOut = NativeLongByReference()

    val code = lib.pczt_serialize(pczt.getHandle(), bytesOut, lenOut)
    checkResult(code, "Serialize PCZT")

    val len = lenOut.getValue().toInt()
    val ptr = bytesOut.value
    val result = ptr.getByteArray(0, len)
    lib.pczt_free_bytes(ptr, NativeLong(len.toLong()))

    return result
}

/**
 * Parse PCZT from bytes
 */
fun parsePczt(bytes: ByteArray): PCZT {
    val handleOut = PointerByReference()
    val code = lib.pczt_parse(bytes, NativeLong(bytes.size.toLong()), handleOut)
    checkResult(code, "Parse PCZT")
    return PCZT(handleOut.value)
}

/**
 * Calculate the ZIP-317 transaction fee.
 *
 * This is a pure function that computes the fee based on transaction shape.
 * Use this to calculate fees before building a transaction, e.g., for "send max"
 * functionality where you need to know the fee to calculate the maximum sendable amount.
 *
 * @param numTransparentInputs Number of transparent UTXOs to spend
 * @param numTransparentOutputs Number of transparent outputs (including change if any)
 * @param numOrchardOutputs Number of Orchard (shielded) outputs
 * @return The fee in zatoshis
 *
 * @sample
 * ```kotlin
 * // Transparent-only: 1 input, 2 outputs (1 payment + 1 change)
 * val fee = calculateFee(1, 2, 0) // Returns 10000
 *
 * // Shielded: 1 input, 1 change, 1 orchard output
 * val fee = calculateFee(1, 1, 1) // Returns 15000
 *
 * // Calculate max sendable amount
 * val totalInput = 100_000_000UL // 1 ZEC in zatoshis
 * val fee = calculateFee(1, 2, 0).toULong()
 * val maxSend = totalInput - fee // 99_990_000 zatoshis
 * ```
 *
 * @see <a href="https://zips.z.cash/zip-0317">ZIP-317</a>
 */
fun calculateFee(
    numTransparentInputs: Int,
    numTransparentOutputs: Int,
    numOrchardOutputs: Int
): Long {
    return lib.pczt_calculate_fee(
        NativeLong(numTransparentInputs.toLong()),
        NativeLong(numTransparentOutputs.toLong()),
        NativeLong(numOrchardOutputs.toLong())
    )
}
