CREATE TABLE IF NOT EXISTS companies (
    cik VARCHAR(10) PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS raw_filings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accession_number VARCHAR(25) NOT NULL UNIQUE,
    cik VARCHAR(10) NOT NULL REFERENCES companies(cik),
    form_type VARCHAR(10) NOT NULL,
    filed_at DATE NOT NULL,
    edgar_url TEXT NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_raw_filings_filed_at ON raw_filings(filed_at DESC);

CREATE INDEX idx_raw_filings_cik ON raw_filings(cik);

CREATE TABLE fetch_state (
    id SERIAL PRIMARY KEY,
    last_fetched_at TIMESTAMPTZ,
    total_filings_fetched INTEGER NOT NULL DEFAULT 0
);

INSERT INTO fetch_state (last_fetched_at, total_filings_fetched)
VALUES (NULL, 0);