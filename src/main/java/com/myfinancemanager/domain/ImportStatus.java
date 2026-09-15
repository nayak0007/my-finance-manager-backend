package com.myfinancemanager.domain;

public enum ImportStatus {
    QUEUED,
    PROCESSING,
    READY_FOR_REVIEW,
    COMMITTED,
    PARTIALLY_COMMITTED,
    FAILED
}
