# Incubyte Salary Management — Requirements and Plan

**Assessment:** Incubyte Salary Management. **Environment:** Windows 11, Java 17.0.12, Maven 3.9.16, Node.js 24.20.0, npm 11.19.0, Angular CLI 22.2.2, Git 2.56.0, Docker Desktop, PostgreSQL 17. **MVP decisions:** one HR Manager role using HTTP Basic credentials from environment variables; one current compensation per employee with an explicit `ANNUAL`, `MONTHLY`, or `HOURLY` period; no payroll processing. Deploy only over HTTPS.

## Requirements

Build a secure, responsive application for an HR Manager to manage 10,000 synthetic employees across countries. The Angular interface must provide an employee directory with server-side pagination, stable sorting, and validated filters for name, employee ID, country, and department; employee detail and create/edit flows; and clear salary currency and period display. The Java 17 Spring Boot API must validate employee identity, country, department, currency, and salary fields, use DTOs and consistent field-level errors, and protect salary data behind authenticated access. PostgreSQL stores salaries as `NUMERIC`; Java uses `BigDecimal`. Each record carries an ISO currency and salary period. Never sum or average salaries across currencies.

Deliver a reproducible seed for exactly 10,000 synthetic employees, deterministic unit and integration tests, a deployable functional application, README and architecture/trade-off notes, AI workflow artifacts, and a short demo video. Success means a reviewer can sign in, search and page through the records, inspect and update a validated record, and see the saved change; setup, seed, tests, deployment, and demo are documented and repeatable.

Out of scope: payroll, tax/net-pay calculations, payslips, payments, FX conversion, benefits, attendance, employee self-service, multi-tenant administration, microservices, and elaborate analytics. Do not use real personal data or expose unauthenticated salary endpoints.

## Architecture and acceptance criteria

Use an explainable modular monolith: Angular SPA → Spring Boot REST API → PostgreSQL, with Flyway migrations, Bean Validation, DTOs, centralized API errors, and Spring Security HTTP Basic for the MVP HR role. Keep credentials outside source control. Directory page size defaults to 25 and is capped at 100; sorting is allowlisted and has a unique tie-breaker; filters combine correctly and requests return page metadata. Reject malformed and duplicate identity fields, invalid country/currency codes, negative salary, and excess precision. Store currency and period with every salary. Seed generation is fixed-seed, repeatable, and produces exactly 10,000 synthetic rows. HTTPS and host-managed secrets are required for production.

The existing V1 employee schema defines UUID, unique employee number and email, first/last name, country code, department, job title, currency code, base salary (`NUMERIC(19,4)`), salary period, optimistic-lock version, and timestamps. It stores one current compensation per employee. Index employee number, country, department, and normalized name as query needs become concrete. Append salary-change audit records attributed to the authenticated HR username; never log salary values.

## Risks and delivery

Salary privacy requires authenticated HR access, least privilege, safe logs, auditability, HTTPS, and synthetic demo data. Currency aggregation is prohibited without an FX model. Bound and index directory queries. Validate inputs at API and database layers. Confirm authentication and hosting choices before production deployment; prefer one managed application platform and PostgreSQL, subject to account and current pricing. Keep Compose for local PostgreSQL and preserve its named volume.

Implement in five reviewed milestones: (1) requirements and Java 17/Maven, Angular, PostgreSQL, migration, health check, and local instructions; (2) employee and salary API; (3) directory and deterministic 10,000-row seed; (4) compensation insights and stronger tests; (5) deployment and assessment documentation. Verify each milestone before its commit. Do not push until the user reviews local changes and history. Minimum verification includes backend and frontend builds, service/API validation and paging tests, PostgreSQL migration/constraint integration checks, deterministic exact-count seed verification, frontend form/currency tests, and a deployed sign-in/search/edit smoke flow.
