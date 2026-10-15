package com.vertice.api.user;

import com.vertice.api.common.exception.ExportTooLargeException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * An in-memory output that refuses to grow past {@code maxBytes}: the write that would cross the
 * limit throws {@link ExportTooLargeException} before anything of it is stored, so the buffer never
 * stores more than {@code maxBytes} (P1-05, review of #69). The serializer's own write buffer comes on
 * top, a few KB at most, so an oversized export is stopped while it is being written instead of after
 * the whole document has been built. The backing array grows by doubling, so it is at most twice
 * {@code maxBytes}: bounded, without allocating the full limit up front for every small export.
 */
final class BoundedOutputStream extends ByteArrayOutputStream {

    private final long maxBytes;

    BoundedOutputStream(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public synchronized void write(int b) {
        ensureRoom(1);
        super.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        ensureRoom(len);
        super.write(b, off, len);
    }

    /** The document as written; at most {@code maxBytes} bytes to decode. */
    String toUtf8String() {
        return toString(StandardCharsets.UTF_8);
    }

    private void ensureRoom(int len) {
        if (count + (long) len > maxBytes) {
            throw new ExportTooLargeException("the document exceeds %d bytes".formatted(maxBytes));
        }
    }
}
