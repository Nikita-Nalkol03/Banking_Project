
package com.smartbank.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbank.dto.TransferRequest;
import com.smartbank.dto.TransferResponse;
import com.smartbank.entity.IdempotencyRecord;
import com.smartbank.entity.TransactionStatus;
import com.smartbank.entity.User;
import com.smartbank.repository.IdempotencyRecordRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.TransferService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final UserRepository userRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransferExecutionService transferExecutionService;

    @Override
    public TransferResponse transfer(
            TransferRequest request,
            String idempotencyKey) {

        validateIdempotencyKey(idempotencyKey);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Transfer request is required"
            );
        }

        User user = getAuthenticatedUser();
        String normalizedKey = idempotencyKey.trim();
        String requestHash = calculateRequestHash(request);

        var existingRecord = idempotencyRecordRepository
                .findByUserIdAndIdempotencyKey(
                        user.getId(),
                        normalizedKey
                );

        if (existingRecord.isPresent()) {
            return replayOrReject(
                    existingRecord.get(),
                    requestHash
            );
        }

        try {
            return transferExecutionService.execute(
                    request,
                    normalizedKey,
                    user.getId(),
                    requestHash
            );
        } catch (DataIntegrityViolationException ex) {
            /*
             * A concurrent request may have inserted the same key.
             * The execution service's transaction has rolled back
             * before control reaches this catch block.
             */
            var concurrentRecord = idempotencyRecordRepository
                    .findByUserIdAndIdempotencyKey(
                            user.getId(),
                            normalizedKey
                    );

            if (concurrentRecord.isPresent()) {
                return replayOrReject(
                        concurrentRecord.get(),
                        requestHash
                );
            }

            throw ex;
        }
    }

    private TransferResponse replayOrReject(
            IdempotencyRecord record,
            String requestHash) {

        if (!record.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different request"
            );
        }

        if (record.getStatus() != TransactionStatus.SUCCESS
                || record.getTransactionReference() == null
                || record.getSenderAccountNumber() == null
                || record.getReceiverAccountNumber() == null
                || record.getAmount() == null
                || record.getResponseCreatedAt() == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A completed response is not available for this idempotency key"
            );
        }

        return new TransferResponse(
                record.getTransactionReference(),
                record.getSenderAccountNumber(),
                record.getReceiverAccountNumber(),
                record.getAmount(),
                record.getStatus(),
                record.getDescription(),
                record.getResponseCreatedAt()
        );
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key is required and must not exceed 100 characters"
            );
        }
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().equals("anonymousUser")) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User is not authenticated"
            );
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));
    }

    private String calculateRequestHash(TransferRequest request) {
        String canonicalRequest =
                normalize(request.getSenderAccountNumber())
                + "\n" + normalize(request.getReceiverAccountNumber())
                + "\n" + (request.getAmount() == null
                        ? ""
                        : request.getAmount().stripTrailingZeros().toPlainString())
                + "\n" + normalize(request.getDescription());

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}