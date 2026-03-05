package com.signal.backend.edgar;

import java.time.OffsetDateTime;

/**
 * Represents a single parsed entry from the EDGAR 8-K Atom RSS feed.
 * This is an intermediate value object and is not persisted directly.
 */
public record EdgarFeedEntry(
        String accessionNumber,
        String cik,
        String companyName,
        String formType,
        String edgarUrl,
        OffsetDateTime filedAt
) {
}