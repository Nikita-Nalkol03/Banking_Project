
package com.smartbank.service;

import com.smartbank.dto.LoginRequest;
import com.smartbank.dto.LoginResponse;
import com.smartbank.dto.RefreshTokenRequest;
import com.smartbank.dto.RegisterRequest;
import com.smartbank.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);
}