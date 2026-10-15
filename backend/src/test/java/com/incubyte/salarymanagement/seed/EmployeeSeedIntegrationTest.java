package com.incubyte.salarymanagement.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incubyte.salarymanagement.employee.CompensationUpdateRequest;
import com.incubyte.salarymanagement.employee.EmployeeCreateRequest;
import com.incubyte.salarymanagement.employee.EmployeeRepository;
import com.incubyte.salarymanagement.employee.SalaryPeriod;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "HR_MANAGER_USERNAME=test-hr-manager",
        "HR_MANAGER_PASSWORD=test-only-password"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EmployeeSeedIntegrationTest {
    private static final String HR_USERNAME = "test-hr-manager";
    private static final String HR_PASSWORD = "test-only-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("salary_seed_test")
            .withUsername("seed_test_user")
            .withPassword("seed_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired EmployeeSeedDataset dataset;
    @Autowired EmployeeSeedService seedService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Validator validator;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationContext applicationContext;
    @Autowired EmployeeRepository employeeRepository;

    @BeforeEach
    void clearIsolatedDatabase() {
        jdbcTemplate.update("DELETE FROM salary_change_audit");
        jdbcTemplate.update("DELETE FROM employees");
    }

    @Test
    void ordinaryApplicationStartupDoesNotRegisterTheSeedCommand() {
        assertThat(applicationContext.containsBean("employeeSeedCommand")).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Long.class)).isZero();
    }

    @Test
    void generatedDatasetIsDeterministicAndHasExplicitCountryCurrencyAndPeriodDistributions() {
        List<EmployeeSeedRecord> first = dataset.generate();
        List<EmployeeSeedRecord> second = dataset.generate();

        assertThat(first).hasSize(10_000).isEqualTo(second);
        assertThat(first.stream().map(EmployeeSeedRecord::id).distinct()).hasSize(10_000);
        assertThat(first.stream().map(EmployeeSeedRecord::employeeNumber).distinct()).hasSize(10_000);
        assertThat(first.stream().map(EmployeeSeedRecord::email).distinct()).hasSize(10_000);
        assertThat(first.stream().map(EmployeeSeedRecord::countryCode).distinct()).hasSize(14);
        assertThat(first.stream().map(EmployeeSeedRecord::currencyCode).distinct()).hasSize(13);
        assertThat(first.stream().map(EmployeeSeedRecord::department).distinct()).hasSize(8);
        assertThat(first.stream().map(EmployeeSeedRecord::salaryPeriod).distinct())
                .containsExactlyInAnyOrder(SalaryPeriod.values());
        assertThat(first.stream().map(EmployeeSeedRecord::createdAt).distinct()).hasSize(10_000);
        assertThat(first).allSatisfy(record -> {
            assertThat(record.createdAt()).isEqualTo(record.updatedAt());
            assertThat(record.baseSalary().scale()).isEqualTo(4);
            assertThat(record.baseSalary()).isPositive();
            assertThat(validator.validate(new EmployeeCreateRequest(record.employeeNumber(), record.firstName(),
                    record.lastName(), record.email(), record.countryCode(), record.department(), record.jobTitle(),
                    record.baseSalary(), record.currencyCode(), record.salaryPeriod()))).isEmpty();
        });
    }

    @Test
    void insertsExactlyTenThousandRowsWithDatabaseEnforcedUniquenessAndBalancedPeriods() {
        EmployeeSeedResult result = seedService.seed();

        assertThat(result).isEqualTo(new EmployeeSeedResult(10_000, false));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Long.class)).isEqualTo(10_000L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT id) FROM employees", Long.class)).isEqualTo(10_000L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT employee_number) FROM employees", Long.class))
                .isEqualTo(10_000L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT email) FROM employees", Long.class))
                .isEqualTo(10_000L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT country_code) FROM employees", Long.class))
                .isEqualTo(14L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT currency_code) FROM employees", Long.class))
                .isEqualTo(13L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT department) FROM employees", Long.class))
                .isEqualTo(8L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees WHERE base_salary <= 0", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees WHERE created_at <> updated_at", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees WHERE salary_period NOT IN ('ANNUAL', 'MONTHLY', 'HOURLY')",
                Long.class)).isZero();

        var periodCounts = jdbcTemplate.query("SELECT salary_period, COUNT(*) AS rows FROM employees GROUP BY salary_period",
                (resultSet, rowNumber) -> resultSet.getString("salary_period") + ":" + resultSet.getLong("rows"));
        assertThat(periodCounts).containsExactlyInAnyOrder("ANNUAL:3334", "MONTHLY:3333", "HOURLY:3333");
    }

    @Test
    void exactRerunIsANoopAndDoesNotDuplicateOrReplaceTheDataset() {
        assertThat(seedService.seed().alreadyComplete()).isFalse();
        EmployeeSeedResult rerun = seedService.seed();

        assertThat(rerun).isEqualTo(new EmployeeSeedResult(10_000, true));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Long.class)).isEqualTo(10_000L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM salary_change_audit", Long.class)).isZero();
        assertThat(employeeRepository.findAllById(dataset.generate().stream().map(EmployeeSeedRecord::id).toList()))
                .hasSize(10_000);
    }

    @Test
    void partialSeedFailsSafelyWithoutChangingExistingRows() {
        EmployeeSeedRecord existing = dataset.generate().get(0);
        insertEmployee(existing);

        assertThatThrownBy(seedService::seed)
                .isInstanceOf(EmployeeSeedConflictException.class)
                .hasMessageContaining("complete expected dataset is not present");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM employees WHERE id = ?", String.class, existing.id()))
                .isEqualTo(existing.email());
    }

    @Test
    void conflictingUniqueIdentifierFailsBeforeAnySeedRowsAreInserted() {
        EmployeeSeedRecord first = dataset.generate().get(0);
        jdbcTemplate.update("""
                INSERT INTO employees (id, employee_number, first_name, last_name, email, country_code,
                    department, job_title, currency_code, base_salary, salary_period, version, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, 'Unrelated', 'Employee', 'unrelated@example.test', 'US',
                    'Operations', 'Analyst', 'USD', 50000.0000, 'ANNUAL', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, first.employeeNumber());

        assertThatThrownBy(seedService::seed)
                .isInstanceOf(EmployeeSeedConflictException.class)
                .hasMessageContaining("conflict with existing employees");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees WHERE employee_number LIKE 'SYN-%'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void databaseFailureDuringBatchRollsBackEveryInsertedEmployee() {
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_seed_row() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.employee_number = 'SYN-05000' THEN
                        RAISE EXCEPTION 'intentional isolated seed test failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbcTemplate.execute("CREATE TRIGGER reject_seed_row_before_insert BEFORE INSERT ON employees "
                + "FOR EACH ROW EXECUTE FUNCTION reject_seed_row()");
        try {
            assertThatThrownBy(seedService::seed).isInstanceOf(RuntimeException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Long.class)).isZero();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_seed_row_before_insert ON employees");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_seed_row()");
        }
    }

    @Test
    void seededEmployeeAndSubsequentCompensationAuditAreAvailableThroughTheProtectedApi() throws Exception {
        EmployeeSeedRecord record = dataset.generate().get(0);
        seedService.seed();

        String listResponse = mockMvc.perform(get("/api/employees").with(httpBasic(HR_USERNAME, HR_PASSWORD))
                        .param("employeeNumber", record.employeeNumber()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode listed = objectMapper.readTree(listResponse).path("items").get(0);
        assertThat(listed.path("id").asText()).isEqualTo(record.id().toString());
        assertThat(listed.path("currencyCode").asText()).isEqualTo(record.currencyCode());
        assertThat(listed.path("salaryPeriod").asText()).isEqualTo(record.salaryPeriod().name());

        String historyUrl = "/api/employees/" + record.id() + "/compensation/history";
        String initialHistory = mockMvc.perform(get(historyUrl).with(httpBasic(HR_USERNAME, HR_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(initialHistory).path("totalElements").asLong()).isZero();

        var update = new CompensationUpdateRequest(record.baseSalary().add(new BigDecimal("1.0000")),
                record.currencyCode(), record.salaryPeriod(), 0L);
        mockMvc.perform(put("/api/employees/{id}/compensation", record.id())
                        .with(httpBasic(HR_USERNAME, HR_PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isOk());
        String updatedHistory = mockMvc.perform(get(historyUrl).with(httpBasic(HR_USERNAME, HR_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(updatedHistory).path("totalElements").asLong()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM salary_change_audit WHERE employee_id = ?",
                Long.class, record.id())).isEqualTo(1L);
    }

    private void insertEmployee(EmployeeSeedRecord employee) {
        jdbcTemplate.update("""
                        INSERT INTO employees (id, employee_number, first_name, last_name, email, country_code,
                            department, job_title, currency_code, base_salary, salary_period, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                employee.id(), employee.employeeNumber(), employee.firstName(), employee.lastName(), employee.email(),
                employee.countryCode(), employee.department(), employee.jobTitle(), employee.currencyCode(),
                employee.baseSalary(), employee.salaryPeriod().name(),
                java.sql.Timestamp.from(employee.createdAt()), java.sql.Timestamp.from(employee.updatedAt()));
    }
}
