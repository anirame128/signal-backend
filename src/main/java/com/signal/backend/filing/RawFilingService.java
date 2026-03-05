package com.signal.backend.filing;

import com.signal.backend.company.Company;
import com.signal.backend.company.CompanyService;
import com.signal.backend.document.FilingDocumentService;
import com.signal.backend.edgar.EdgarClient;
import com.signal.backend.edgar.EdgarFeedEntry;
import com.signal.backend.fetchstate.FetchState;
import com.signal.backend.fetchstate.FetchStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RawFilingService {
    
    private static final Logger log = LoggerFactory.getLogger(RawFilingService.class);
    private static final int MAX_PAGES = 10;

    private final EdgarClient edgarClient;
    private final CompanyService companyService;
    private final RawFilingRepository rawFilingRepository;
    private final FetchStateRepository fetchStateRepository;
    private final FilingDocumentService filingDocumentService;
    private final ApplicationEventPublisher eventPublisher;

    public RawFilingService(EdgarClient edgarClient,
                            CompanyService companyService,
                            RawFilingRepository rawFilingRepository,
                            FetchStateRepository fetchStateRepository,
                            FilingDocumentService filingDocumentService,
                            ApplicationEventPublisher eventPublisher) {
        this.edgarClient = edgarClient;
        this.companyService = companyService;
        this.rawFilingRepository = rawFilingRepository;
        this.fetchStateRepository = fetchStateRepository;
        this.filingDocumentService = filingDocumentService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public int fetchAndPersist() {
        log.info("Checking for unparsed backlog before fetch");
        filingDocumentService.parseUnparsed();

        FetchState state = fetchStateRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "fetch_state table is empty — Flyway migration may not have run"));

        OffsetDateTime lastFetchedAt = state.getLastFetchedAt();
        int newFilingsCount = 0;
        boolean done = false;

        for (int page = 0; page < MAX_PAGES && !done; page++) {
            List<EdgarFeedEntry> entries = edgarClient.fetchPage(page * 100);

            if (entries.isEmpty()) {
                log.info("Empty page at offset {} — stopping pagination", page * 100);
                break;
            }

            for (EdgarFeedEntry entry : entries) {
                if (lastFetchedAt != null && !entry.filedAt().isAfter(lastFetchedAt)) {
                    log.info("Reached filedAt {} which is not after lastFetchedAt {} — stopping",
                            entry.filedAt(), lastFetchedAt);
                    done = true;
                    break;
                }

                if (rawFilingRepository.existsByAccessionNumber(entry.accessionNumber())) {
                    log.debug("Skipping already-stored filing: {}", entry.accessionNumber());
                    continue;
                }

                Company company = companyService.upsert(entry.cik(), entry.companyName());

                RawFiling filing = new RawFiling(
                        entry.accessionNumber(),
                        company,
                        entry.formType(),
                        entry.filedAt().toLocalDate(),
                        entry.edgarUrl()
                );

                try {
                    // Force INSERT now so unique-key collisions are handled per-row.
                    rawFilingRepository.saveAndFlush(filing);
                    eventPublisher.publishEvent(new RawFilingCreatedEvent(filing.getId()));
                    newFilingsCount++;
                    log.debug("Persisted new filing: {} ({})", entry.accessionNumber(), entry.companyName());
                } catch (DataIntegrityViolationException ex) {
                    // Concurrent runs can race; unique(accession_number) makes this idempotent.
                    log.info("Skipping duplicate filing due to unique constraint: {}", entry.accessionNumber());
                }
            }
        }

        state.recordFetch(newFilingsCount);
        fetchStateRepository.save(state);
        log.info("Fetch complete — {} new filings persisted", newFilingsCount);
        return newFilingsCount;
    }
}