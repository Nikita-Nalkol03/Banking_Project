package com.smartbank.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepositRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(
            value = "0.01",
            message = "Deposit amount must be greater than zero"
    )
    private BigDecimal amount;
}