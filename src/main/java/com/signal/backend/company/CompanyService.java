package com.signal.backend.company;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional
    public Company upsert(String cik, String companyName) {
        return companyRepository.findById(cik)
                .map(existing -> {
                    if (!existing.getCompanyName().equals(companyName)) {
                        log.debug("Updating company name for CIK {}: '{}' -> '{}'", 
                                cik, existing.getCompanyName(), companyName);
                        existing.setCompanyName(companyName);
                    }
                    return companyRepository.save(existing);
                })
                .orElseGet(() -> {
                    log.debug("Creating new company for CIK {}: '{}'", cik, companyName);
                    return companyRepository.save(
                            new Company(cik, companyName)
                        );
                });
    }
}