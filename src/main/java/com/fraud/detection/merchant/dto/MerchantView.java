package com.fraud.detection.merchant.dto;

import java.math.BigDecimal;

public record MerchantView(
        Long id,
        String name,
        String email,
        String phone,
        Long categoryId,
        String categoryCode, // model string, e.g. "grocery_pos"
        String categoryDisplayName, // friendly label
        BigDecimal lat,
        BigDecimal lon,
        boolean active) {
}