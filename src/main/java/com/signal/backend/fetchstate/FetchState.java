package com.signal.backend.fetchstate;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fetch_state")
public class FetchState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @Column(name = "last_fetched_at")
    private OffsetDateTime lastFetchedAt;

    @Column(name = "total_filings_fetched", nullable = false)
    private int totalFilingsFetched;

    protected FetchState() {}

    public Integer getId() { return id; }
    public OffsetDateTime getLastFetchedAt() { return lastFetchedAt; }
    public int getTotalFilingsFetched() { return totalFilingsFetched; }
    
    public void recordFetch(int newFilingsCount) {
        this.lastFetchedAt = OffsetDateTime.now();
        this.totalFilingsFetched += newFilingsCount;
    }
}