/**
 * t2z Java bindings.
 *
 * <p>This module provides Java bindings to the t2z Rust library using JNA.
 * It enables transparent Zcash wallets (including hardware wallets) to send
 * shielded Orchard outputs using PCZT (Partially Constructed Zcash Transactions)
 * as defined in ZIP 374.</p>
 *
 * <h2>Example usage:</h2>
 * <pre>{@code
 * // Create payment request
 * List<Payment> payments = List.of(new Payment("tm9iML...", 100000L));
 * try (TransactionRequest request = new TransactionRequest(payments)) {
 *     // Create PCZT from transparent inputs
 *     PCZT pczt = T2z.proposeTransaction(inputs, request);
 *
 *     // Add proofs and sign
 *     PCZT proved = T2z.proveTransaction(pczt);
 *     byte[] sighash = T2z.getSighash(proved, 0);
 *     byte[] signature = Signing.signMessage(privateKey, sighash);
 *     PCZT signed = T2z.appendSignature(proved, 0, signature);
 *
 *     // Finalize
 *     byte[] txBytes = T2z.finalizeAndExtract(signed);
 * }
 * }</pre>
 */
package com.zcash.t2z;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Main API class for t2z operations.
 */
public final class T2z {
    private static final T2zLib lib = T2zLib.INSTANCE;

    private T2z() {
        // Static utility class
    }

