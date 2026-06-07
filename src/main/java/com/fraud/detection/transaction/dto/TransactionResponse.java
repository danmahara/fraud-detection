package com.fraud.detection.transaction.dto;

public record TransactionResponse(
        Long transactionId,
        String status, // PENDING / APPROVED / BLOCKED
        String riskLevel, // GREEN / YELLOW / ORANGE / RED
        String decision // APPROVED / OTP_REQUIRED / DECLINED
) {
}