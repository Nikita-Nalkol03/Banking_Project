
package com.smartbank.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartbank.entity.IdempotencyRecord;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );
    void deleteByUserId(Long userId);
}