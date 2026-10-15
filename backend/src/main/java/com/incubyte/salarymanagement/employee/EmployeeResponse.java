package com.incubyte.salarymanagement.employee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EmployeeResponse(UUID id, String employeeNumber, String firstName, String lastName, String email,
                               String countryCode, String department, String jobTitle, BigDecimal amount,
                               String currencyCode, SalaryPeriod salaryPeriod, long version,
                               Instant createdAt, Instant updatedAt) { }
