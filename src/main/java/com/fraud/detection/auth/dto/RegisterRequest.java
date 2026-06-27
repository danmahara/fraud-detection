package com.fraud.detection.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @Email @NotBlank String email,
        String phone,
        @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String password) {
}