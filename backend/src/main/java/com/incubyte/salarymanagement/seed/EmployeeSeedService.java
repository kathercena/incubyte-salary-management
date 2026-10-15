package com.incubyte.salarymanagement.seed;

import com.incubyte.salarymanagement.employee.Employee;
import com.incubyte.salarymanagement.employee.EmployeeRepository;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeSeedService {
    private static final int BATCH_SIZE = 500;
    private static final String INSERT_EMPLOYEE = """
            INSERT INTO employees (id, employee_number, first_name, last_name, email, country_code,
                department, job_title, currency_code, base_salary, salary_period, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
            """;

    private final EmployeeRepository employeeRepository;
    private final EmployeeSeedDataset dataset;
    private final JdbcTemplate jdbcTemplate;

    public EmployeeSeedService(EmployeeRepository employeeRepository, EmployeeSeedDataset dataset,
                               JdbcTemplate jdbcTemplate) {
        this.employeeRepository = employeeRepository;
        this.dataset = dataset;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public EmployeeSeedResult seed() {
        List<EmployeeSeedRecord> expected = dataset.generate();
        List<Employee> rowsWithExpectedIds = employeeRepository.findAllById(
                expected.stream().map(EmployeeSeedRecord::id).toList());
        if (!rowsWithExpectedIds.isEmpty()) {
            if (isExactCompleteDataset(expected, rowsWithExpectedIds)) {
                return new EmployeeSeedResult(EmployeeSeedDataset.EMPLOYEE_COUNT, true);
            }
            throw new EmployeeSeedConflictException(
                    "Seed identifiers already exist, but the complete expected dataset is not present; no rows were changed.");
        }

        List<Employee> identifierConflicts = employeeRepository.findAllByEmployeeNumberInOrEmailIn(
                expected.stream().map(EmployeeSeedRecord::employeeNumber).toList(),
                expected.stream().map(EmployeeSeedRecord::email).toList());
        if (!identifierConflicts.isEmpty()) {
            throw new EmployeeSeedConflictException(
                    "Seed employee numbers or email addresses conflict with existing employees; no rows were changed.");
        }

        try {
            jdbcTemplate.batchUpdate(INSERT_EMPLOYEE, expected, BATCH_SIZE, (statement, employee) -> {
                statement.setObject(1, employee.id());
                statement.setString(2, employee.employeeNumber());
                statement.setString(3, employee.firstName());
                statement.setString(4, employee.lastName());
                statement.setString(5, employee.email());
                statement.setString(6, employee.countryCode());
                statement.setString(7, employee.department());
                statement.setString(8, employee.jobTitle());
                statement.setString(9, employee.currencyCode());
                statement.setBigDecimal(10, employee.baseSalary());
                statement.setString(11, employee.salaryPeriod().name());
                statement.setTimestamp(12, Timestamp.from(employee.createdAt()));
                statement.setTimestamp(13, Timestamp.from(employee.updatedAt()));
            });
        } catch (DataIntegrityViolationException exception) {
            throw new EmployeeSeedConflictException(
                    "The seed could not be inserted because an employee constraint was violated; the transaction was rolled back.");
        }
        return new EmployeeSeedResult(EmployeeSeedDataset.EMPLOYEE_COUNT, false);
    }

    private boolean isExactCompleteDataset(List<EmployeeSeedRecord> expected, List<Employee> actual) {
        if (actual.size() != expected.size()) {
            return false;
        }
        Map<java.util.UUID, EmployeeSeedRecord> byId = expected.stream()
                .collect(Collectors.toMap(EmployeeSeedRecord::id, record -> record));
        Map<java.util.UUID, Employee> actualById = new HashMap<>();
        actual.forEach(employee -> actualById.put(employee.getId(), employee));
        return byId.entrySet().stream().allMatch(entry -> matches(entry.getValue(), actualById.get(entry.getKey())));
    }

    private boolean matches(EmployeeSeedRecord expected, Employee actual) {
        return actual != null
                && expected.employeeNumber().equals(actual.getEmployeeNumber())
                && expected.firstName().equals(actual.getFirstName())
                && expected.lastName().equals(actual.getLastName())
                && expected.email().equals(actual.getEmail())
                && expected.countryCode().equals(actual.getCountryCode())
                && expected.department().equals(actual.getDepartment())
                && expected.jobTitle().equals(actual.getJobTitle())
                && expected.baseSalary().compareTo(actual.getBaseSalary()) == 0
                && expected.currencyCode().equals(actual.getCurrencyCode())
                && expected.salaryPeriod() == actual.getSalaryPeriod()
                && actual.getVersion() == 0
                && expected.createdAt().equals(actual.getCreatedAt())
                && expected.updatedAt().equals(actual.getUpdatedAt());
    }
}
