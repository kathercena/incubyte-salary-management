package com.incubyte.salarymanagement.employee;

import java.util.UUID;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
    List<Employee> findAllByEmployeeNumberInOrEmailIn(Collection<String> employeeNumbers, Collection<String> emails);
}
