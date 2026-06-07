package com.fraud.detection.transaction.dto;

import com.fraud.detection.entity.enums.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull Long accountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String merchant,
        String merchantCategory,
        @NotNull Channel channel,
        BigDecimal locationLat,
        BigDecimal locationLon,
        String deviceId) {
}