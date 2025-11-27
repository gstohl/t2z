/**
 * JNA interface for the t2z native library
 *
 * This file provides low-level FFI bindings to the Rust t2z library.
 * Users should prefer the high-level API in T2z.kt instead of using these directly.
 */
package com.zcash.t2z

import com.sun.jna.*
import com.sun.jna.ptr.PointerByReference

/**
 * Result codes from FFI functions
 */
enum class ResultCode(val code: Int) {
    SUCCESS(0),
    ERROR_NULL_POINTER(1),
    ERROR_INVALID_UTF8(2),
    ERROR_BUFFER_TOO_SMALL(3),
    ERROR_PROPOSAL(10),
    ERROR_PROVER(11),
    ERROR_VERIFICATION(12),
    ERROR_SIGHASH(13),
    ERROR_SIGNATURE(14),
    ERROR_COMBINE(15),
    ERROR_FINALIZATION(16),
    ERROR_PARSE(17),
    ERROR_NOT_IMPLEMENTED(99);

    companion object {
        fun fromCode(code: Int): ResultCode =
            entries.find { it.code == code } ?: throw IllegalArgumentException("Unknown result code: $code")
    }
}

/**
 * C-compatible payment structure for JNA
 */
@Structure.FieldOrder("address", "amount", "memo", "label", "message")
open class CPayment : Structure() {
    @JvmField var address: String? = null
    @JvmField var amount: Long = 0
    @JvmField var memo: String? = null
    @JvmField var label: String? = null
    @JvmField var message: String? = null

    class ByReference : CPayment(), Structure.ByReference
    class ByValue : CPayment(), Structure.ByValue
}

/**
 * C-compatible transparent output structure for JNA
 */
@Structure.FieldOrder("script_pub_key", "script_pub_key_len", "value")
open class CTransparentOutput : Structure() {
    @JvmField var script_pub_key: Pointer? = null
    @JvmField var script_pub_key_len: NativeLong = NativeLong(0)
    @JvmField var value: Long = 0

    class ByReference : CTransparentOutput(), Structure.ByReference
    class ByValue : CTransparentOutput(), Structure.ByValue
}

/**
 * JNA interface to the t2z native library
 */
interface T2zLib : Library {
    companion object {
        val INSTANCE: T2zLib by lazy {
            val libName = when {
                Platform.isMac() -> "t2z"
                Platform.isLinux() -> "t2z"
                Platform.isWindows() -> "t2z"
                else -> throw UnsupportedOperationException("Unsupported platform")
            }
            Native.load(libName, T2zLib::class.java)
        }
    }

    // Error handling
    fun pczt_get_last_error(buffer: ByteArray, bufferLen: NativeLong): Int

    // Transaction request
    fun pczt_transaction_request_new(
        payments: Array<CPayment>?,
        numPayments: NativeLong,
        requestOut: PointerByReference
    ): Int

    fun pczt_transaction_request_free(request: Pointer?)

    fun pczt_transaction_request_set_target_height(request: Pointer, targetHeight: Int): Int

    fun pczt_transaction_request_set_use_mainnet(request: Pointer, useMainnet: Boolean): Int

    // PCZT operations
    fun pczt_propose_transaction(
        inputsBytes: ByteArray,
        inputsBytesLen: NativeLong,
        request: Pointer,
        changeAddress: String?,
        pcztOut: PointerByReference
    ): Int

    fun pczt_prove_transaction(pczt: Pointer, pcztOut: PointerByReference): Int

    fun pczt_verify_before_signing(
        pczt: Pointer,
        request: Pointer,
        expectedChange: Array<CTransparentOutput>?,
        expectedChangeLen: NativeLong
    ): Int

    fun pczt_get_sighash(pczt: Pointer, inputIndex: NativeLong, sighashOut: ByteArray): Int

    fun pczt_append_signature(
        pczt: Pointer,
        inputIndex: NativeLong,
        signature: ByteArray,
        pcztOut: PointerByReference
    ): Int

    fun pczt_combine(pczts: Array<Pointer>, numPczts: NativeLong, pcztOut: PointerByReference): Int

    fun pczt_finalize_and_extract(
        pczt: Pointer,
        txBytesOut: PointerByReference,
        txBytesLenOut: NativeLongByReference
    ): Int

    fun pczt_parse(pcztBytes: ByteArray, pcztBytesLen: NativeLong, pcztOut: PointerByReference): Int

    fun pczt_serialize(pczt: Pointer, bytesOut: PointerByReference, bytesLenOut: NativeLongByReference): Int

    fun pczt_free(pczt: Pointer?)

    fun pczt_free_bytes(bytes: Pointer?, len: NativeLong)
}

/**
 * JNA NativeLong by reference helper
 */
class NativeLongByReference : com.sun.jna.ptr.ByReference(NativeLong.SIZE) {
    fun getValue(): NativeLong = NativeLong(when (NativeLong.SIZE) {
        4 -> getPointer().getInt(0).toLong()
        8 -> getPointer().getLong(0)
        else -> throw IllegalStateException("Unexpected NativeLong.SIZE: ${NativeLong.SIZE}")
    })

    fun setValue(value: NativeLong) {
        when (NativeLong.SIZE) {
            4 -> getPointer().setInt(0, value.toInt())
            8 -> getPointer().setLong(0, value.toLong())
            else -> throw IllegalStateException("Unexpected NativeLong.SIZE: ${NativeLong.SIZE}")
        }
    }
}
