# Render + Neon deployment status

The Incubyte Salary Management assessment is deployed for a synthetic-data demo. The deployment uses a Render static site for Angular, a Render Docker web service for Spring Boot, and Neon PostgreSQL. It is an assessment MVP, not a production HR system; do not enter real employee or salary data.

## Current deployment

| Component | Deployed address | Verified status |
| --- | --- | --- |
| Angular frontend | [salary-management-frontend-pnwt.onrender.com](https://salary-management-frontend-pnwt.onrender.com) | Production site is deployed; the live HR workflow was manually exercised. |
| Spring Boot API | [salary-management-api-fqc0.onrender.com](https://salary-management-api-fqc0.onrender.com) | Deployed; `/actuator/health` returned `UP`. |
| Neon PostgreSQL | Database `neondb` | Contains 10,000 synthetic employees. Read-only verification found 10,000 distinct IDs, employee numbers, and emails, and compensation data for all 10,000 rows. |

Manual live testing covered HR sign-in, employee directory use, profile editing, compensation editing, and compensation audit history. The UI/API preserve the salary currency and period; amounts are decimal strings in the browser and API response to avoid JavaScript precision loss.

## Verification evidence and limitations

- Angular production build passed, TypeScript checking passed, and the frontend suite passed **24 tests**.
- The latest Maven Surefire reports record **23 tests: 23 passed, 0 failures, 0 errors, 0 skipped** (15 employee API integration tests and 8 seed integration tests). Maven reported `BUILD SUCCESS` after running with Java 17 and `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` in the PowerShell session.
- The backend integration tests use Testcontainers with isolated PostgreSQL databases; they do not use the Render/Neon database or the local Compose volume.
- Neon preflight used pgJDBC with `sslmode=verify-full` and an explicit trusted root certificate. The deployed application connects using TLS configuration from its server-side environment. No database credentials or connection strings containing credentials belong in this repository.
- The assessment demo video has been recorded and uploaded to Google Drive; its link is included in the README. The link is supplied, but external reviewer access has not been independently tested. AI collaboration artifacts are present in [`ai-collaboration.md`](ai-collaboration.md) and [`ai-prompts.md`](ai-prompts.md).
- Render Free and Neon Free quotas, sleep behavior, availability, and recovery limits can change. Review current provider terms and verify backup/restore expectations before any real operational use.

## Architecture and deployment configuration

The application is a modular monolith: Angular SPA → Spring Boot REST API → PostgreSQL. Flyway owns schema migrations; the API uses DTOs, Bean Validation, centralized API errors, HTTP Basic authentication for the MVP HR role, optimistic concurrency, and append-only compensation audit history. PostgreSQL stores salary as `NUMERIC`; Java uses `BigDecimal`. Currency and salary period are explicit, and the application does not aggregate unlike currencies or periods.

The root [`render.yaml`](../render.yaml) defines the deployed Render services:

| Service | Configuration | Runtime behavior |
| --- | --- | --- |
| `salary-management-api` | Docker context `./backend`; Dockerfile `./backend/Dockerfile` | Java 17 multi-stage image, non-root UID/GID 10001, listens on Render's `PORT` (local default 8080), health check `/actuator/health`. |
| `salary-management-frontend` | Root `frontend`; `npm ci && npm run build && node scripts/write-render-config.mjs`; publishes `dist/frontend/browser` | Receives the backend origin through Render's `RENDER_EXTERNAL_URL` service reference as `API_ORIGIN`; generated runtime config contains only the public API base URL. |

The source-controlled `frontend/public/app-config.js` remains `/api` for the local proxy. The deployment script writes the generated config into the build output and rejects non-HTTPS, credential-bearing, path, or query values. The frontend Basic-auth interceptor attaches credentials only to the configured API origin and `/api` path.

The backend CORS policy allows only the exact `APP_CORS_ALLOWED_ORIGIN` configured for the deployed frontend, for `/api/**`; preflight is allowed before authentication. Do not use a wildcard. Render preview URLs have different origins and are denied unless deliberately configured and tested. If a service URL changes, update the Render environment reference/origin as appropriate and redeploy the affected service.

## Server-side environment and secrets

Configure these values in the Render Dashboard's backend environment. Keep passwords and database URLs containing credentials out of Git, build arguments, frontend configuration, logs, and chat.

| Variable | Configuration |
| --- | --- |
| `SPRING_DATASOURCE_URL` | Neon JDBC URL using `sslmode=verify-full` and the server image's trusted CA bundle; never embed username or password. |
| `SPRING_DATASOURCE_USERNAME` | Dedicated Neon application role. |
| `POSTGRES_PASSWORD` | Neon application-role password; secret. |
| `HR_MANAGER_USERNAME` | Demo HR account name. |
| `HR_MANAGER_PASSWORD` | Demo HR account password; secret. |
| `APP_CORS_ALLOWED_ORIGIN` | Exact frontend origin: `https://salary-management-frontend-pnwt.onrender.com` (origin only, no path or trailing slash). |
| `PORT` | Supplied by Render; defaults to 8080 locally. |

Render terminates HTTPS at its public services. Use HTTPS for both public URLs and never send HTTP Basic credentials over plaintext HTTP. The shared Basic-auth account and free-tier deployment are for synthetic assessment data only, not real payroll or employee data.

## Neon TLS and database operations

The pgJDBC configuration uses `sslmode=verify-full`, which validates both the certificate chain and Neon hostname. The Render image includes the operating-system CA bundle, and the Java truststore contains the ISRG Root X1 root used by Neon. The local Neon preflight also succeeded with pgJDBC, `verify-full`, and an explicit root certificate. Keep TLS verification enabled; do not replace it with `sslmode=require` or disable certificate checking.

The deployed Neon database is already seeded. Verification reported database `neondb`, Flyway version 2, 10,000 employees, unique IDs/employee numbers/emails, and non-null compensation data for every employee. No additional seed run is needed for the assessment demo.

The repository's deterministic seed launcher is explicit opt-in only and is not invoked during normal application startup or by the Render web service. It inserts 10,000 synthetic employees transactionally, treats an exact complete rerun as a no-op, and rejects partial/conflicting identities without overwriting them. See the README's **Explicit synthetic employee seed** section for the command and safe disposable-database setup. Never run a seed against a non-empty or unintended database. Flyway V1 and V2 are applied by application startup to the configured database; do not reset or truncate a database to resolve a migration issue.

## Local development and troubleshooting

Follow the README for the local Compose database, environment-variable setup, Angular `/api` proxy, and Maven/Angular commands. The Compose database has a persistent volume and is separate from Testcontainers' disposable PostgreSQL test databases; do not remove that volume as routine troubleshooting.

- If the API is unhealthy, check Render logs and `/actuator/health`, then verify `PORT`, datasource URL, database role, password, and Flyway startup without printing secrets.
- If browser requests fail, check that `APP_CORS_ALLOWED_ORIGIN` exactly matches the deployed frontend origin and that `API_ORIGIN` resolves to the deployed backend origin. Rebuild/redeploy the static site if its backend reference changed.
- If pgJDBC reports certificate or hostname verification errors, confirm `sslmode=verify-full`, the Neon hostname, and the configured CA bundle. Do not disable verification to make the connection succeed.
- On Windows, use `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` in the PowerShell session running Maven/Spring Boot if PostgreSQL rejects the host timezone name.
- Run backend integration tests only where Docker/Testcontainers can access the Docker engine. A Maven `BUILD SUCCESS` with tests skipped is not evidence that the integration suite passed; inspect the Surefire test totals.

## Remaining assessment work

- Confirm that intended external reviewers can access the Google Drive demo video; access has not been independently tested.
- Preserve deployment verification evidence and periodically recheck provider limits and recovery options. No real employee or salary data should be used for this assessment deployment.

## Provider references

- [Render Blueprint specification](https://render.com/docs/blueprint-spec)
- [Render web-service port binding](https://render.com/docs/web-services)
- [Render free-service limitations](https://render.com/docs/free)
- [Render environment variables](https://render.com/docs/environment-variables)
- [Neon PostgreSQL connection guidance](https://neon.com/docs/connect/connect-from-any-app)
- [Neon TLS verification and CA paths](https://neon.com/docs/connect/connect-securely)
- [Neon Free plan limits](https://neon.com/pricing)
- [pgJDBC SSL modes and certificate verification](https://jdbc.postgresql.org/documentation/ssl/)
