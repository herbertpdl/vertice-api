package com.vertice.api.common.exception;

/**
 * The ids in the request all exist, but they don't belong together — e.g. a set from another
 * workout, or a plan for a client the trainer doesn't have. Maps to {@code FAILED_PRECONDITION}:
 * retrying the same request can't succeed until the related data changes.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
