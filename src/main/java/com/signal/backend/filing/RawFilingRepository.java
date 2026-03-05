package com.signal.backend.filing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RawFilingRepository extends JpaRepository<RawFiling, UUID> {
    boolean existsByAccessionNumber(String accessionNumber);
}