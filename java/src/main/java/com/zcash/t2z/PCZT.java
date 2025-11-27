package com.zcash.t2z;

import com.sun.jna.Pointer;

import java.io.Closeable;
import java.lang.ref.Cleaner;

/**
 * Partially Constructed Zcash Transaction (PCZT).
 */
public final class PCZT implements Closeable {
    private static final Cleaner cleaner = Cleaner.create();
    private static final T2zLib lib = T2zLib.INSTANCE;

    private Pointer handle;
    private boolean closed = false;
    private Cleaner.Cleanable cleanable;

    /**
     * Create a PCZT from a native handle (internal use only).
     */
    PCZT(Pointer handle) {
        this.handle = handle;
        this.cleanable = cleaner.register(this, new CleanerAction(handle));
    }

    @Override
    public void close() {
        if (!closed) {
            if (cleanable != null) {
                cleanable.clean();
            }
            handle = null;
            closed = true;
        }
    }

    /**
     * Get the native handle (read-only access).
     */
    Pointer getHandle() {
        checkNotClosed();
        return handle;
    }

    /**
     * Take ownership of the handle (transfers ownership, makes this instance invalid).
     */
    Pointer takeHandle() {
        checkNotClosed();
        cleanable = null; // Don't free - ownership transferred
        Pointer h = handle;
        handle = null;
        closed = true;
        return h;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("PCZT already closed");
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
                lib.pczt_free(handle);
                handle = null;
            }
        }
    }
}
