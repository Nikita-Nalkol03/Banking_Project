package com.smartbank.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartbank.dto.TransactionHistoryResponse;
import com.smartbank.entity.TransactionType;
import com.smartbank.service.TransactionHistoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class TransactionHistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionHistoryService transactionHistoryService;

    @GetMapping("/{accountNumber}/transactions")
    public ResponseEntity<Page<TransactionHistoryResponse>> getTransactions(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) TransactionType type) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page index cannot be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        if (!"createdAt".equals(sortBy)
                && !"amount".equals(sortBy)) {
            throw new IllegalArgumentException(
                    "sortBy must be createdAt or amount"
            );
        }

        Sort.Direction sortDirection;

        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "direction must be asc or desc"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortDirection, sortBy)
        );

        return ResponseEntity.ok(
                transactionHistoryService.getAccountTransactions(
                        accountNumber,
                        type,
                        pageable
                )
        );
    }
}