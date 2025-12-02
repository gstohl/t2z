package com.zcash.t2z;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.io.Closeable;
import java.lang.ref.Cleaner;
import java.util.List;

/**
 * Transaction request containing multiple payments.
 */
public final class TransactionRequest implements Closeable {
    private static final Cleaner cleaner = Cleaner.create();
    private static final T2zLib lib = T2zLib.INSTANCE;

    private Pointer handle;
    private boolean closed = false;
    private final Cleaner.Cleanable cleanable;

    /**
     * Create a transaction request from a list of payments.
     *
     * @param payments List of payments (must not be empty)
     */
    public TransactionRequest(List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            throw new IllegalArgumentException("At least one payment is required");
        }

        // Build C payment array - must use ByReference for proper JNA handling
        T2zLib.CPayment.ByReference firstPayment = new T2zLib.CPayment.ByReference();
        T2zLib.CPayment[] cPayments = (T2zLib.CPayment[]) firstPayment.toArray(payments.size());

        for (int i = 0; i < payments.size(); i++) {
            Payment p = payments.get(i);
            cPayments[i].address = p.getAddress();
            cPayments[i].amount = p.getAmount();
            cPayments[i].memo = p.getMemo();
            cPayments[i].label = p.getLabel();
            cPayments[i].message = p.getMessage();
        }

        PointerByReference handleOut = new PointerByReference();
        int code = lib.pczt_transaction_request_new(
            firstPayment,
            new NativeLong(cPayments.length),
            handleOut
        );
        T2z.checkResult(code, "Create transaction request");

        this.handle = handleOut.getValue();
        this.cleanable = cleaner.register(this, new CleanerAction(handle));
    }

    /**
     * Set the target block height for consensus branch ID selection.
     *
     * @param height The target block height
     */
    public void setTargetHeight(int height) {
        checkNotClosed();
        int code = lib.pczt_transaction_request_set_target_height(handle, height);
        T2z.checkResult(code, "Set target height");
    }

    /**
     * Set whether to use mainnet parameters for consensus branch ID.
     *
     * <p>By default, the library uses mainnet parameters. Set this to false for testnet.
     * Regtest networks (like Zebra's regtest) typically use mainnet-like branch IDs,
     * so keep the default (true) for regtest.</p>
     *
     * @param useMainnet true for mainnet/regtest, false for testnet
     */
    public void setUseMainnet(boolean useMainnet) {
        checkNotClosed();
        int code = lib.pczt_transaction_request_set_use_mainnet(handle, useMainnet);
        T2z.checkResult(code, "Set use mainnet");
    }

    @Override
    public void close() {
        if (!closed) {
            cleanable.clean();
            handle = null;
            closed = true;
        }
    }

    Pointer getHandle() {
        checkNotClosed();
        return handle;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("TransactionRequest already closed");
        }
    }

    private static class CleanerAction implements Runnable {
        private Pointer handle;

        CleanerAction(Pointer handle) {
            this.handle = handle;
        }

        @Override
        public void run() {
            if (handle != null) {
                lib.pczt_transaction_request_free(handle);
                handle = null;
            }
        }
    }
}
