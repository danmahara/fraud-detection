package com.fraud.detection.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fraud.detection.entity.enums.Channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTransactionRequest(
                @NotNull Long accountId,
                @NotNull @Positive BigDecimal amount,
                @NotBlank String merchant,
                String merchantCategory,
                @NotNull Channel channel,
                BigDecimal locationLat,
                BigDecimal locationLon,
                String deviceId,
                List<Double> features // V1..V28; optional (null for manual transactions)
) {
}