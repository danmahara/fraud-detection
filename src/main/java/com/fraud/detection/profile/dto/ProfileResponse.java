package com.fraud.detection.profile.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfileResponse(
        BigDecimal homeLat,
        BigDecimal homeLon,
        LocalDate dob,
        String gender,
        Integer cityPop,
        boolean profileComplete // true once the scoring-required fields are set
) {
}