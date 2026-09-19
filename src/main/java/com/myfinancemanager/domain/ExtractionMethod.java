package com.myfinancemanager.domain;

/**
 * Which engine produced a batch's transactions. Only {@link #OPENROUTER} is written by the
 * current parser; {@link #RAPID_API} and {@link #OPENROUTER_FALLBACK} are kept because rows
 * parsed by the retired two-provider flow still carry them in the database.
 */
public enum ExtractionMethod {
    /** Extraction by the OpenRouter AI provider — the only parser in use. */
    OPENROUTER,
    /** Legacy: produced by the removed RapidAPI primary parser. */
    RAPID_API,
    /** Legacy: produced by the OpenRouter path while it was a fallback to RapidAPI. */
    OPENROUTER_FALLBACK,
    NONE
}
