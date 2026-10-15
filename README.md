# Incubyte Salary Management

Salary management application for an HR Manager. The repository is being delivered in five reviewable milestones. This first milestone bootstraps the Java 17 Spring Boot backend, Angular frontend, PostgreSQL local setup, and health check.

## Local development

Prerequisites: Java 17, Maven 3.9+, Node.js 24/npm 11, Angular CLI 22, and Docker Desktop with Compose.

1. Enter the local database password privately in PowerShell, then start PostgreSQL. This reads it without echoing or storing it in command history. The existing Compose file persists data in the `postgres_data` volume; do not remove that volume for routine development.

   ```powershell
   $securePassword = Read-Host 'Local PostgreSQL password' -AsSecureString
   $credential = [System.Net.NetworkCredential]::new('salary_app', $securePassword)
   $env:POSTGRES_PASSWORD = $credential.Password
   docker compose up -d postgres
   ```

2. In another PowerShell session, enter the same password privately. Use UTC for the Java process so PostgreSQL receives a timezone name it recognizes on Windows hosts configured with the `Asia/Calcutta` alias. Spring reads the password from `POSTGRES_PASSWORD`.

   ```powershell
   $securePassword = Read-Host 'Local PostgreSQL password' -AsSecureString
   $credential = [System.Net.NetworkCredential]::new('salary_app', $securePassword)
   $env:POSTGRES_PASSWORD = $credential.Password
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

The API listens on `http://localhost:8080`; its health endpoint is `http://localhost:8080/actuator/health`. Angular serves at `http://localhost:4200`. Employee API/UI functionality is added in subsequent milestones. This bootstrap has no Angular test target yet; frontend tests are added with the relevant features. Never commit `.env` files, local credentials, personal data, or generated build output.


## Project structure

- `backend/` — Spring Boot modular monolith (Java 17), REST API, Flyway migrations.
- `frontend/` — standalone Angular application.
- `compose.yaml` — local PostgreSQL 17 with persistent named volume.
- `requirements-and-plan.md` — assessment requirements, decisions, and milestone plan.

## Current scope

Milestone 1 establishes application startup and PostgreSQL health. It intentionally does not expose employee or salary endpoints before authentication and the employee data model are implemented.
