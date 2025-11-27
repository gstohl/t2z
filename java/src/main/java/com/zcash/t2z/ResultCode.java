package com.zcash.t2z;

/**
 * Result codes from FFI functions.
 */
public enum ResultCode {
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

    private final int code;

    ResultCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ResultCode fromCode(int code) {
        for (ResultCode rc : values()) {
            if (rc.code == code) {
                return rc;
            }
        }
        throw new IllegalArgumentException("Unknown result code: " + code);
    }
}
