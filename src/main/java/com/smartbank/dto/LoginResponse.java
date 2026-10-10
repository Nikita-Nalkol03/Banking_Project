package com.smartbank.dto;

import com.smartbank.entity.Role;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private Long userId;
    private String email;
    private Role role;
}