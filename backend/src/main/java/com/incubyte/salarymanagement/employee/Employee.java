package com.incubyte.salarymanagement.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    private UUID id;

    @Column(name = "employee_number", nullable = false, length = 32, unique = true)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "country_code", nullable = false, length = 2)
    @JdbcTypeCode(Types.CHAR)
    private String countryCode;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(name = "job_title", nullable = false, length = 120)
    private String jobTitle;

    @Column(name = "currency_code", nullable = false, length = 3)
    @JdbcTypeCode(Types.CHAR)
    private String currencyCode;

    @Column(name = "base_salary", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", nullable = false, length = 16)
    private SalaryPeriod salaryPeriod;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() { }

    public Employee(String employeeNumber, String firstName, String lastName, String email,
                    String countryCode, String department, String jobTitle, BigDecimal baseSalary,
                    String currencyCode, SalaryPeriod salaryPeriod) {
        this.id = UUID.randomUUID();
        updateProfile(employeeNumber, firstName, lastName, email, countryCode, department, jobTitle);
        updateCompensation(baseSalary, currencyCode, salaryPeriod);
    }

    public void updateProfile(String employeeNumber, String firstName, String lastName, String email,
                              String countryCode, String department, String jobTitle) {
        this.employeeNumber = employeeNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.countryCode = countryCode;
        this.department = department;
        this.jobTitle = jobTitle;
    }

    public void updateCompensation(BigDecimal amount, String currencyCode, SalaryPeriod salaryPeriod) {
        this.baseSalary = amount;
        this.currencyCode = currencyCode;
        this.salaryPeriod = salaryPeriod;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmployeeNumber() { return employeeNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getCountryCode() { return countryCode; }
    public String getDepartment() { return department; }
    public String getJobTitle() { return jobTitle; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public String getCurrencyCode() { return currencyCode; }
    public SalaryPeriod getSalaryPeriod() { return salaryPeriod; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
