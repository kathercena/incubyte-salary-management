package com.incubyte.salarymanagement.employee;

import java.util.UUID;

public record CompensationResponse(UUID employeeId, String amount, String currencyCode,
                                  SalaryPeriod salaryPeriod, long version) { }
