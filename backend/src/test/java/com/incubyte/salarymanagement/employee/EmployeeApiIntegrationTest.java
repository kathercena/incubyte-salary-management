package com.incubyte.salarymanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "HR_MANAGER_USERNAME=test-hr-manager",
        "HR_MANAGER_PASSWORD=test-only-password",
        "APP_CORS_ALLOWED_ORIGIN=http://localhost:4200"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EmployeeApiIntegrationTest {
    private static final String HR_USERNAME = "test-hr-manager";
    private static final String HR_PASSWORD = "test-only-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("salary_management_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Flyway flyway;

    @BeforeEach
    void clearIsolatedDatabase() {
        jdbcTemplate.update("DELETE FROM salary_change_audit");
        jdbcTemplate.update("DELETE FROM employees");
    }

    @Test
    void appliesBothFlywayMigrationsToTheIsolatedPostgresDatabase() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.salary_change_audit') IS NOT NULL", Boolean.class))
                .isTrue();
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendAndPreservesAuthenticatedApiAccess() throws Exception {
        mockMvc.perform(options("/api/employees")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));

        mockMvc.perform(options("/api/employees")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mockMvc.perform(get("/api/employees").with(hrAuth())
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void createsReadsAndUpdatesCurrentCompensationWithAnAuditEntry() throws Exception {
        JsonNode created = createEmployee("EMP-100", "one@example.test", "Jane", "Smith", "US", "Finance",
                "Analyst", "75000.1250", "USD", "ANNUAL");
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(created.get("version").asLong()).isZero();
        assertThat(created.get("amount").asText()).isEqualTo("75000.1250");

        mockMvc.perform(get("/api/employees/{id}", id).with(hrAuth()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/{id}/compensation", id).with(hrAuth()))
                .andExpect(status().isOk());

        CompensationUpdateRequest update = new CompensationUpdateRequest(
                new BigDecimal("81000.5000"), "USD", SalaryPeriod.ANNUAL, 0L);
        MvcResult updated = mockMvc.perform(put("/api/employees/{id}/compensation", id).with(hrAuth())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isOk()).andReturn();
        JsonNode compensation = objectMapper.readTree(updated.getResponse().getContentAsByteArray());
        assertThat(compensation.get("amount").asText()).isEqualTo("81000.5000");
        assertThat(compensation.get("currencyCode").asText()).isEqualTo("USD");
        assertThat(compensation.get("version").asLong()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT base_salary FROM employees WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("81000.5000");
        assertThat(jdbcTemplate.queryForObject("SELECT changed_by FROM salary_change_audit WHERE employee_id = ?",
                String.class, id)).isEqualTo(HR_USERNAME);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM salary_change_audit WHERE employee_id = ?",
                Integer.class, id)).isEqualTo(1);

        MvcResult history = mockMvc.perform(get("/api/employees/{id}/compensation/history", id).with(hrAuth()))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(history.getResponse().getContentAsByteArray()).get("totalElements").asLong())
                .isEqualTo(1L);

        EmployeeUpdateRequest profileUpdate = new EmployeeUpdateRequest("EMP-100", "Jane", "Smith",
                "one@example.test", "US", "People", "Senior Analyst", 1L);
        MvcResult profile = mockMvc.perform(put("/api/employees/{id}", id).with(hrAuth())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(profileUpdate)))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(profile.getResponse().getContentAsByteArray()).get("version").asLong())
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM salary_change_audit WHERE employee_id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidAndMalformedRequestsWithoutPersistingEmployees() throws Exception {
        postJson("{\"employeeNumber\":\"EMP-1\"}").andExpect(status().isBadRequest());
        postJson("{malformed-json").andExpect(status().isBadRequest());
        assertInvalidCreate("-1.00", "USD", "ANNUAL");
        assertInvalidCreate("1.12345", "USD", "ANNUAL");
        assertInvalidCreate("1000000000000000.0000", "USD", "ANNUAL");
        assertInvalidCreate("10.00", "ZZZ", "ANNUAL");
        assertInvalidCreate("10.00", "USD", "WEEKLY");

        String invalidCountry = objectMapper.writeValueAsString(new EmployeeCreateRequest("EMP-X", "Alex", "Doe",
                "alex@example.test", "ZZ", "Finance", "Analyst", new BigDecimal("10.00"), "USD",
                SalaryPeriod.ANNUAL));
        mockMvc.perform(post("/api/employees").with(hrAuth()).contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCountry)).andExpect(status().isBadRequest());
        assertThat(employeeCount()).isZero();
    }

    @Test
    void acceptsZeroAndAllConfiguredSalaryPeriodsWithoutCurrencyAggregation() throws Exception {
        JsonNode zero = createEmployee("EMP-ZERO", "zero@example.test", "Zero", "Amount", "US", "HR",
                "Analyst", "0.0000", "USD", "ANNUAL");
        JsonNode monthly = createEmployee("EMP-MONTH", "month@example.test", "Monthly", "Amount", "GB", "Finance",
                "Analyst", "100.0000", "GBP", "MONTHLY");
        JsonNode hourly = createEmployee("EMP-HOUR", "hour@example.test", "Hourly", "Amount", "CA", "Finance",
                "Analyst", "25.0000", "CAD", "HOURLY");
        assertThat(zero.get("amount").asText()).isEqualTo("0.0000");
        assertThat(monthly.get("salaryPeriod").asText()).isEqualTo("MONTHLY");
        assertThat(hourly.get("salaryPeriod").asText()).isEqualTo("HOURLY");
        assertThat(employeeCount()).isEqualTo(3);
    }

    @Test
    void preservesMaximumSupportedMoneyPrecisionAcrossResponsesAndAuditHistory() throws Exception {
        JsonNode created = createEmployee("EMP-MAX", "maximum@example.test", "Maximum", "Amount", "US", "Finance",
                "Analyst", "999999999999999.9999", "USD", "ANNUAL");
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(created.get("amount").isTextual()).isTrue();
        assertThat(created.get("amount").asText()).isEqualTo("999999999999999.9999");

        MvcResult updated = mockMvc.perform(put("/api/employees/{id}/compensation", id).with(hrAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"999999999999999.9998\",\"currencyCode\":\"USD\","
                                + "\"salaryPeriod\":\"ANNUAL\",\"version\":0}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode compensation = objectMapper.readTree(updated.getResponse().getContentAsByteArray());
        assertThat(compensation.get("amount").asText()).isEqualTo("999999999999999.9998");

        MvcResult history = mockMvc.perform(get("/api/employees/{id}/compensation/history", id).with(hrAuth()))
                .andExpect(status().isOk()).andReturn();
        JsonNode change = objectMapper.readTree(history.getResponse().getContentAsByteArray())
                .get("items").get(0);
        assertThat(change.get("previousAmount").asText()).isEqualTo("999999999999999.9999");
        assertThat(change.get("newAmount").asText()).isEqualTo("999999999999999.9998");
        assertThat(jdbcTemplate.queryForObject("SELECT base_salary FROM employees WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("999999999999999.9998");
    }

    @Test
    void databaseUniquenessConflictsReturn409AndPreserveOneRow() throws Exception {
        createEmployee("EMP-DUP", "duplicate@example.test", "A", "One", "US", "Finance", "Analyst",
                "1", "USD", "ANNUAL");
        postEmployeeRequest("EMP-DUP", "other@example.test", "B", "Two", "GB", "HR", "Partner", "2", "GBP")
                .andExpect(status().isConflict());
        postEmployeeRequest("EMP-OTHER", "duplicate@example.test", "B", "Two", "GB", "HR", "Partner", "2", "GBP")
                .andExpect(status().isConflict());
        assertThat(employeeCount()).isEqualTo(1);
    }

    @Test
    void searchPaginatesFiltersAndUsesAllowlistedStableSorting() throws Exception {
        JsonNode smith = createEmployee("EMP-002", "smith@example.test", "Jane", "Smith", "US", "Engineering",
                "Engineer", "70000", "USD", "ANNUAL");
        JsonNode sameLastName = createEmployee("EMP-001", "jane@example.test", "Alex", "Smith", "US", "Finance",
                "Analyst", "65000", "USD", "MONTHLY");
        createEmployee("EMP-003", "adams@example.test", "Alice", "Adams", "GB", "Engineering", "Analyst",
                "50000", "GBP", "ANNUAL");

        MvcResult page = mockMvc.perform(get("/api/employees?page=0&size=2&sort=lastName,asc").with(hrAuth()))
                .andExpect(status().isOk()).andReturn();
        JsonNode pageBody = objectMapper.readTree(page.getResponse().getContentAsByteArray());
        assertThat(pageBody.get("items").size()).isEqualTo(2);
        assertThat(pageBody.get("totalElements").asLong()).isEqualTo(3L);
        assertThat(pageBody.get("items").get(0).get("lastName").asText()).isEqualTo("Adams");

        MvcResult filtered = mockMvc.perform(get("/api/employees")
                        .param("name", "Jane Smith")
                        .param("employeeNumber", "EMP-002")
                        .param("countryCode", "US")
                        .param("department", "Engineering")
                        .with(hrAuth()))
                .andExpect(status().isOk()).andReturn();
        JsonNode filteredBody = objectMapper.readTree(filtered.getResponse().getContentAsByteArray());
        assertThat(filteredBody.get("totalElements").asLong()).isEqualTo(1L);
        assertThat(filteredBody.get("items").get(0).get("id").asText()).isEqualTo(smith.get("id").asText());

        MvcResult tieSorted = mockMvc.perform(get("/api/employees?name=Smith&sort=lastName,asc&size=10").with(hrAuth()))
                .andExpect(status().isOk()).andReturn();
        List<String> ids = new ArrayList<>();
        objectMapper.readTree(tieSorted.getResponse().getContentAsByteArray()).get("items")
                .forEach(item -> ids.add(item.get("id").asText()));
        List<String> sortedIds = ids.stream().sorted().toList();
        assertThat(ids).containsExactlyElementsOf(sortedIds);
        assertThat(ids).contains(sameLastName.get("id").asText());

        MvcResult beyondLast = mockMvc.perform(get("/api/employees?page=99&size=2").with(hrAuth()))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(beyondLast.getResponse().getContentAsByteArray()).get("items")).isEmpty();
        mockMvc.perform(get("/api/employees?page=-1").with(hrAuth())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/employees?size=101").with(hrAuth())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/employees?sort=baseSalary,asc").with(hrAuth())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/employees?sort=lastName,sideways").with(hrAuth())).andExpect(status().isBadRequest());
    }

    @Test
    void missingAndMalformedEmployeeIdsReturnClientErrors() throws Exception {
        mockMvc.perform(get("/api/employees/{id}", UUID.randomUUID()).with(hrAuth()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employees/not-a-uuid").with(hrAuth()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/employees/{id}/compensation/history", UUID.randomUUID()).with(hrAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedAndInvalidBasicCredentials() throws Exception {
        mockMvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/employees").with(httpBasic("wrong-user", "wrong-password")))
                .andExpect(status().isUnauthorized());
        assertThat(employeeCount()).isZero();
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void rejectsAuthenticatedUsersWithoutTheHrManagerRole() throws Exception {
        mockMvc.perform(get("/api/employees")).andExpect(status().isForbidden());
    }

    @Test
    void concurrentDuplicateCreatesLeaveOneRowAndReturnOneConflict() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentCreate(ready, start));
            Future<Integer> second = executor.submit(() -> concurrentCreate(ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(employeeCount()).isEqualTo(1);
    }

    @Test
    void staleConcurrentCompensationUpdateConflictsAndOnlyAuditsWinningChange() throws Exception {
        JsonNode created = createEmployee("EMP-RACE", "race@example.test", "Race", "Test", "US", "Finance",
                "Analyst", "50000", "USD", "ANNUAL");
        UUID id = UUID.fromString(created.get("id").asText());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentCompensationUpdate(id, "60000", ready, start));
            Future<Integer> second = executor.submit(() -> concurrentCompensationUpdate(id, "70000", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM employees WHERE id = ?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM salary_change_audit WHERE employee_id = ?",
                Integer.class, id)).isEqualTo(1);
        BigDecimal finalAmount = jdbcTemplate.queryForObject("SELECT base_salary FROM employees WHERE id = ?",
                BigDecimal.class, id);
        assertThat(finalAmount.compareTo(new BigDecimal("60000")) == 0
                || finalAmount.compareTo(new BigDecimal("70000")) == 0).isTrue();
    }

    @Test
    void rollsBackCurrentCompensationWhenAuditPersistenceFails() throws Exception {
        JsonNode created = createEmployee("EMP-ROLLBACK", "rollback@example.test", "Roll", "Back", "US", "Finance",
                "Analyst", "50000", "USD", "ANNUAL");
        UUID id = UUID.fromString(created.get("id").asText());
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_salary_audit_insert() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced isolated test failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_salary_audit_insert_trigger
                BEFORE INSERT ON salary_change_audit
                FOR EACH ROW EXECUTE FUNCTION reject_salary_audit_insert()
                """);
        try {
            CompensationUpdateRequest update = new CompensationUpdateRequest(
                    new BigDecimal("90000"), "USD", SalaryPeriod.ANNUAL, 0L);
            mockMvc.perform(put("/api/employees/{id}/compensation", id).with(hrAuth())
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(update)))
                    .andExpect(status().isInternalServerError());
            assertThat(jdbcTemplate.queryForObject("SELECT base_salary FROM employees WHERE id = ?",
                    BigDecimal.class, id)).isEqualByComparingTo("50000");
            assertThat(jdbcTemplate.queryForObject("SELECT version FROM employees WHERE id = ?",
                    Long.class, id)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM salary_change_audit WHERE employee_id = ?",
                    Integer.class, id)).isZero();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_salary_audit_insert_trigger ON salary_change_audit");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_salary_audit_insert()");
        }
    }

    @Test
    void independentEmployeeUpdatesCanCommitConcurrently() throws Exception {
        JsonNode first = createEmployee("EMP-C1", "c1@example.test", "One", "User", "US", "Finance", "Analyst",
                "1000", "USD", "ANNUAL");
        JsonNode second = createEmployee("EMP-C2", "c2@example.test", "Two", "User", "GB", "Engineering", "Engineer",
                "2000", "GBP", "MONTHLY");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> concurrentCompensationUpdate(
                    UUID.fromString(first.get("id").asText()), "1100", ready, start));
            Future<Integer> secondResult = executor.submit(() -> concurrentCompensationUpdate(
                    UUID.fromString(second.get("id").asText()), "2200", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(firstResult.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(secondResult.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM salary_change_audit", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT base_salary FROM employees WHERE id = ?", BigDecimal.class,
                UUID.fromString(first.get("id").asText()))).isEqualByComparingTo("1100");
        assertThat(jdbcTemplate.queryForObject("SELECT base_salary FROM employees WHERE id = ?", BigDecimal.class,
                UUID.fromString(second.get("id").asText()))).isEqualByComparingTo("2200");
    }

    private JsonNode createEmployee(String employeeNumber, String email, String firstName, String lastName,
                                    String country, String department, String jobTitle,
                                    String amount, String currency, String period) throws Exception {
        MvcResult result = postEmployeeRequest(employeeNumber, email, firstName, lastName, country, department,
                jobTitle, amount, currency, period)
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.ResultActions postEmployeeRequest(
            String employeeNumber, String email, String firstName, String lastName, String country,
            String department, String jobTitle, String amount, String currency) throws Exception {
        return postEmployeeRequest(employeeNumber, email, firstName, lastName, country, department,
                jobTitle, amount, currency, "ANNUAL");
    }

    private org.springframework.test.web.servlet.ResultActions postEmployeeRequest(
            String employeeNumber, String email, String firstName, String lastName, String country,
            String department, String jobTitle, String amount, String currency, String period) throws Exception {
        EmployeeCreateRequest request = new EmployeeCreateRequest(employeeNumber, firstName, lastName, email,
                country, department, jobTitle, new BigDecimal(amount), currency, SalaryPeriod.valueOf(period));
        return mockMvc.perform(post("/api/employees").with(hrAuth()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post("/api/employees").with(hrAuth()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertInvalidCreate(String amount, String currency, String period) throws Exception {
        String body = """
                {"employeeNumber":"EMP-INVALID","firstName":"A","lastName":"B","email":"invalid@example.test",
                 "countryCode":"US","department":"HR","jobTitle":"Analyst","amount":%s,
                 "currencyCode":"%s","salaryPeriod":"%s"}
                """.formatted(amount, currency, period);
        postJson(body).andExpect(status().isBadRequest());
    }

    private int concurrentCreate(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent create barrier timed out.");
        }
        MvcResult result = postEmployeeRequest("EMP-RACE-DUP", "race-duplicate@example.test", "Race", "Dup",
                "US", "Finance", "Analyst", "100", "USD").andReturn();
        return result.getResponse().getStatus();
    }

    private int concurrentCompensationUpdate(UUID id, String amount, CountDownLatch ready,
                                             CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent update barrier timed out.");
        }
        CompensationUpdateRequest request = new CompensationUpdateRequest(
                new BigDecimal(amount), "USD", SalaryPeriod.ANNUAL, 0L);
        MvcResult result = mockMvc.perform(put("/api/employees/{id}/compensation", id).with(hrAuth())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private RequestPostProcessor hrAuth() {
        return httpBasic(HR_USERNAME, HR_PASSWORD);
    }

    private int employeeCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Integer.class);
    }
}
