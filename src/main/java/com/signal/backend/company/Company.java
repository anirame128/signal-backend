package com.signal.backend.company;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "companies")
public class Company {
    @Id
    @Column(name = "cik", length = 10, nullable = false, updatable = false)
    private String cik;

    @Column(name = "company_name", length = 200, nullable=false)
    private String companyName;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private OffsetDateTime firstSeenAt;

    protected Company() {}

    public Company(String cik, String companyName) {
        this.cik = cik;
        this.companyName = companyName;
    }

    @PrePersist
    void prePersist() {
        if (firstSeenAt == null) {
            firstSeenAt = OffsetDateTime.now();
        }
    }

    public String getCik() {
        return cik;
    }

    public String getCompanyName() {
        return companyName;
    }

    public OffsetDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setCompanyName(String companyName) {
        this.companyName = Objects.requireNonNull(companyName);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Company other)) return false;
        return Objects.equals(cik, other.cik);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cik);
    }

    @Override
    public String toString() {
        return "Company{cik='%s', companyName='%s'}".formatted(cik, companyName);
    }
}