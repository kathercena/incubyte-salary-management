package com.incubyte.salarymanagement.employee;

import com.incubyte.salarymanagement.validation.CountryCode;
import com.incubyte.salarymanagement.validation.CurrencyCode;
import com.incubyte.salarymanagement.validation.MoneyAmount;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record EmployeeCreateRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String employeeNumber,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @CountryCode String countryCode,
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Size(max = 120) String jobTitle,
        @NotNull @MoneyAmount BigDecimal amount,
        @NotBlank @CurrencyCode String currencyCode,
        @NotNull SalaryPeriod salaryPeriod) { }
