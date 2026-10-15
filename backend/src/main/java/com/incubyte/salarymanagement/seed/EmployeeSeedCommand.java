package com.incubyte.salarymanagement.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class EmployeeSeedCommand implements CommandLineRunner {
    private final EmployeeSeedService seedService;

    public EmployeeSeedCommand(EmployeeSeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(String... args) {
        EmployeeSeedResult result = seedService.seed();
        if (result.alreadyComplete()) {
            System.out.println("Synthetic employee seed is already complete; no rows were changed.");
        } else {
            System.out.printf("Synthetic employee seed inserted %d employees.%n", result.employeeCount());
        }
    }
}
