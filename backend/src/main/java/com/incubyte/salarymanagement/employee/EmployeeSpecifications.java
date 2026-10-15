package com.incubyte.salarymanagement.employee;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class EmployeeSpecifications {
    private EmployeeSpecifications() { }

    static Specification<Employee> matching(String name, String employeeNumber, String countryCode,
                                             String department) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(name)) {
                String pattern = containsPattern(name);
                var fullName = builder.lower(builder.concat(
                        builder.concat(root.get("firstName"), " "), root.get("lastName")));
                predicates.add(builder.like(fullName, pattern, '\\'));
            }
            if (hasText(employeeNumber)) {
                predicates.add(builder.equal(root.get("employeeNumber"), employeeNumber));
            }
            if (hasText(countryCode)) {
                predicates.add(builder.equal(root.get("countryCode"), countryCode));
            }
            if (hasText(department)) {
                predicates.add(builder.like(builder.lower(root.get("department")), containsPattern(department), '\\'));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String containsPattern(String value) {
        String escaped = value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
