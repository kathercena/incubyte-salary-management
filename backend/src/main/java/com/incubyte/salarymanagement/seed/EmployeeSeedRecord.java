package com.incubyte.salarymanagement.seed;

import com.incubyte.salarymanagement.employee.SalaryPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EmployeeSeedRecord(
        UUID id,
        String employeeNumber,
        String firstName,
        String lastName,
        String email,
        String countryCode,
        String department,
        String jobTitle,
        BigDecimal baseSalary,
        String currencyCode,
        SalaryPeriod salaryPeriod,
        Instant createdAt,
        Instant updatedAt) { }
