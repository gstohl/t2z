package com.zcash.t2z;

/**
 * Exception thrown when a t2z operation fails.
 */
public class T2zException extends RuntimeException {
    private final ResultCode code;

    public T2zException(String message, ResultCode code) {
        super(message);
        this.code = code;
    }

    public ResultCode getCode() {
        return code;
    }
}
