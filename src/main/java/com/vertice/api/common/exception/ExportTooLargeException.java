package com.vertice.api.common.exception;

/**
 * A data export that would exceed {@code vertice.export.*} (P1-05): too many rows in one section,
 * or a document over the size limit. Maps to {@code RESOURCE_EXHAUSTED}. The message names the
 * section and the limit only, never any of the data.
 */
public class ExportTooLargeException extends RuntimeException {

    public ExportTooLargeException(String detail) {
        super("Data export too large for one response: " + detail);
    }
}
