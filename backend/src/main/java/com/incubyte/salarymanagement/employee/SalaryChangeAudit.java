package com.incubyte.salarymanagement.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "salary_change_audit")
public class SalaryChangeAudit {
    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "previous_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousAmount;

    @Column(name = "previous_currency_code", nullable = false, length = 3)
    @JdbcTypeCode(Types.CHAR)
    private String previousCurrencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_salary_period", nullable = false, length = 16)
    private SalaryPeriod previousSalaryPeriod;

    @Column(name = "new_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal newAmount;

    @Column(name = "new_currency_code", nullable = false, length = 3)
    @JdbcTypeCode(Types.CHAR)
    private String newCurrencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_salary_period", nullable = false, length = 16)
    private SalaryPeriod newSalaryPeriod;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected SalaryChangeAudit() { }

    public SalaryChangeAudit(UUID employeeId, BigDecimal previousAmount, String previousCurrencyCode,
                             SalaryPeriod previousSalaryPeriod, BigDecimal newAmount, String newCurrencyCode,
                             SalaryPeriod newSalaryPeriod, String changedBy) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.previousAmount = previousAmount;
        this.previousCurrencyCode = previousCurrencyCode;
        this.previousSalaryPeriod = previousSalaryPeriod;
        this.newAmount = newAmount;
        this.newCurrencyCode = newCurrencyCode;
        this.newSalaryPeriod = newSalaryPeriod;
        this.changedBy = changedBy;
    }

    @PrePersist
    void beforeInsert() { changedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public BigDecimal getPreviousAmount() { return previousAmount; }
    public String getPreviousCurrencyCode() { return previousCurrencyCode; }
    public SalaryPeriod getPreviousSalaryPeriod() { return previousSalaryPeriod; }
    public BigDecimal getNewAmount() { return newAmount; }
    public String getNewCurrencyCode() { return newCurrencyCode; }
    public SalaryPeriod getNewSalaryPeriod() { return newSalaryPeriod; }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
