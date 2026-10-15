# Incubyte Salary Management

Salary management application for an HR Manager, delivered in five reviewable milestones. Milestone 1 established the Java 17 Spring Boot and Angular applications, PostgreSQL local setup, and health check. Milestone 2 adds the authenticated employee and compensation API.

## Local development

Prerequisites: Java 17, Maven 3.9+, Node.js 24/npm 11, Angular CLI 22, and Docker Desktop with Compose.

1. Enter the local database password privately in PowerShell, then start PostgreSQL. This reads it without echoing or storing it in command history. The existing Compose file persists data in the `postgres_data` volume; do not remove that volume for routine development.

   ```powershell
   $securePassword = Read-Host 'Local PostgreSQL password' -AsSecureString
   $credential = [System.Net.NetworkCredential]::new('salary_app', $securePassword)
   $env:POSTGRES_PASSWORD = $credential.Password
   docker compose up -d postgres
   ```

2. In another PowerShell session, enter the same database password privately and configure the HR Manager credentials privately. Use UTC for the Java process so PostgreSQL receives a timezone name it recognizes on Windows hosts configured with the `Asia/Calcutta` alias. Spring reads the database password from `POSTGRES_PASSWORD`; the API uses HTTP Basic authentication with `HR_MANAGER_USERNAME` and `HR_MANAGER_PASSWORD`.

   ```powershell
   $securePassword = Read-Host 'Local PostgreSQL password' -AsSecureString
   $credential = [System.Net.NetworkCredential]::new('salary_app', $securePassword)
   $env:POSTGRES_PASSWORD = $credential.Password
   $env:HR_MANAGER_USERNAME = Read-Host 'HR Manager username'
   $secureHrPassword = Read-Host 'HR Manager password' -AsSecureString
   $hrCredential = [System.Net.NetworkCredential]::new($env:HR_MANAGER_USERNAME, $secureHrPassword)
   $env:HR_MANAGER_PASSWORD = $hrCredential.Password
   $env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
   cd backend
   mvn spring-boot:run
   ```

3. Install the locked frontend dependencies and start Angular in another terminal.

   ```powershell
   cd frontend
   npm ci
   npm start
   ```

The API listens on `http://localhost:8080`; its health endpoint is `http://localhost:8080/actuator/health`. Angular serves at `http://localhost:4200`. The employee directory UI is added in a later milestone. No Angular feature tests are present yet; frontend tests will be added with the UI features. Never commit `.env` files, local credentials, personal data, or generated build output.

The employee API uses HTTP Basic authentication and requires the configured HR Manager account. The health endpoint is public; employee and compensation endpoints are protected. HTTP Basic credentials must only be used over HTTPS in a deployed environment.

## Backend API

All `/api/**` routes require the HR Manager's HTTP Basic credentials.

- `POST /api/employees` — create employee and initial current compensation.
- `GET /api/employees` — server-paginated directory; filters: `name`, `employeeNumber`, `countryCode`, `department`; default page/size: `0`/`25`, maximum size: `100`; sorting uses an allowlist and stable ID tie-breaker.
- `GET /api/employees/{id}` and `PUT /api/employees/{id}` — read/update employee profile. Updates require the current `version`.
- `GET /api/employees/{id}/compensation` and `PUT /api/employees/{id}/compensation` — read/update current amount, ISO currency, and `ANNUAL`, `MONTHLY`, or `HOURLY` period. Updates require the current employee `version` and append an audit entry.
- `GET /api/employees/{id}/compensation/history` — paginated, read-only compensation change audit.

No employee or audit deletion route is provided. The employee's current compensation remains on the employee row; the audit is append-only. Run backend integration tests with `cd backend; mvn test`; tests use a dedicated PostgreSQL 17 Testcontainers database and never connect to the Compose database.

## Explicit synthetic employee seed

The backend includes a deterministic dataset of exactly 10,000 synthetic employees. It uses reserved `.test` email addresses, stable employee numbers and IDs, fixed timestamps, supported country/currency pairs, and all three salary periods. It creates the current employee compensation only; the append-only salary-change history starts when compensation is subsequently changed through the API. The seed does not run during normal application startup and does not expose an HTTP endpoint. It inserts in one transaction using JDBC batches, refuses partial or conflicting seed identities, and reports an exact complete rerun as a no-op.

Run the seed only against a disposable PostgreSQL database. The following PowerShell setup creates a temporary PostgreSQL 17 container without a volume. Keep it running in one terminal; stopping the container removes its data. This is separate from the persistent Compose database above.

```powershell
$securePassword = Read-Host 'Disposable seed database password' -AsSecureString
$credential = [System.Net.NetworkCredential]::new('seed_app', $securePassword)
$env:POSTGRES_PASSWORD = $credential.Password
docker run --rm --name incubyte-salary-seed-db -e POSTGRES_DB=salary_seed -e POSTGRES_USER=seed_app -e POSTGRES_PASSWORD -p 127.0.0.1:55432:5432 postgres:17
```

In another PowerShell terminal, configure the disposable database privately. The one-shot non-web launcher does not start HTTP security or require HR Manager credentials. Build the backend, then run the launcher with its required opt-in flag. Flyway applies the existing migrations to that disposable database before the seed runs.

```powershell
$securePassword = Read-Host 'Disposable seed database password' -AsSecureString
$credential = [System.Net.NetworkCredential]::new('seed_app', $securePassword)
$env:POSTGRES_PASSWORD = $credential.Password
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:55432/salary_seed'
$env:SPRING_DATASOURCE_USERNAME = 'seed_app'
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
cd backend
mvn -B package
mvn -B spring-boot:run "-Dspring-boot.run.main-class=com.incubyte.salarymanagement.seed.EmployeeSeedApplication" "-Dspring-boot.run.arguments=--app.seed.enabled=true"
```

The first run reports 10,000 inserted employees; an exact rerun reports that the dataset is already complete without changing rows. Conflicting identifiers or a partial dataset stop without overwriting existing employees. Integration tests use their own isolated Testcontainers PostgreSQL instance, never this disposable database or the Compose volume.


## Project structure

- `backend/` — Spring Boot modular monolith (Java 17), REST API, Flyway migrations.
- `frontend/` — standalone Angular application.
- `compose.yaml` — local PostgreSQL 17 with persistent named volume.
- `requirements-and-plan.md` — assessment requirements, decisions, and milestone plan.

## Current scope

Milestone 1 established application startup and PostgreSQL health. Milestone 2 adds authenticated employee and compensation operations, validation, optimistic concurrency, and append-only compensation audit. The frontend remains a scaffold pending Milestone 3.
