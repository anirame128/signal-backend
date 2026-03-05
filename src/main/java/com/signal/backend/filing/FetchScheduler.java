package com.signal.backend.filing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FetchScheduler {
    private static final Logger log = LoggerFactory.getLogger(FetchScheduler.class);

    private final RawFilingService rawFilingService;

    public FetchScheduler(RawFilingService rawFilingService) {
        this.rawFilingService = rawFilingService;
    }

    @Scheduled(cron = "0 0/30 9-17 * * MON-FRI", zone = "America/New_York")
    public void scheduledFetch() {
        log.info("Scheduler triggered — starting EDGAR fetch");
        rawFilingService.fetchAndPersist();
    }
}