package com.incubyte.salarymanagement.employee;

import com.incubyte.salarymanagement.api.PageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "employeeNumber", "employeeNumber",
            "firstName", "firstName",
            "lastName", "lastName",
            "countryCode", "countryCode",
            "department", "department",
            "jobTitle", "jobTitle",
            "createdAt", "createdAt");

    private final EmployeeRepository employees;
    private final SalaryChangeAuditRepository salaryChanges;

    public EmployeeService(EmployeeRepository employees, SalaryChangeAuditRepository salaryChanges) {
        this.employees = employees;
        this.salaryChanges = salaryChanges;
    }

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        Employee employee = new Employee(request.employeeNumber(), request.firstName(), request.lastName(),
                request.email(), request.countryCode(), request.department(), request.jobTitle(), request.amount(),
                request.currencyCode(), request.salaryPeriod());
        return response(employees.saveAndFlush(employee));
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(int page, int size, String name, String employeeNumber,
                                                  String countryCode, String department, String sort) {
        Sort resolvedSort = parseSort(sort);
        PageRequest pageable = PageRequest.of(page, size, resolvedSort);
        Specification<Employee> specification = EmployeeSpecifications.matching(
                name, employeeNumber, countryCode, department);
        return pageResponse(employees.findAll(specification, pageable).map(this::response));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID id) {
        return response(findEmployee(id));
    }

    @Transactional
    public EmployeeResponse updateProfile(UUID id, EmployeeUpdateRequest request) {
        Employee employee = findEmployee(id);
        verifyVersion(employee, request.version());
        employee.updateProfile(request.employeeNumber(), request.firstName(), request.lastName(), request.email(),
                request.countryCode(), request.department(), request.jobTitle());
        return response(employees.saveAndFlush(employee));
    }

    @Transactional(readOnly = true)
    public CompensationResponse getCompensation(UUID id) {
        Employee employee = findEmployee(id);
        return compensation(employee);
    }

    @Transactional
    public CompensationResponse updateCompensation(UUID id, CompensationUpdateRequest request, String actor) {
        Employee employee = findEmployee(id);
        verifyVersion(employee, request.version());
        boolean changed = employee.getBaseSalary().compareTo(request.amount()) != 0
                || !employee.getCurrencyCode().equals(request.currencyCode())
                || employee.getSalaryPeriod() != request.salaryPeriod();
        if (!changed) {
            return compensation(employee);
        }

        SalaryChangeAudit audit = new SalaryChangeAudit(employee.getId(), employee.getBaseSalary(),
                employee.getCurrencyCode(), employee.getSalaryPeriod(), request.amount(), request.currencyCode(),
                request.salaryPeriod(), actor);
        employee.updateCompensation(request.amount(), request.currencyCode(), request.salaryPeriod());
        Employee saved = employees.saveAndFlush(employee);
        salaryChanges.saveAndFlush(audit);
        return compensation(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalaryChangeResponse> compensationHistory(UUID employeeId, int page, int size) {
        findEmployee(employeeId);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("id")));
        Page<SalaryChangeResponse> result = salaryChanges
                .findByEmployeeIdOrderByChangedAtDescIdDesc(employeeId, pageable)
                .map(this::response);
        return pageResponse(result);
    }

    private Employee findEmployee(UUID id) {
        return employees.findById(id).orElseThrow(EmployeeNotFoundException::new);
    }

    private void verifyVersion(Employee employee, Long requestedVersion) {
        if (employee.getVersion() != requestedVersion) {
            throw new EmployeeConflictException("The employee was changed by another request. Reload and retry.");
        }
    }

    private Sort parseSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.containsKey(parts[0])) {
            throw new IllegalArgumentException("Unsupported sort field or direction.");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported sort field or direction.");
        }
        return Sort.by(direction, SORT_FIELDS.get(parts[0]))
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private EmployeeResponse response(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getEmployeeNumber(), employee.getFirstName(),
                employee.getLastName(), employee.getEmail(), employee.getCountryCode(), employee.getDepartment(),
                employee.getJobTitle(), employee.getBaseSalary().toPlainString(), employee.getCurrencyCode(), employee.getSalaryPeriod(),
                employee.getVersion(), employee.getCreatedAt(), employee.getUpdatedAt());
    }

    private CompensationResponse compensation(Employee employee) {
        return new CompensationResponse(employee.getId(), employee.getBaseSalary().toPlainString(), employee.getCurrencyCode(),
                employee.getSalaryPeriod(), employee.getVersion());
    }

    private SalaryChangeResponse response(SalaryChangeAudit audit) {
        return new SalaryChangeResponse(audit.getId(), audit.getEmployeeId(), audit.getPreviousAmount().toPlainString(),
                audit.getPreviousCurrencyCode(), audit.getPreviousSalaryPeriod(), audit.getNewAmount().toPlainString(),
                audit.getNewCurrencyCode(), audit.getNewSalaryPeriod(), audit.getChangedBy(), audit.getChangedAt());
    }

    private <T> PageResponse<T> pageResponse(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
