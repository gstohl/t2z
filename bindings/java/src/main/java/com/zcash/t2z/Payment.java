package com.zcash.t2z;

import java.util.Objects;

/**
 * Payment request with address, amount, and optional metadata.
 */
public final class Payment {
    private final String address;
    private final long amount;
    private final String memo;
    private final String label;
    private final String message;

    /**
     * Create a payment with required fields only.
     *
     * @param address The destination address (transparent or unified)
     * @param amount  The amount in zatoshis
     */
    public Payment(String address, long amount) {
        this(address, amount, null, null, null);
    }

    /**
     * Create a payment with all fields.
     *
     * @param address The destination address (transparent or unified)
     * @param amount  The amount in zatoshis
     * @param memo    Optional memo for shielded outputs
     * @param label   Optional label
     * @param message Optional message
     */
    public Payment(String address, long amount, String memo, String label, String message) {
        this.address = Objects.requireNonNull(address, "address must not be null");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        this.amount = amount;
        this.memo = memo;
        this.label = label;
        this.message = message;
    }

    public String getAddress() {
        return address;
    }

    public long getAmount() {
        return amount;
    }

    public String getMemo() {
        return memo;
    }

    public String getLabel() {
        return label;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return amount == payment.amount &&
                Objects.equals(address, payment.address) &&
                Objects.equals(memo, payment.memo) &&
                Objects.equals(label, payment.label) &&
                Objects.equals(message, payment.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, amount, memo, label, message);
    }

    @Override
    public String toString() {
        return "Payment{" +
                "address='" + address + '\'' +
                ", amount=" + amount +
                (memo != null ? ", memo='" + memo + '\'' : "") +
                (label != null ? ", label='" + label + '\'' : "") +
                (message != null ? ", message='" + message + '\'' : "") +
                '}';
    }

    /**
     * Builder for Payment objects.
     */
    public static class Builder {
        private String address;
        private long amount;
        private String memo;
        private String label;
        private String message;

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder amount(long amount) {
            this.amount = amount;
            return this;
        }

        public Builder memo(String memo) {
            this.memo = memo;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Payment build() {
            return new Payment(address, amount, memo, label, message);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
