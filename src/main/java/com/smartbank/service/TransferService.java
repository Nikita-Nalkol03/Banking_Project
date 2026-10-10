package com.smartbank.service;

import com.smartbank.dto.TransferRequest;
import com.smartbank.dto.TransferResponse;

public interface TransferService {

    TransferResponse transfer(
            TransferRequest request,
            String idempotencyKey
    );
}