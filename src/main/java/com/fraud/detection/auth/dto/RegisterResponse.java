package com.fraud.detection.auth.dto;

public record RegisterResponse(
        Long userId,
        String email,
        String message) {
}