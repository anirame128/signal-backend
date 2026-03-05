package com.signal.backend.filing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class FetchAdminController {

    private final RawFilingService rawFilingService;

    public FetchAdminController(RawFilingService rawFilingService) {
        this.rawFilingService = rawFilingService;
    }

    @PostMapping("/fetch-now")
    public ResponseEntity<Map<String, Object>> fetchNow() {
        int newFilingsInserted = rawFilingService.fetchAndPersist();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "newFilingsInserted", newFilingsInserted
        ));
    }
}
