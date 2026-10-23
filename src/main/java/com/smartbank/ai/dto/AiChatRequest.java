package com.smartbank.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AiChatRequest(


    @NotBlank(message = "Message must not be empty")
    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    String message,

    @NotBlank(message = "Account number is required")
    String accountNumber,

    LocalDate startDate,

    LocalDate endDate


) {
}
