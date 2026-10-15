package com.incubyte.salarymanagement.seed;

import com.incubyte.salarymanagement.employee.SalaryPeriod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EmployeeSeedDataset {
    public static final int EMPLOYEE_COUNT = 10_000;
    private static final long DATASET_SEED = 0x53414C415259L;
    private static final Instant FIRST_CREATED_AT = Instant.parse("2025-01-01T00:00:00Z");
    private static final String[] FIRST_NAMES = {
        "Alex", "Avery", "Casey", "Cameron", "Drew", "Jordan", "Morgan", "Quinn",
        "Riley", "Robin", "Sam", "Taylor", "Jamie", "Lee", "Kai", "Skyler"
    };
    private static final String[] LAST_NAMES = {
        "Anderson", "Bennett", "Chen", "Diaz", "Evans", "Foster", "Gupta", "Hughes",
        "Ito", "Jones", "Khan", "Lopez", "Martin", "Nguyen", "Patel", "Rossi",
        "Singh", "Taylor", "Wilson", "Zhang"
    };
    private static final String[] DEPARTMENTS = {
        "Engineering", "Finance", "Human Resources", "Legal", "Marketing", "Operations",
        "People", "Sales"
    };
    private static final String[] JOB_TITLES = {
        "Analyst", "Associate", "Coordinator", "Engineer", "Manager", "Product Specialist",
        "Senior Analyst", "Team Lead"
    };
    private static final SalaryPeriod[] PERIODS = SalaryPeriod.values();
    private static final CountryProfile[] COUNTRIES = {
        new CountryProfile("US", "USD", 40_000, 180_000),
        new CountryProfile("GB", "GBP", 30_000, 120_000),
        new CountryProfile("CA", "CAD", 50_000, 180_000),
        new CountryProfile("AU", "AUD", 55_000, 180_000),
        new CountryProfile("DE", "EUR", 35_000, 140_000),
        new CountryProfile("FR", "EUR", 30_000, 100_000),
        new CountryProfile("IN", "INR", 600_000, 3_600_000),
        new CountryProfile("JP", "JPY", 3_500_000, 12_000_000),
        new CountryProfile("BR", "BRL", 50_000, 300_000),
        new CountryProfile("SG", "SGD", 45_000, 180_000),
        new CountryProfile("MX", "MXN", 250_000, 1_200_000),
        new CountryProfile("ZA", "ZAR", 250_000, 1_000_000),
        new CountryProfile("NZ", "NZD", 50_000, 150_000),
        new CountryProfile("CH", "CHF", 70_000, 200_000)
    };

    public List<EmployeeSeedRecord> generate() {
        var records = new java.util.ArrayList<EmployeeSeedRecord>(EMPLOYEE_COUNT);
        for (int sequence = 1; sequence <= EMPLOYEE_COUNT; sequence++) {
            int index = sequence - 1;
            var random = new SplittableRandom(DATASET_SEED + sequence);
            var country = COUNTRIES[index % COUNTRIES.length];
            var period = PERIODS[index % PERIODS.length];
            var annualAmount = BigDecimal.valueOf(random.nextLong(country.minimumAnnual(), country.maximumAnnual() + 1));
            var amount = switch (period) {
                case ANNUAL -> annualAmount;
                case MONTHLY -> annualAmount.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
                case HOURLY -> annualAmount.divide(BigDecimal.valueOf(2_080), 4, RoundingMode.HALF_UP);
            };
            var createdAt = FIRST_CREATED_AT.plusSeconds(sequence * 60L);
            var key = "incubyte-salary-seed-v1:" + sequence;
            records.add(new EmployeeSeedRecord(
                    UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),
                    "SYN-" + "%05d".formatted(sequence),
                    FIRST_NAMES[random.nextInt(FIRST_NAMES.length)],
                    LAST_NAMES[random.nextInt(LAST_NAMES.length)],
                    "employee%05d@seed.example.test".formatted(sequence),
                    country.countryCode(),
                    DEPARTMENTS[random.nextInt(DEPARTMENTS.length)],
                    JOB_TITLES[random.nextInt(JOB_TITLES.length)],
                    amount.setScale(4, RoundingMode.HALF_UP),
                    country.currencyCode(),
                    period,
                    createdAt,
                    createdAt));
        }
        return List.copyOf(records);
    }

    private record CountryProfile(String countryCode, String currencyCode,
                                  long minimumAnnual, long maximumAnnual) { }
}
