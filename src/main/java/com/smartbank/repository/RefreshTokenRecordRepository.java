
package com.smartbank.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.smartbank.entity.RefreshTokenRecord;

@Repository
public interface RefreshTokenRecordRepository
        extends JpaRepository<RefreshTokenRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from RefreshTokenRecord r
            where r.tokenHash = :tokenHash
            """)
    Optional<RefreshTokenRecord> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);
}