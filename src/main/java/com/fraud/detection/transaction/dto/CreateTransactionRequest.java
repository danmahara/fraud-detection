package com.fraud.detection.transaction.dto;

import com.fraud.detection.entity.enums.Channel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateTransactionRequest(
                @NotNull Long accountId,
                @NotNull @Positive BigDecimal amount,

                // Merchant reference — supply ONE of these (id preferred, then phone, then
                // email).
                Long merchantId,
                String merchantPhone,
                String merchantEmail,

                @NotNull Channel channel,
                String deviceId,
                OffsetDateTime transactionTime // optional; null -> server uses now()
) {
}