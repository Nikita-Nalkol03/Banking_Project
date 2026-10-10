package com.smartbank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.smartbank.entity.Role;
import com.smartbank.entity.User;
import com.smartbank.entity.UserStatus;
import com.smartbank.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${smartbank.admin.email}")
    private String adminEmail;

    @Value("${smartbank.admin.password}")
    private String adminPassword;

    @Value("${smartbank.admin.first-name}")
    private String adminFirstName;

    @Value("${smartbank.admin.last-name}")
    private String adminLastName;

    @Value("${smartbank.admin.phone}")
    private String adminPhone;

    @Override
    public void run(String... args) {

        String email = adminEmail.trim().toLowerCase();

        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        User admin = User.builder()
                .firstName(adminFirstName.trim())
                .lastName(adminLastName.trim())
                .email(email)
                .phone(adminPhone.trim())
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(admin);

        System.out.println(
                "SmartBank ADMIN user initialized: " + email
        );
    }
}