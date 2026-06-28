package com.fraud.detection.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminUserDetailView(
        Long id,
        String name,
        String email,
        String phone,
        String role,
        OffsetDateTime createdAt,

        // Profile (may be null if not onboarded)
        BigDecimal homeLat,
        BigDecimal homeLon,
        LocalDate dob,
        String gender,
        boolean profileComplete,

        // Accounts
        List<AccountSummary> accounts,

        // Recent transactions (latest 10)
        List<AdminTransactionView> recentTransactions) {
    public record AccountSummary(
            Long id,
            String accountNumber,
            BigDecimal balance,
            String status) {
    }
}