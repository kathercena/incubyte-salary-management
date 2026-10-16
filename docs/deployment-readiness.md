# Render + Neon deployment preparation

This repository contains deployment configuration for an assessment/demo deployment. No cloud resources have been created, and the application has not been deployed. Use synthetic data only. The shared HTTP Basic HR account and free-tier service limits are not suitable for real employee salary data or production HR use.

The target is a Render Free Static Site for Angular, a Render Free Docker Web Service for Spring Boot, and a Neon PostgreSQL database. A $0 bill depends on current provider terms and staying within included quotas; it is not guaranteed. Render explicitly describes Free web services as unsuitable for production. A Free Render web service sleeps after 15 minutes without inbound traffic and may take about a minute to wake; Render currently includes 750 free instance hours per workspace/month, and bandwidth/build limits also apply. Neon Free currently includes 100 CU-hours and 1 GB storage per project/month, with compute scaling to zero after inactivity. These limits and terms can change; review current plans and billing before creating resources.

## What the Blueprint configures

The root [`render.yaml`](../render.yaml) defines two Render services. It does not create a Neon database. Render's supported Blueprint fields cover the Docker web service, static site, service reference, health check, build output, response headers, and prompted environment variables.

| Service | Root directory / context | Build and runtime | Public configuration |
| --- | --- | --- | --- |
| `salary-management-api` | Docker context `./backend`; Dockerfile `./backend/Dockerfile` (paths relative to repository root) | Multi-stage Java 17 image; Maven packages the Spring Boot JAR; runs as UID/GID 10001. The app binds to `0.0.0.0:$PORT` (local default `8080`). | Health check: `/actuator/health`. Render provides the backend `https://…onrender.com` URL. |
| `salary-management-frontend` | Root `frontend` | `npm ci && npm run build && node scripts/write-render-config.mjs`; publishes `dist/frontend/browser`. | Render supplies the backend origin through `API_ORIGIN`. The script writes `app-config.js` into generated output with `apiBaseUrl: "https://<backend>.onrender.com/api"`. |

The public Angular config contains only the API base URL. The source-controlled `frontend/public/app-config.js` remains `/api` for local proxy development; the deployment script writes only into the generated build directory. It refuses a missing, non-HTTPS, or non-origin `API_ORIGIN` and does not accept credentials or path/query components.

The frontend's Basic-auth interceptor attaches credentials only when both the request origin and `/api` path match the configured API base. The backend allows CORS only from the single exact `APP_CORS_ALLOWED_ORIGIN` value and only for `/api/**`; allowed methods are GET, POST, PUT, OPTIONS and allowed headers are Authorization and Content-Type. Preflight is permitted before authentication. CORS is denied when no origin is configured. Do not replace this with `*`.

## Neon database setup

Create a new, empty Neon PostgreSQL database/project for this demo. Do not use the local Compose database or copy real personnel records. In Neon, select the direct (non-pooler) connection details, then separate the hostname, database name, role, and password. The backend URL uses this format, with no credentials embedded in it:

```text
jdbc:postgresql://<neon-host>:5432/<database>?sslmode=verify-full&sslrootcert=/etc/ssl/certs/ca-certificates.crt
```

Use `sslmode=verify-full` so pgJDBC validates the trusted certificate chain and the Neon hostname. Neon documents ISRG Root X1 as its public CA and gives `/etc/ssl/certs/ca-certificates.crt` as the Debian/Ubuntu system CA bundle path; the backend runtime image is Ubuntu Jammy based. This review rebuilt the image and confirmed the CA bundle is readable, trusts ISRG Root X1, and the Java truststore contains the ISRG Root X1 trusted entry. The configured JDBC URL explicitly points pgJDBC at the OS bundle. A TLS-verified connection to Neon still needs testing after a disposable Neon database is created, so no successful Neon handshake is claimed. Do not use real employee data. Supply the database role and password separately using `SPRING_DATASOURCE_USERNAME` and `POSTGRES_PASSWORD`. The existing Spring datasource configuration maps these variables and runs Flyway V1/V2 on application startup. Use a fresh empty database so Flyway can establish its schema. No Flyway migration or seed was run against a remote database as part of preparing this repository.

## Render Dashboard configuration

1. After reviewing, committing, and pushing these deployment-preparation changes, create a Blueprint from the repository's `main` branch. The first creation flow prompts for the five `sync: false` backend values. Enter the Neon JDBC URL, Neon role, Neon password, HR Manager username, and HR Manager password through the Dashboard only. Do not place values in the Blueprint, Git, a browser-visible frontend setting, or build arguments. When adding/changing a secret after the initial Blueprint creation, set it in the service's Environment page; Render does not re-prompt for `sync: false` values on later Blueprint syncs.
2. In the Render backend service's Environment page, set `APP_CORS_ALLOWED_ORIGIN` to the exact frontend origin, for example `https://salary-management-frontend.onrender.com` (origin only; no trailing path or `/`). Obtain the actual value from the deployed static site's URL. Save and redeploy the backend. Until set, browser cross-origin requests are intentionally denied.
3. Confirm the frontend's build-time `API_ORIGIN` reference resolves to the backend service's `RENDER_EXTERNAL_URL`. If the first static build runs before that reference is available or the backend URL later changes, redeploy the static site so the script can produce the correct public `/api` URL.
4. Verify the public site is HTTPS, `GET https://<backend-host>/actuator/health` reports `UP`, the browser preflight from the exact frontend origin succeeds, and authenticated API requests work. Confirm no credential is present in Angular assets or the browser network URL.

