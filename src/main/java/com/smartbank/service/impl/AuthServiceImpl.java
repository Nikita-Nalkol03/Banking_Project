
package com.smartbank.service.impl;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartbank.dto.LoginRequest;
import com.smartbank.dto.LoginResponse;
import com.smartbank.dto.RefreshTokenRequest;
import com.smartbank.dto.RegisterRequest;
import com.smartbank.dto.UserResponse;
import com.smartbank.entity.RefreshTokenRecord;
import com.smartbank.entity.Role;
import com.smartbank.entity.User;
import com.smartbank.entity.UserStatus;
import com.smartbank.exception.ResourceAlreadyExistsException;
import com.smartbank.repository.RefreshTokenRecordRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.security.JwtService;
import com.smartbank.service.AuthService;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRecordRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        String phone = request.getPhone().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException(
                    "Email is already registered");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new ResourceAlreadyExistsException(
                    "Phone number is already registered");
        }

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getRole(),
                savedUser.getStatus());
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {

        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                email, request.getPassword()));

        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"));

        String accessToken =
                jwtService.generateAccessToken(userDetails);

        String refreshToken =
                jwtService.generateRefreshToken(userDetails);

        saveRefreshToken(user, refreshToken);

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpiration(),
                user.getId(),
                user.getEmail(),
                user.getRole());
    }

    @Override
    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {

        String rawToken = request.getRefreshToken().trim();
        String tokenHash = jwtService.hashToken(rawToken);

        RefreshTokenRecord oldRecord =
                refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                        .orElseThrow(this::unauthorizedRefreshToken);

        if (oldRecord.getRevokedAt() != null
                || !oldRecord.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw unauthorizedRefreshToken();
        }

        try {
            if (!"REFRESH".equals(
                    jwtService.extractTokenType(rawToken))) {
                throw unauthorizedRefreshToken();
            }

            User user = oldRecord.getUser();

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(user.getEmail());

            if (!jwtService.isTokenValid(rawToken, userDetails)) {
                throw unauthorizedRefreshToken();
            }

            String newAccessToken =
                    jwtService.generateAccessToken(userDetails);

            String newRefreshToken =
                    jwtService.generateRefreshToken(userDetails);

            // Revoke the old token and store its replacement atomically.
            oldRecord.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(oldRecord);

            saveRefreshToken(user, newRefreshToken);

            return new LoginResponse(
                    newAccessToken,
                    newRefreshToken,
                    "Bearer",
                    jwtService.getAccessTokenExpiration(),
                    user.getId(),
                    user.getEmail(),
                    user.getRole());

        } catch (JwtException ex) {
            throw unauthorizedRefreshToken();
        }
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {

        String rawToken = request.getRefreshToken().trim();
        String tokenHash = jwtService.hashToken(rawToken);

        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .ifPresent(record -> {
                    if (record.getRevokedAt() == null) {
                        record.setRevokedAt(LocalDateTime.now());
                        refreshTokenRepository.save(record);
                    }
                });
    }

    private void saveRefreshToken(User user, String rawToken) {

        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setUser(user);
        record.setTokenHash(jwtService.hashToken(rawToken));
        record.setExpiresAt(
                LocalDateTime.now().plusNanos(
                        jwtService.getRefreshTokenExpiration() * 1_000_000L));

        refreshTokenRepository.save(record);
    }

    private ResponseStatusException unauthorizedRefreshToken() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid, expired, or revoked");
    }
}