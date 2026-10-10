package com.smartbank.service;

import com.smartbank.dto.DepositRequest;
import com.smartbank.dto.DepositResponse;

public interface DepositService {

    DepositResponse deposit(
            String accountNumber,
            DepositRequest request
    );
}