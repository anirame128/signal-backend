package com.signal.backend.filing;

import com.signal.backend.company.Company;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.time.LocalDate;

@Entity
@Table(name = "raw_filings")
public class RawFiling {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "accession_number", length = 25, nullable = false, unique = true)
    private String accessionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cik", nullable = false, updatable = false)
    private Company company;

    @Column(name = "form_type", length = 10, nullable = false)
    private String formType;

    @Column(name = "filed_at", nullable = false)
    private LocalDate filedAt;

    @Column(name = "edgar_url", nullable = false)
    private String edgarUrl;

    @Column(name = "fetched_at", nullable = false, updatable = false)
    private OffsetDateTime fetchedAt;

    protected RawFiling() {}

    public RawFiling(String accessionNumber, 
                     Company company, 
                     String formType, 
                     LocalDate filedAt, 
                     String edgarUrl) {
        this.id = UUID.randomUUID();
        this.accessionNumber = accessionNumber;
        this.company = company;
        this.formType = formType;
        this.filedAt = filedAt;
        this.edgarUrl = edgarUrl;
    }

    @PrePersist
    void prePersist() {
        if (fetchedAt == null) {
            fetchedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public String getAccessionNumber() { return accessionNumber; }
    public Company getCompany() { return company; }
    public String getFormType() { return formType; }
    public LocalDate getFiledAt() { return filedAt; }
    public String getEdgarUrl() { return edgarUrl; }
    public OffsetDateTime getFetchedAt() { return fetchedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RawFiling other)) return false;
        return Objects.equals(accessionNumber, other.accessionNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessionNumber);
    }

   @Override
    public String toString() {
        return "RawFiling{accessionNumber='%s', formType='%s', filedAt=%s}"
                .formatted(accessionNumber, formType, filedAt);
    }
}