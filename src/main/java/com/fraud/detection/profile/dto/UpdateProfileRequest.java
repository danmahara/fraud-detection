package com.fraud.detection.profile.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotNull BigDecimal homeLat,
        @NotNull BigDecimal homeLon,
        @NotNull LocalDate dob,
        @NotNull String gender, // "M" or "F" (matches the ML model's encoding)
        String deviceId

) {
}