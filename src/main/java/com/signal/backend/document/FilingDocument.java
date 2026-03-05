package com.signal.backend.document;

import com.signal.backend.filing.RawFiling;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "filing_documents")
public class FilingDocument {

    public enum ParseStatus { SUCCESS, FAILED, PARTIAL }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_filing_id", nullable = false, unique = true)
    private RawFiling rawFiling;

    @Column(name = "primary_doc_url", nullable = false)
    private String primaryDocUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "item_numbers", columnDefinition = "varchar(10)[]", nullable = false)
    private String[] itemNumbers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_texts", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> itemTexts;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "exhibit_types", columnDefinition = "varchar(20)[]")
    private String[] exhibitTypes;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "filing_size_bytes")
    private Integer filingSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 10)
    private ParseStatus parseStatus;

    @Column(name = "parse_error")
    private String parseError;

    @Column(name = "parsed_at", nullable = false, updatable = false)
    private OffsetDateTime parsedAt;

    protected FilingDocument() {}

    public FilingDocument(RawFiling rawFiling,
                          String primaryDocUrl,
                          String[] itemNumbers,
                          Map<String, String> itemTexts,
                          String[] exhibitTypes,
                          Integer wordCount,
                          Integer filingSizeBytes,
                          ParseStatus parseStatus) {
        this.id = UUID.randomUUID();
        this.rawFiling = rawFiling;
        this.primaryDocUrl = primaryDocUrl;
        this.itemNumbers = itemNumbers;
        this.itemTexts = itemTexts;
        this.exhibitTypes = exhibitTypes;
        this.wordCount = wordCount;
        this.filingSizeBytes = filingSizeBytes;
        this.parseStatus = parseStatus;
        this.parsedAt = OffsetDateTime.now();
    }

    public FilingDocument(RawFiling rawFiling, String parseError) {
        this.id = UUID.randomUUID();
        this.rawFiling = rawFiling;
        this.primaryDocUrl = "";
        this.itemNumbers = new String[0];
        this.itemTexts = Map.of();
        this.exhibitTypes = new String[0];
        this.parseStatus = ParseStatus.FAILED;
        this.parseError = parseError;
        this.parsedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public RawFiling getRawFiling() { return rawFiling; }
    public String getPrimaryDocUrl() { return primaryDocUrl; }
    public String[] getItemNumbers() { return itemNumbers; }
    public Map<String, String> getItemTexts() { return itemTexts; }
    public String[] getExhibitTypes() { return exhibitTypes; }
    public Integer getWordCount() { return wordCount; }
    public Integer getFilingSizeBytes() { return filingSizeBytes; }
    public ParseStatus getParseStatus() { return parseStatus; }
    public String getParseError() { return parseError; }
    public OffsetDateTime getParsedAt() { return parsedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FilingDocument other)) return false;
        return Objects.equals(rawFiling, other.rawFiling);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawFiling);
    }
}
