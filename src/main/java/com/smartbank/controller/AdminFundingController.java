package com.smartbank.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartbank.dto.DepositRequest;
import com.smartbank.dto.DepositResponse;
import com.smartbank.service.DepositService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/accounts")
@Validated
@RequiredArgsConstructor
public class AdminFundingController {

    private final DepositService depositService;

    @PostMapping("/{accountNumber}/deposit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepositResponse> deposit(
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest request) {

        return ResponseEntity.ok(
                depositService.deposit(accountNumber, request)
        );
    }
}