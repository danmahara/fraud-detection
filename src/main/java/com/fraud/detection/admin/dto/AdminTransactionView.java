package com.fraud.detection.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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
                List<String> flagReasons,
                OffsetDateTime transactionTime) {
}