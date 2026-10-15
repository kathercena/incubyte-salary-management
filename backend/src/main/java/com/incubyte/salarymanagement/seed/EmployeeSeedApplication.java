package com.incubyte.salarymanagement.seed;

import com.incubyte.salarymanagement.SalaryManagementApplication;
import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class EmployeeSeedApplication {
    private EmployeeSeedApplication() { }

    public static void main(String[] args) {
        boolean explicitlyEnabled = Arrays.stream(args)
                .anyMatch(argument -> argument.equals("--app.seed.enabled=true"));
        if (!explicitlyEnabled) {
            throw new IllegalArgumentException("Pass --app.seed.enabled=true to run the synthetic employee seed.");
        }
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(SalaryManagementApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            // Closing the non-web application context lets this one-shot command exit cleanly.
        }
    }
}
