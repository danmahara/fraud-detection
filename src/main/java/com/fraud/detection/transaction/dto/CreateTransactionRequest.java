package com.fraud.detection.transaction.dto;

import com.fraud.detection.entity.enums.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateTransactionRequest(
        @NotNull Long accountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String merchant,
        @NotBlank String merchantCategory, // must match a trained category, e.g. "grocery_pos"
        @NotNull Channel channel,
        @NotNull BigDecimal merchLat, // where the purchase happened
        @NotNull BigDecimal merchLon,
        String deviceId,
        OffsetDateTime transactionTime // optional; null -> server uses now()
) {
}