package com.zcash.t2z;

import com.sun.jna.Pointer;

import java.io.Closeable;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Partially Constructed Zcash Transaction (PCZT).
 */
public final class PCZT implements Closeable {
    private static final Cleaner cleaner = Cleaner.create();
    private static final T2zLib lib = T2zLib.INSTANCE;

    // Shared holder so CleanerAction can be cancelled by setting to null
    private final AtomicReference<Pointer> handleHolder;
    private boolean closed = false;
    private Cleaner.Cleanable cleanable;

    /**
     * Create a PCZT from a native handle (internal use only).
     */
    PCZT(Pointer handle) {
        this.handleHolder = new AtomicReference<>(handle);
        this.cleanable = cleaner.register(this, new CleanerAction(handleHolder));
    }

    @Override
    public void close() {
        if (!closed) {
            if (cleanable != null) {
                cleanable.clean();
            }
            closed = true;
        }
    }

    /**
     * Get the native handle (read-only access).
     */
    Pointer getHandle() {
        checkNotClosed();
        return handleHolder.get();
    }

    /**
     * Take ownership of the handle (transfers ownership, makes this instance invalid).
     */
    Pointer takeHandle() {
        checkNotClosed();
        // Atomically take the handle and clear the holder so CleanerAction won't free it
        Pointer h = handleHolder.getAndSet(null);
        closed = true;
        // Clean up the cleanable (it will do nothing since handleHolder is now null)
        if (cleanable != null) {
            cleanable.clean();
            cleanable = null;
        }
        return h;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("PCZT already closed");
        }
    }

    private static class CleanerAction implements Runnable {
        private final AtomicReference<Pointer> handleHolder;

        CleanerAction(AtomicReference<Pointer> handleHolder) {
            this.handleHolder = handleHolder;
        }

        @Override
        public void run() {
            Pointer handle = handleHolder.getAndSet(null);
            if (handle != null) {
                lib.pczt_free(handle);
            }
        }
    }
}
