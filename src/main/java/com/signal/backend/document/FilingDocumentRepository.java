package com.signal.backend.document;

import com.signal.backend.filing.RawFiling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FilingDocumentRepository extends JpaRepository<FilingDocument, UUID> {

    boolean existsByRawFiling(RawFiling rawFiling);

    @Query("""
            SELECT rf FROM RawFiling rf
            WHERE NOT EXISTS (
                SELECT 1 FROM FilingDocument fd
                WHERE fd.rawFiling = rf
            )
            ORDER BY rf.filedAt DESC
            """)
    List<RawFiling> findUnparsedRawFilings();

    @Query("""
            SELECT fd FROM FilingDocument fd
            WHERE fd.parseStatus = 'FAILED'
            ORDER BY fd.parsedAt DESC
            """)
    List<FilingDocument> findFailedDocuments();
}
