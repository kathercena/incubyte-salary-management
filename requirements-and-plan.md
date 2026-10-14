# Salary Management â€” Requirements and Delivery Plan

**Assessment:** Incubyte Salary Management | **Phase:** Planning only | **Assumptions:** One HR-manager persona; 10,000 synthetic employees; salaries are stored as gross annual base salary in each employeeâ€™s stated currency; no payroll processing. Confirm salary period, authorization model and target hosting account before implementation.

## Requirements (one-page draft)

**Goal.** Give an HR Manager a secure, responsive way to find employees and maintain their basic employment and salary records across countries, with correct currency context and auditable changes.

**In scope.** Employee directory with server-side pagination, sorting and filters (name, employee ID, country, department); employee detail and create/edit flows; validated employee identity, country, department, currency and salary fields; clear currency and salary-period display; seeded, reproducible dataset of 10,000 synthetic employees; accessible Angular Material UI; documented REST API; deterministic unit and integration tests; deployable application, README, architecture/trade-off notes, AI prompt log, and short demo video. Salary values must never be summed or averaged across currencies. Use authenticated access and keep credentials outside source control.

**Explicit exclusions.** Payroll runs, tax/net-pay calculations, payslips, bank/payment integrations, FX conversion, benefits, time/attendance, employee self-service, multi-tenant administration, microservices, and elaborate analytics. These add regulatory, security, integration or delivery risk without being needed to demonstrate salary record management. No real employee data or public salary endpoints.

**Success.** A reviewer can sign in, browse/filter/page through 10,000 records, inspect and update a record with validation, and see changes persist; seed and tests are repeatable; the deployed HTTPS app is usable by the reviewer; documentation and demo explain the solution and trade-offs.

## Architecture and schema

Use a modular monolith: Angular + Angular Material SPA â†’ Spring Boot 3 REST API (Java 17) â†’ PostgreSQL. Keep modules simple: employees, reference data, security/audit. Serve the SPA and API from one origin if the host permits; otherwise configure explicit CORS. Use Spring Data JPA, Bean Validation, Flyway migrations, DTOs and centralized error responses. Use `BigDecimal`/PostgreSQL `NUMERIC`, never floating point for money. Store ISO 4217 currency code per employee and salary period explicitly. Suggested `employee`: id (UUID), employee_number (unique), first_name, last_name, email (unique if required), country_code, department, job_title, currency_code, base_salary (NUMERIC(19,4), nonnegative), salary_period (enum), version, created_at, updated_at. Add indexes for employee_number, country_code, department and normalized name/search fields. Keep a separate append-only salary-change audit record (actor, employee, old/new amount and currency, timestamp) if it fits the available authentication model; never log salary values in request/error logs. Prefer PostgreSQL for deployment parity; use embedded H2 only for fast tests, not as production fallback.

## Acceptance criteria

- Directory requests enforce bounded page size (default 25, maximum 100), stable sort with unique tie-breaker, and validated filters; response includes total count and page metadata.
- Filters and sorting combine correctly; no client-side loading of all 10,000 records.
- Create/update rejects missing or malformed required fields, invalid country/currency codes, negative or excessive precision salary, duplicate employee number, and invalid email where email is collected; errors identify fields without leaking internals.
- Salary is always displayed with currency code and period; no cross-currency aggregate is presented.
- Seed is deterministic, idempotent or safely resettable, creates exactly 10,000 synthetic employees, and contains no real personal data.
- Protected API and UI require authentication; production uses HTTPS and secrets from host-managed configuration.
- Reviewer can deploy from documented steps and exercise the complete core flow; tests pass in CI using a disposable database.

## Risks and controls

- **Salary privacy:** protect every API route, least-privilege HR role, HTTPS, secret management, avoid salary in logs/URLs, audit edits, synthetic demo data; clarify auth provider before coding.
- **Currency semantics:** define salary period and precision; persist currency per record; prohibit cross-currency totals/averages and FX assumptions.
- **Pagination/query abuse:** server-side bounded pagination, deterministic ordering, allowlisted sort fields, indexed filters, parameterized queries; test page boundaries and combined filters.
- **Validation/data quality:** ISO country/currency allowlists, `NUMERIC` plus Java `BigDecimal`, explicit scale/range, uniqueness constraints, clear API errors; confirm whether email is mandatory.
- **Deployment/time:** hosting account, database, auth and video tooling are unknown; choose one-origin managed hosting and managed PostgreSQL early, keep a tested local Compose option only if Docker becomes available. Avoid Kubernetes and multi-service deployment.
- **10,000-row seed:** generate from fixed seed and bounded batches; verify exact count and rerun behavior without committing generated personal-looking data.

## Deployment recommendation and test minimum

For a 2â€“3 day deadline, use one managed platform that can build/host Spring Boot and Angular plus managed PostgreSQL (e.g. Render or Railway, subject to account, pricing and region availability). Build Angular into Spring static resources or deploy both behind one host; provision DB, run Flyway, seed synthetic data, set secrets via host environment, enable HTTPS, and smoke-test the public URL with a non-sensitive demo account. Confirm current platform limits/pricing before choosing; no hosting credentials are present in this workspace.

Minimum tests: JUnit/Mockito service tests for create/update validation, duplicate employee, salary precision and audit behavior; repository/API integration tests against PostgreSQL (Testcontainers if Docker is available) for migrations, paging/filter/sort, constraints and error responses; Angular unit tests for form validation and currency/period rendering; one end-to-end smoke flow for login â†’ filter/page â†’ edit â†’ reload persisted value. Seed test asserts fixed output and exactly 10,000 rows. Keep clock/seed deterministic. Run tests in CI; do not rely solely on H2 for PostgreSQL behavior.

## Prioritized checklist and incremental commits

1. **P0 â€” Confirm prerequisites and decisions:** install/verify JDK 17, Node LTS/npm, Git, build tools and Docker or choose hosted DB; confirm salary period, auth and deployment account.
2. **P0 â€” Bootstrap and schema:** create Maven Spring Boot and Angular workspaces, Flyway schema, health endpoint, configuration and README; commit `chore: bootstrap application and local development`.
3. **P0 â€” Backend core:** employee DTO/entity/service/API, validation, bounded paging/filter/sort, errors and migration; commit `feat: add employee management API`.
4. **P0 â€” Seed and backend tests:** deterministic 10k generator, PostgreSQL integration coverage and CI; commit `test: seed sample employees and cover API behavior`.
5. **P0 â€” Frontend core:** directory, server-side filters/paging, detail/edit form, currency-aware display and API integration; commit `feat: build employee management UI`.
6. **P0 â€” Security and privacy:** authentication/authorization, secrets, safe logging and salary audit; commit `feat: secure salary access and audit changes`.
7. **P0 â€” Deploy and verify:** production config, managed DB, HTTPS smoke test, demo account/data; commit `chore: deploy assessment application`.
8. **P1 â€” Finish assessment evidence:** README, architecture and trade-offs, AI prompts, reproducible setup, demo video and final review; commit `docs: document architecture and assessment walkthrough`.

**Current repository/tool baseline:** workspace inspection found only empty `outputs/` and `work/` directories and no source files. Git, Node/npm, Angular CLI, Maven/Gradle, Docker and `psql` were not found on PATH; only a Java launcher shim was found, so JDK 17 remains unverified. No command, test, or deployment has been claimed as successful. This document is a draft for review; implementation has not started.

