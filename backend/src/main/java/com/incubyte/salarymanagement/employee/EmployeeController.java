package com.incubyte.salarymanagement.employee;

import com.incubyte.salarymanagement.api.PageResponse;
import com.incubyte.salarymanagement.validation.CountryCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/employees")
@Validated
public class EmployeeController {
    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeCreateRequest request) {
        EmployeeResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<EmployeeResponse> search(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String name,
            @RequestParam(required = false) @Size(max = 32)
                    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String employeeNumber,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z]{2}") @CountryCode String countryCode,
            @RequestParam(required = false) @Size(max = 100) String department,
            @RequestParam(defaultValue = "lastName,asc") @Size(max = 50) String sort) {
        return service.search(page, size, name, employeeNumber, countryCode, department, sort);
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public EmployeeResponse updateProfile(@PathVariable UUID id, @Valid @RequestBody EmployeeUpdateRequest request) {
        return service.updateProfile(id, request);
    }

    @GetMapping("/{id}/compensation")
    public CompensationResponse getCompensation(@PathVariable UUID id) {
        return service.getCompensation(id);
    }

    @PutMapping("/{id}/compensation")
    public CompensationResponse updateCompensation(@PathVariable UUID id,
                                                    @Valid @RequestBody CompensationUpdateRequest request,
                                                    Authentication authentication) {
        return service.updateCompensation(id, request, authentication.getName());
    }

    @GetMapping("/{id}/compensation/history")
    public PageResponse<SalaryChangeResponse> compensationHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return service.compensationHistory(id, page, size);
    }
}
