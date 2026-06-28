package com.fraud.detection.account.dto;

import java.math.BigDecimal;

public record AccountView(
        Long id,
        String accountNumber,
        BigDecimal balance,
        String status) {
}