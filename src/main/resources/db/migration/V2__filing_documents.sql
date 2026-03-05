-- ------------------------------------------------------------
-- filing_documents
-- Stores the parsed, structured output of fetching and parsing
-- the primary 8-K document from EDGAR for each raw filing.
-- ------------------------------------------------------------

CREATE TABLE filing_documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- FK to the raw filing this document was parsed from
    -- UNIQUE enforces exactly one parsed document per raw filing
    raw_filing_id       UUID NOT NULL REFERENCES raw_filings(id) UNIQUE,

    -- The resolved direct URL to the 8-K HTML document
    -- (index page URL with /ix?doc= stripped, https://www.sec.gov prepended)
    -- Stored so future re-fetches skip the index page parse entirely
    primary_doc_url     TEXT NOT NULL,

    -- Item numbers present in this filing (e.g. '{5.02,1.01,9.01}')
    -- Drives tier classification and is the strongest single ML feature
    -- Already available free from the RSS feed summary — stored here
    -- as the authoritative parsed value
    item_numbers        VARCHAR(10)[] NOT NULL,

    -- Clean prose text per item section, keyed by item number
    -- e.g. {"5.02": "CEO John Smith resigned effective...", "1.01": "..."}
    -- Stored as JSONB so individual sections can be queried directly:
    --   item_texts->>'5.02'
    -- without loading the full document. Item 9.01 (exhibits boilerplate)
    -- is never stored here.
    item_texts          JSONB NOT NULL DEFAULT '{}',

    -- Exhibit types present in the filing index
    -- e.g. '{EX-99.1,EX-10.1}'
    -- Lets us query: 'EX-99.1' = ANY(exhibit_types) to detect press releases
    exhibit_types       VARCHAR(20)[],

    -- Total word count of the filing body (excluding XBRL header and
    -- Item 9.01 boilerplate). Proxy for filing complexity — known ML feature.
    word_count          INTEGER,

    -- Raw file size in bytes from the index page document table.
    -- Secondary proxy for filing complexity.
    filing_size_bytes   INTEGER,

    -- Parse lifecycle tracking
    -- SUCCESS: document fetched, parsed, all item texts extracted
    -- FAILED:  fetch or parse error — see parse_error for details
    -- PARTIAL: document fetched but some item sections could not be extracted
    parse_status        VARCHAR(10) NOT NULL DEFAULT 'SUCCESS'
                            CHECK (parse_status IN ('SUCCESS', 'FAILED', 'PARTIAL')),

    -- Error message if parse_status != SUCCESS, NULL otherwise
    -- Stored so retry logic can distinguish transient vs permanent failures
    parse_error         TEXT,

    parsed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_filing_documents_raw_filing_id
    ON filing_documents(raw_filing_id);

-- Speeds up queries filtering by parse status (retry jobs, monitoring)
CREATE INDEX idx_filing_documents_parse_status
    ON filing_documents(parse_status)
    WHERE parse_status != 'SUCCESS';