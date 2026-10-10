package com.smartbank.dto;

import com.smartbank.entity.Role;
import com.smartbank.entity.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Role role;
    private UserStatus status;
}