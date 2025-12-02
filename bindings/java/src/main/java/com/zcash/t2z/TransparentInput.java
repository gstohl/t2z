package com.zcash.t2z;

import java.util.Arrays;
import java.util.Objects;

/**
 * Transparent UTXO input to spend.
 */
public final class TransparentInput {
    private final byte[] pubkey;        // 33 bytes compressed
    private final byte[] txid;          // 32 bytes
    private final int vout;
    private final long amount;
    private final byte[] scriptPubKey;

    /**
     * Create a transparent input.
     *
     * @param pubkey       33-byte compressed secp256k1 public key
     * @param txid         32-byte transaction ID
     * @param vout         Output index
     * @param amount       Amount in zatoshis
     * @param scriptPubKey The scriptPubKey of the UTXO
     */
    public TransparentInput(byte[] pubkey, byte[] txid, int vout, long amount, byte[] scriptPubKey) {
        Objects.requireNonNull(pubkey, "pubkey must not be null");
        Objects.requireNonNull(txid, "txid must not be null");
        Objects.requireNonNull(scriptPubKey, "scriptPubKey must not be null");

        if (pubkey.length != 33) {
            throw new IllegalArgumentException("Invalid pubkey length: expected 33, got " + pubkey.length);
        }
        if (txid.length != 32) {
            throw new IllegalArgumentException("Invalid txid length: expected 32, got " + txid.length);
        }
        if (vout < 0) {
            throw new IllegalArgumentException("vout must be non-negative");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }

        this.pubkey = pubkey.clone();
        this.txid = txid.clone();
        this.vout = vout;
        this.amount = amount;
        this.scriptPubKey = scriptPubKey.clone();
    }

    public byte[] getPubkey() {
        return pubkey.clone();
    }

    public byte[] getTxid() {
        return txid.clone();
    }

    public int getVout() {
        return vout;
    }

    public long getAmount() {
        return amount;
    }

    public byte[] getScriptPubKey() {
        return scriptPubKey.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransparentInput that = (TransparentInput) o;
        return vout == that.vout &&
                amount == that.amount &&
                Arrays.equals(pubkey, that.pubkey) &&
                Arrays.equals(txid, that.txid) &&
                Arrays.equals(scriptPubKey, that.scriptPubKey);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(vout, amount);
        result = 31 * result + Arrays.hashCode(pubkey);
        result = 31 * result + Arrays.hashCode(txid);
        result = 31 * result + Arrays.hashCode(scriptPubKey);
        return result;
    }

    @Override
    public String toString() {
        return "TransparentInput{" +
                "vout=" + vout +
                ", amount=" + amount +
                '}';
    }

    /**
     * Builder for TransparentInput objects.
     */
    public static class Builder {
        private byte[] pubkey;
        private byte[] txid;
        private int vout;
        private long amount;
        private byte[] scriptPubKey;

        public Builder pubkey(byte[] pubkey) {
            this.pubkey = pubkey;
            return this;
        }

        public Builder txid(byte[] txid) {
            this.txid = txid;
            return this;
        }

        public Builder vout(int vout) {
            this.vout = vout;
            return this;
        }

        public Builder amount(long amount) {
            this.amount = amount;
            return this;
        }

        public Builder scriptPubKey(byte[] scriptPubKey) {
            this.scriptPubKey = scriptPubKey;
            return this;
        }

        public TransparentInput build() {
            return new TransparentInput(pubkey, txid, vout, amount, scriptPubKey);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
