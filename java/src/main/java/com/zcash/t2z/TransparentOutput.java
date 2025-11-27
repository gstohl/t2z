package com.zcash.t2z;

import java.util.Arrays;
import java.util.Objects;

/**
 * Transparent transaction output (for change verification).
 */
public final class TransparentOutput {
    private final byte[] scriptPubKey;
    private final long value;

    /**
     * Create a transparent output.
     *
     * @param scriptPubKey The scriptPubKey
     * @param value        The value in zatoshis
     */
    public TransparentOutput(byte[] scriptPubKey, long value) {
        Objects.requireNonNull(scriptPubKey, "scriptPubKey must not be null");
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        this.scriptPubKey = scriptPubKey.clone();
        this.value = value;
    }

    public byte[] getScriptPubKey() {
        return scriptPubKey.clone();
    }

    public long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransparentOutput that = (TransparentOutput) o;
        return value == that.value && Arrays.equals(scriptPubKey, that.scriptPubKey);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(value);
        result = 31 * result + Arrays.hashCode(scriptPubKey);
        return result;
    }

    @Override
    public String toString() {
        return "TransparentOutput{" +
                "value=" + value +
                '}';
    }
}
