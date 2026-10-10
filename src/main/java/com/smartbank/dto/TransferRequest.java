package com.smartbank.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferRequest {

    @NotBlank(message = "Sender account number is required")
    private String senderAccountNumber;

    @NotBlank(message = "Receiver account number is required")
    private String receiverAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(
            value = "0.01",
            message = "Transfer amount must be greater than zero"
    )
    private BigDecimal amount;

    @Size(
            max = 255,
            message = "Description cannot exceed 255 characters"
    )
    private String description;
}