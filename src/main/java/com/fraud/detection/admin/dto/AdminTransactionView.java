package com.fraud.detection.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminTransactionView(
        Long id,
        String userEmail,
        BigDecimal amount,
        String merchant,
        String merchantCategory,
        String channel,
        String status,
        String riskLevel,
        Double fraudScore,
        OffsetDateTime transactionTime) {
}