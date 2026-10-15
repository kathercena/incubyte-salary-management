package com.incubyte.salarymanagement.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class MoneyAmountValidator implements ConstraintValidator<MoneyAmount, BigDecimal> {
    @Override
    public boolean isValid(BigDecimal amount, ConstraintValidatorContext context) {
        if (amount == null) {
            return true;
        }
        if (amount.signum() < 0 || amount.scale() > 4) {
            return false;
        }
        int integerDigits = Math.max(0, amount.precision() - amount.scale());
        return integerDigits <= 15 && amount.precision() <= 19;
    }
}