Render preview deployments use separate frontend origins and are not allowed by this single-origin CORS configuration. Only enable a preview origin through an explicit backend configuration change and test; do not use a wildcard.

### Backend environment variables

| Variable | Value source |
| --- | --- |
| `SPRING_DATASOURCE_URL` | Neon host/database JDBC URL above, with `sslmode=verify-full` and the system CA bundle path |
| `SPRING_DATASOURCE_USERNAME` | Neon database role |
| `POSTGRES_PASSWORD` | Neon database role password; secret |
| `HR_MANAGER_USERNAME` | A newly selected demo HR username |
| `HR_MANAGER_PASSWORD` | A unique strong demo password; secret |
| `APP_CORS_ALLOWED_ORIGIN` | Exact Render static-site origin; set manually after its URL is known |
| `PORT` | Supplied by Render; the application defaults to `8080` locally |

Render terminates HTTPS for its public services. Require HTTPS for the frontend and backend endpoints; Neon connections use TLS. Do not send HTTP Basic credentials over plaintext HTTP. Rotate demo credentials after a demo and use a dedicated database role with only the permissions needed by the application and Flyway.

## Seed the new Neon database explicitly

Seeding is never part of normal application startup or the Render web service. Only run this after verifying that the target is the newly created, empty Neon demo database. The launcher checks for an explicit `--app.seed.enabled=true` opt-in, runs Flyway, and inserts the deterministic synthetic dataset transactionally. An exact rerun is a no-op; partial/conflicting identities fail without overwriting existing employees.

From a local PowerShell session at the repository root, first obtain the public ISRG Root X1 certificate from the official [Let's Encrypt certificate page](https://letsencrypt.org/certificates/) and save it outside the repository as `%APPDATA%\postgresql\root.crt`. pgJDBC uses that default root certificate path on Windows when `sslrootcert` is not specified. Then enter the Neon host and database name, and read the role password without echoing it. The host guard prevents this documented command from targeting the local Compose database. Do not paste a password into a command, URL, source file, or log.

```powershell
$originalLocation = Get-Location
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
try {
  $neonHost = Read-Host 'Neon database hostname'
  if ($neonHost -notmatch '^[A-Za-z0-9.-]+\.neon\.tech$') { throw 'Expected the hostname of the new Neon database.' }
  $database = Read-Host 'Neon database name'
  if ($database -notmatch '^[A-Za-z0-9_]+$') { throw 'Use a simple PostgreSQL database name.' }
  $rootCertificate = Join-Path $env:APPDATA 'postgresql\root.crt'
  if (-not (Test-Path -LiteralPath $rootCertificate -PathType Leaf)) { throw 'Save the official ISRG Root X1 PEM certificate at %APPDATA%\postgresql\root.crt first.' }
  $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://${neonHost}:5432/${database}?sslmode=verify-full"
  $env:SPRING_DATASOURCE_USERNAME = Read-Host 'Neon database role'
  $securePassword = Read-Host 'Neon database password' -AsSecureString
  $credential = [System.Net.NetworkCredential]::new($env:SPRING_DATASOURCE_USERNAME, $securePassword)
  $env:POSTGRES_PASSWORD = $credential.Password
  $env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
  $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
  $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
  Set-Location (Join-Path $originalLocation 'backend')
  mvn -B package
  if ($LASTEXITCODE -ne 0) { throw 'Maven package failed; seed was not started.' }
  mvn -B spring-boot:run "-Dspring-boot.run.main-class=com.incubyte.salarymanagement.seed.EmployeeSeedApplication" "-Dspring-boot.run.arguments=--app.seed.enabled=true"
  if ($LASTEXITCODE -ne 0) { throw 'Seed command failed.' }
} finally {
  Set-Location $originalLocation
  $env:JAVA_HOME = $previousJavaHome
  $env:PATH = $previousPath
  Remove-Item Env:SPRING_DATASOURCE_URL, Env:SPRING_DATASOURCE_USERNAME, Env:POSTGRES_PASSWORD, Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
  $credential = $null
  $securePassword = $null
}
```

Review the Neon host and database name immediately before running Maven. Do not use the Compose database, a production database, or any database containing real employees. This command is documented only; it was not executed during deployment preparation.

## Remaining release work

- Create the Render Blueprint and Neon project in the user's accounts; enter secret values privately in the Dashboard.
- Set the exact CORS origin, verify the generated frontend config, deployment health, and full browser HR workflow.
- Decide whether free service sleep/quotas and Neon restore/backup limits are acceptable for the demo; select paid plans and operational backups before any production use.
- Record deployment evidence and the demo video. No cloud deployment is claimed complete here.

## Provider references

- [Render Blueprint specification](https://render.com/docs/blueprint-spec)
- [Render web-service port binding](https://render.com/docs/web-services)
- [Render free-service limitations](https://render.com/docs/free)
- [Render environment variables](https://render.com/docs/environment-variables)
- [Neon PostgreSQL connection guidance](https://neon.com/docs/connect/connect-from-any-app)
- [Neon TLS verification and CA paths](https://neon.com/docs/connect/connect-securely)
- [Neon Free plan limits](https://neon.com/pricing)
- [pgJDBC SSL modes and certificate verification](https://jdbc.postgresql.org/documentation/ssl/)
