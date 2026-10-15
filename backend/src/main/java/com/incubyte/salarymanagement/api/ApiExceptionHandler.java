package com.incubyte.salarymanagement.api;

import com.incubyte.salarymanagement.employee.EmployeeConflictException;
import com.incubyte.salarymanagement.employee.EmployeeNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EmployeeNotFoundException.class)
    ResponseEntity<ApiError> notFound(EmployeeNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({EmployeeConflictException.class, DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> conflict(Exception exception, HttpServletRequest request) {
        String message = exception instanceof EmployeeConflictException
                ? exception.getMessage() : "The record conflicts with a concurrent change or existing data.";
        return response(HttpStatus.CONFLICT, message, request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldViolation)
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "Request validation failed.", request, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> invalidRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "The request is invalid.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> internalError(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request, List.of());
    }

    private ApiError.FieldViolation fieldViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage() == null
                ? "Invalid value." : error.getDefaultMessage());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request,
                                               List<ApiError.FieldViolation> errors) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), errors));
    }
}
