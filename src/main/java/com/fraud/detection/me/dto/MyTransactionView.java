package com.fraud.detection.me.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record MyTransactionView(
        Long id,
        BigDecimal amount,
        String merchant,
        String merchantCategory,
        String channel,
        String status,
        String riskLevel,
        List<String> flagReasons,
        OffsetDateTime transactionTime) {
}