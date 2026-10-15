package com.incubyte.salarymanagement.employee;

import com.incubyte.salarymanagement.validation.CurrencyCode;
import com.incubyte.salarymanagement.validation.MoneyAmount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CompensationUpdateRequest(
        @NotNull @MoneyAmount BigDecimal amount,
        @NotBlank @CurrencyCode String currencyCode,
        @NotNull SalaryPeriod salaryPeriod,
        @NotNull @PositiveOrZero Long version) { }
