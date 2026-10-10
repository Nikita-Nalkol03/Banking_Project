package com.smartbank.service;

import java.util.List;

import com.smartbank.dto.AccountResponse;
import com.smartbank.dto.CreateAccountRequest;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    List<AccountResponse> getMyAccounts();
}