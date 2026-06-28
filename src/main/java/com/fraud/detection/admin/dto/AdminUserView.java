package com.fraud.detection.admin.dto;

import java.time.OffsetDateTime;

public record AdminUserView(
        Long id,
        String name,
        String email,
        String phone,
        String role,
        long transactionCount, // how many transactions this user has
        OffsetDateTime createdAt) {
}