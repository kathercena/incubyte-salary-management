package com.incubyte.salarymanagement.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MoneyAmountValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MoneyAmount {
    String message() default "must be between 0 and 999999999999999.9999 with no more than 4 decimal places";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