    /**
     * Get the last error message from the native library.
     */
    static String getLastError() {
        byte[] buffer = new byte[512];
        lib.pczt_get_last_error(buffer, new NativeLong(buffer.length));
        int nullIndex = 0;
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] == 0) {
                nullIndex = i;
                break;
            }
        }
        return new String(buffer, 0, nullIndex);
    }

    /**
     * Check result code and throw exception on error.
     */
    static void checkResult(int code, String operation) {
        if (code != ResultCode.SUCCESS.getCode()) {
            String errorMsg = getLastError();
            ResultCode resultCode = ResultCode.fromCode(code);
            throw new T2zException(
                operation + " failed: " + (errorMsg.isEmpty() ? "error code " + code : errorMsg),
                resultCode
            );
        }
    }

    /**
     * Serialize transparent inputs to binary format expected by Rust.
     */
    static byte[] serializeTransparentInputs(List<TransparentInput> inputs) {
        // Calculate total size
        int totalSize = 2; // u16 for num_inputs
        for (TransparentInput input : inputs) {
            totalSize += 33 + 32 + 4 + 8 + 2 + input.getScriptPubKey().length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        // Number of inputs (u16 LE)
        buffer.putShort((short) inputs.size());

        for (TransparentInput input : inputs) {
            // Pubkey (33 bytes)
            buffer.put(input.getPubkey());

            // TXID (32 bytes)
            buffer.put(input.getTxid());

            // Vout (u32 LE)
            buffer.putInt(input.getVout());

            // Amount (u64 LE)
            buffer.putLong(input.getAmount());

            // Script length (u16 LE)
            buffer.putShort((short) input.getScriptPubKey().length);

            // Script pubkey
            buffer.put(input.getScriptPubKey());
        }

        return buffer.array();
    }

    /**
     * Create a PCZT from transparent inputs and transaction request.
     *
     * @param inputs  List of transparent UTXOs to spend
     * @param request Transaction request with payment recipients
     * @return The created PCZT
     */
    public static PCZT proposeTransaction(List<TransparentInput> inputs, TransactionRequest request) {
        return proposeTransactionWithChange(inputs, request, null);
    }

    /**
     * Create a PCZT with explicit change handling.
     *
     * @param inputs        List of transparent UTXOs to spend
     * @param request       Transaction request with payment recipients
     * @param changeAddress Optional change address
     * @return The created PCZT
     */
    public static PCZT proposeTransactionWithChange(
            List<TransparentInput> inputs,
            TransactionRequest request,
            String changeAddress
    ) {
        byte[] inputBytes = serializeTransparentInputs(inputs);
        PointerByReference handleOut = new PointerByReference();

        int code = lib.pczt_propose_transaction(
            inputBytes,
            new NativeLong(inputBytes.length),
            request.getHandle(),
            changeAddress,
            handleOut
        );
        checkResult(code, "Propose transaction");

        return new PCZT(handleOut.getValue());
    }

    /**
     * Add Orchard proofs to the PCZT.
     *
     * <p><b>IMPORTANT:</b> This function ALWAYS consumes the input PCZT, even on error.
     * On error, the input PCZT is invalidated and cannot be reused.
     * If you need to retry on failure, call {@link #serialize(PCZT)} before this function
     * to create a backup that can be restored with {@link #parse(byte[])}.</p>
     *
     * @param pczt The PCZT to prove (consumed)
     * @return A new PCZT with proofs added
     */
    public static PCZT proveTransaction(PCZT pczt) {
        PointerByReference handleOut = new PointerByReference();
        int code = lib.pczt_prove_transaction(pczt.takeHandle(), handleOut);
        checkResult(code, "Prove transaction");
        return new PCZT(handleOut.getValue());
    }

    /**
     * Verify the PCZT before signing.
     *
     * @param pczt           The PCZT to verify (not consumed)
     * @param request        The original transaction request
     * @param expectedChange Expected change outputs
     */
    public static void verifyBeforeSigning(
            PCZT pczt,
            TransactionRequest request,
            List<TransparentOutput> expectedChange
    ) {
        T2zLib.CTransparentOutput.ByReference cOutputs = null;

        if (expectedChange != null && !expectedChange.isEmpty()) {
            T2zLib.CTransparentOutput.ByReference firstOutput = new T2zLib.CTransparentOutput.ByReference();
            T2zLib.CTransparentOutput[] outputArray =
                (T2zLib.CTransparentOutput[]) firstOutput.toArray(expectedChange.size());

            for (int i = 0; i < expectedChange.size(); i++) {
                TransparentOutput o = expectedChange.get(i);
                Memory mem = new Memory(o.getScriptPubKey().length);
                mem.write(0, o.getScriptPubKey(), 0, o.getScriptPubKey().length);
                outputArray[i].script_pub_key = mem;
                outputArray[i].script_pub_key_len = new NativeLong(o.getScriptPubKey().length);
                outputArray[i].value = o.getValue();
            }
            cOutputs = firstOutput;
        }

        int code = lib.pczt_verify_before_signing(
            pczt.getHandle(),
            request.getHandle(),
            cOutputs,
            new NativeLong(expectedChange != null ? expectedChange.size() : 0)
        );
        checkResult(code, "Verify before signing");
    }

    /**
     * Get signature hash for a transparent input.
     *
     * @param pczt  The PCZT (not consumed)
     * @param index The input index
     * @return 32-byte signature hash
     */
    public static byte[] getSighash(PCZT pczt, int index) {
        byte[] sighash = new byte[32];
        int code = lib.pczt_get_sighash(pczt.getHandle(), new NativeLong(index), sighash);
        checkResult(code, "Get sighash");
        return sighash;
    }

    /**
     * Append an external signature to the PCZT.
     *
     * <p><b>IMPORTANT:</b> This function ALWAYS consumes the input PCZT, even on error.
     * On error, the input PCZT is invalidated and cannot be reused.
     * If you need to retry on failure, call {@link #serialize(PCZT)} before this function
     * to create a backup that can be restored with {@link #parse(byte[])}.</p>
     *
     * @param pczt      The PCZT (consumed)
     * @param index     The input index
     * @param signature 64-byte signature (r || s)
     * @return A new PCZT with signature added
     */
    public static PCZT appendSignature(PCZT pczt, int index, byte[] signature) {
        if (signature.length != 64) {
            throw new IllegalArgumentException("Invalid signature length: expected 64, got " + signature.length);
        }

        PointerByReference handleOut = new PointerByReference();
        int code = lib.pczt_append_signature(pczt.takeHandle(), new NativeLong(index), signature, handleOut);
        checkResult(code, "Append signature");
        return new PCZT(handleOut.getValue());
    }

    /**
     * Combine multiple PCZTs into one.
     *
     * <p><b>IMPORTANT:</b> This function ALWAYS consumes ALL input PCZTs, even on error.
     * On error, all input PCZTs are invalidated and cannot be reused.
     * If you need to retry on failure, call {@link #serialize(PCZT)} on each PCZT before
     * this function to create backups that can be restored with {@link #parse(byte[])}.</p>
     *
     * @param pczts List of PCZTs to combine (all consumed)
     * @return Combined PCZT
     */
    public static PCZT combine(List<PCZT> pczts) {
        if (pczts == null || pczts.isEmpty()) {
            throw new IllegalArgumentException("At least one PCZT is required");
        }

        Pointer[] handles = new Pointer[pczts.size()];
        for (int i = 0; i < pczts.size(); i++) {
            handles[i] = pczts.get(i).takeHandle();
        }

        PointerByReference handleOut = new PointerByReference();
        int code = lib.pczt_combine(handles, new NativeLong(handles.length), handleOut);
        checkResult(code, "Combine PCZTs");

        return new PCZT(handleOut.getValue());
    }

    /**
     * Finalize the PCZT and extract transaction bytes.
     *
     * <p><b>IMPORTANT:</b> This function ALWAYS consumes the input PCZT, even on error.
     * On error, the input PCZT is invalidated and cannot be reused.
     * If you need to retry on failure, call {@link #serialize(PCZT)} before this function
     * to create a backup that can be restored with {@link #parse(byte[])}.</p>
     *
     * @param pczt The PCZT (consumed)
     * @return Transaction bytes ready for broadcast
     */
    public static byte[] finalizeAndExtract(PCZT pczt) {
        PointerByReference bytesOut = new PointerByReference();
        T2zLib.NativeLongByReference lenOut = new T2zLib.NativeLongByReference();

        int code = lib.pczt_finalize_and_extract(pczt.takeHandle(), bytesOut, lenOut);
        checkResult(code, "Finalize and extract");

        int len = lenOut.getValue().intValue();
        Pointer ptr = bytesOut.getValue();
        byte[] result = ptr.getByteArray(0, len);
        lib.pczt_free_bytes(ptr, new NativeLong(len));

        return result;
    }

    /**
     * Serialize PCZT to bytes.
     *
     * @param pczt The PCZT (not consumed)
     * @return Serialized bytes
     */
    public static byte[] serialize(PCZT pczt) {
        PointerByReference bytesOut = new PointerByReference();
        T2zLib.NativeLongByReference lenOut = new T2zLib.NativeLongByReference();

        int code = lib.pczt_serialize(pczt.getHandle(), bytesOut, lenOut);
        checkResult(code, "Serialize PCZT");

        int len = lenOut.getValue().intValue();
        Pointer ptr = bytesOut.getValue();
        byte[] result = ptr.getByteArray(0, len);
        lib.pczt_free_bytes(ptr, new NativeLong(len));

        return result;
    }

    /**
     * Parse PCZT from bytes.
     *
     * @param bytes Serialized PCZT bytes
     * @return Parsed PCZT
     */
    public static PCZT parse(byte[] bytes) {
        PointerByReference handleOut = new PointerByReference();
        int code = lib.pczt_parse(bytes, new NativeLong(bytes.length), handleOut);
        checkResult(code, "Parse PCZT");
        return new PCZT(handleOut.getValue());
    }

    /**
     * Calculate the ZIP-317 transaction fee.
     *
     * <p>This is a pure function that computes the fee based on transaction shape.
     * Use this to calculate fees before building a transaction, e.g., for "send max"
     * functionality where you need to know the fee to calculate the maximum sendable amount.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * // Transparent-only: 1 input, 2 outputs (1 payment + 1 change)
     * long fee = T2z.calculateFee(1, 2, 0); // Returns 10000
     *
     * // Shielded: 1 input, 1 change, 1 orchard output
     * long fee = T2z.calculateFee(1, 1, 1); // Returns 15000
     *
     * // Calculate max sendable amount
     * long totalInput = 100_000_000L; // 1 ZEC in zatoshis
     * long fee = T2z.calculateFee(1, 2, 0);
     * long maxSend = totalInput - fee; // 99_990_000 zatoshis
     * }</pre>
     *
     * @param numTransparentInputs  Number of transparent UTXOs to spend
     * @param numTransparentOutputs Number of transparent outputs (including change if any)
     * @param numOrchardOutputs     Number of Orchard (shielded) outputs
     * @return The fee in zatoshis
     * @see <a href="https://zips.z.cash/zip-0317">ZIP-317</a>
     */
    public static long calculateFee(int numTransparentInputs, int numTransparentOutputs, int numOrchardOutputs) {
        return lib.pczt_calculate_fee(
            new NativeLong(numTransparentInputs),
            new NativeLong(numTransparentOutputs),
            new NativeLong(numOrchardOutputs)
        );
    }
}
