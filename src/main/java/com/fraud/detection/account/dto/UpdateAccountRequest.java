package com.fraud.detection.account.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import com.fraud.detection.entity.enums.AccountStatus;

public record UpdateAccountRequest(
        @NotNull AccountStatus status,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal balance) {
}
