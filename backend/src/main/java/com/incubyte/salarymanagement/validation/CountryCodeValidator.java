package com.incubyte.salarymanagement.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.Locale;
import java.util.Arrays;
import java.util.stream.Collectors;

public class CountryCodeValidator implements ConstraintValidator<CountryCode, String> {
    private static final Set<String> CODES = Arrays.stream(Locale.getISOCountries())
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || CODES.contains(value);
    }
}
