package com.signal.backend.document;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class FilingDocumentController {

    private final FilingDocumentService filingDocumentService;

    public FilingDocumentController(FilingDocumentService filingDocumentService) {
        this.filingDocumentService = filingDocumentService;
    }

    @PostMapping("/parse-documents")
    public ResponseEntity<Map<String, Object>> parseDocuments() {
        int parsed = filingDocumentService.parseUnparsed();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "documentsParsed", parsed
        ));
    }
}
