package com.incubyte.salarymanagement.employee;

import java.time.Instant;
import java.util.UUID;

public record SalaryChangeResponse(UUID id, UUID employeeId, String previousAmount,
                                   String previousCurrencyCode, SalaryPeriod previousSalaryPeriod,
                                   String newAmount, String newCurrencyCode, SalaryPeriod newSalaryPeriod,
                                   String changedBy, Instant changedAt) { }
