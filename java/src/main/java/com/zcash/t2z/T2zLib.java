/**
 * JNA interface for the t2z native library.
 *
 * This class provides low-level FFI bindings to the Rust t2z library.
 * Users should prefer the high-level API in T2z.java instead of using these directly.
 */
package com.zcash.t2z;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.util.Arrays;
import java.util.List;

/**
 * JNA interface to the t2z native library.
 */
public interface T2zLib extends Library {

    T2zLib INSTANCE = Native.load(
        Platform.isMac() ? "t2z" : Platform.isLinux() ? "t2z" : "t2z",
        T2zLib.class
    );

    // Result codes
    int SUCCESS = 0;
    int ERROR_NULL_POINTER = 1;
    int ERROR_INVALID_UTF8 = 2;
    int ERROR_BUFFER_TOO_SMALL = 3;
    int ERROR_PROPOSAL = 10;
    int ERROR_PROVER = 11;
    int ERROR_VERIFICATION = 12;
    int ERROR_SIGHASH = 13;
    int ERROR_SIGNATURE = 14;
    int ERROR_COMBINE = 15;
    int ERROR_FINALIZATION = 16;
    int ERROR_PARSE = 17;
    int ERROR_NOT_IMPLEMENTED = 99;

    // Error handling
    int pczt_get_last_error(byte[] buffer, NativeLong bufferLen);

    // Transaction request
    int pczt_transaction_request_new(
        CPayment.ByReference payments,
        NativeLong numPayments,
        PointerByReference requestOut
    );

    void pczt_transaction_request_free(Pointer request);

    int pczt_transaction_request_set_target_height(Pointer request, int targetHeight);

    int pczt_transaction_request_set_use_mainnet(Pointer request, boolean useMainnet);

    // PCZT operations
    int pczt_propose_transaction(
        byte[] inputsBytes,
        NativeLong inputsBytesLen,
        Pointer request,
        String changeAddress,
        PointerByReference pcztOut
    );

    int pczt_prove_transaction(Pointer pczt, PointerByReference pcztOut);

    int pczt_verify_before_signing(
        Pointer pczt,
        Pointer request,
        CTransparentOutput.ByReference expectedChange,
        NativeLong expectedChangeLen
    );

    int pczt_get_sighash(Pointer pczt, NativeLong inputIndex, byte[] sighashOut);

    int pczt_append_signature(
        Pointer pczt,
        NativeLong inputIndex,
        byte[] signature,
        PointerByReference pcztOut
    );

    int pczt_combine(Pointer[] pczts, NativeLong numPczts, PointerByReference pcztOut);

    int pczt_finalize_and_extract(
        Pointer pczt,
        PointerByReference txBytesOut,
        NativeLongByReference txBytesLenOut
    );

    int pczt_parse(byte[] pcztBytes, NativeLong pcztBytesLen, PointerByReference pcztOut);

    int pczt_serialize(Pointer pczt, PointerByReference bytesOut, NativeLongByReference bytesLenOut);

    void pczt_free(Pointer pczt);

    void pczt_free_bytes(Pointer bytes, NativeLong len);

    /**
     * C-compatible payment structure for JNA.
     */
    @Structure.FieldOrder({"address", "amount", "memo", "label", "message"})
    class CPayment extends Structure {
        public String address;
        public long amount;
        public String memo;
        public String label;
        public String message;

        public CPayment() {
            super();
        }

        public static class ByReference extends CPayment implements Structure.ByReference {
        }

        public static class ByValue extends CPayment implements Structure.ByValue {
        }
    }

    /**
     * C-compatible transparent output structure for JNA.
     */
    @Structure.FieldOrder({"script_pub_key", "script_pub_key_len", "value"})
    class CTransparentOutput extends Structure {
        public Pointer script_pub_key;
        public NativeLong script_pub_key_len;
        public long value;

        public CTransparentOutput() {
            super();
        }

        public static class ByReference extends CTransparentOutput implements Structure.ByReference {
        }

        public static class ByValue extends CTransparentOutput implements Structure.ByValue {
        }
    }

    /**
     * JNA NativeLong by reference helper.
     */
    class NativeLongByReference extends com.sun.jna.ptr.ByReference {
        public NativeLongByReference() {
            super(NativeLong.SIZE);
        }

        public NativeLong getValue() {
            if (NativeLong.SIZE == 4) {
                return new NativeLong(getPointer().getInt(0));
            } else {
                return new NativeLong(getPointer().getLong(0));
            }
        }

        public void setValue(NativeLong value) {
            if (NativeLong.SIZE == 4) {
                getPointer().setInt(0, value.intValue());
            } else {
                getPointer().setLong(0, value.longValue());
            }
        }
    }
}
