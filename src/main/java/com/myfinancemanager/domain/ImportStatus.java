package com.myfinancemanager.domain;

public enum ImportStatus {
    QUEUED,
    PROCESSING,
    READY_FOR_REVIEW,
    COMMITTED,
    PARTIALLY_COMMITTED,
    FAILED,
    /**
     * The user aborted the import while it was still being parsed. Terminal: the parse result
     * is discarded, no staged transaction survives, and the batch stays in the history list.
     */
    CANCELLED
}
