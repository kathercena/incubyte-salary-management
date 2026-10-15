CREATE TABLE employees (
    id UUID PRIMARY KEY,
    employee_number VARCHAR(32) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    country_code CHAR(2) NOT NULL,
    department VARCHAR(100) NOT NULL,
    job_title VARCHAR(120) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    base_salary NUMERIC(19, 4) NOT NULL CHECK (base_salary >= 0),
    salary_period VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employees_salary_period_valid CHECK (salary_period IN ('ANNUAL', 'MONTHLY', 'HOURLY'))
);

CREATE INDEX employees_country_code_idx ON employees (country_code);
CREATE INDEX employees_department_idx ON employees (department);
CREATE INDEX employees_name_idx ON employees (last_name, first_name, id);
