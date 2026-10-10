
package com.smartbank.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.smartbank.dto.TransferRequest;
import com.smartbank.dto.TransferResponse;
import com.smartbank.service.TransferService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transfers")
@Validated
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        TransferResponse response =
                transferService.transfer(request, idempotencyKey);

        return ResponseEntity.ok(response);
    }
}