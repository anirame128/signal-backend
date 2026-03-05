package com.signal.backend.fetchstate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FetchStateRepository extends JpaRepository<FetchState, Integer> {
}