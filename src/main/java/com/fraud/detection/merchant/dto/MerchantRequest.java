package com.fraud.detection.merchant.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record MerchantRequest(
        @NotBlank String name,
        @Email String email, // optional, but if present must be valid
        String phone,
        @NotNull Long categoryId, // references categories.id
        @NotNull BigDecimal lat,
        @NotNull BigDecimal lon,
        Boolean active // defaults to true if null
) {
}