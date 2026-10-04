package com.fraud.detection.account.dto;

import java.math.BigDecimal;

import com.fraud.detection.entity.enums.AccountStatus;

public record AccountAdminResponse(
        Long id,
        String accountNumber,
        BigDecimal balance,
        AccountStatus status) {
}