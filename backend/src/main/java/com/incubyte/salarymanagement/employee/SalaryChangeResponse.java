package com.incubyte.salarymanagement.employee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SalaryChangeResponse(UUID id, UUID employeeId, BigDecimal previousAmount,
                                   String previousCurrencyCode, SalaryPeriod previousSalaryPeriod,
                                   BigDecimal newAmount, String newCurrencyCode, SalaryPeriod newSalaryPeriod,
                                   String changedBy, Instant changedAt) { }
