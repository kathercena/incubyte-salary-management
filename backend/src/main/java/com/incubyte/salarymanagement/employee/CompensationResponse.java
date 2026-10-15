package com.incubyte.salarymanagement.employee;

import java.math.BigDecimal;
import java.util.UUID;

public record CompensationResponse(UUID employeeId, BigDecimal amount, String currencyCode,
                                  SalaryPeriod salaryPeriod, long version) { }
