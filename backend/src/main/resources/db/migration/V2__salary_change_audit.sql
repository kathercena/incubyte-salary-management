CREATE TABLE salary_change_audit (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees (id) ON DELETE RESTRICT,
    previous_amount NUMERIC(19, 4) NOT NULL CHECK (previous_amount >= 0),
    previous_currency_code CHAR(3) NOT NULL,
    previous_salary_period VARCHAR(16) NOT NULL CHECK (previous_salary_period IN ('ANNUAL', 'MONTHLY', 'HOURLY')),
    new_amount NUMERIC(19, 4) NOT NULL CHECK (new_amount >= 0),
    new_currency_code CHAR(3) NOT NULL,
    new_salary_period VARCHAR(16) NOT NULL CHECK (new_salary_period IN ('ANNUAL', 'MONTHLY', 'HOURLY')),
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX salary_change_audit_employee_changed_at_idx
    ON salary_change_audit (employee_id, changed_at DESC, id);
