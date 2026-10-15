package com.incubyte.salarymanagement.employee;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryChangeAuditRepository extends JpaRepository<SalaryChangeAudit, UUID> {
    Page<SalaryChangeAudit> findByEmployeeIdOrderByChangedAtDescIdDesc(UUID employeeId, Pageable pageable);
}
