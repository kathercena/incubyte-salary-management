# Deployment readiness checklist

No deployment platform is specified in the assessment, and this application has not been deployed. Select a hosting platform and obtain its account/access details before producing deployment-specific configuration. Keep the current single Spring Boot service, Angular SPA, and PostgreSQL architecture; Kubernetes or additional cloud infrastructure is not required.

Before a production release:

- Provision PostgreSQL with backups, restricted network access, and a least-privilege application user. Run Flyway migrations as part of the controlled release process. Do not use the local Compose volume as production storage.
- Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `POSTGRES_PASSWORD`, `HR_MANAGER_USERNAME`, and `HR_MANAGER_PASSWORD` through the platform's secret manager. Never expose these values in Angular runtime configuration.
- Serve the SPA and API through HTTPS. Configure a same-origin reverse proxy for `/api` to the Spring Boot service, or explicitly configure and test a narrowly scoped CORS policy if separate origins are required. HTTP Basic must not traverse plaintext HTTP.
- Set `frontend/public/app-config.js`'s `window.salaryManagementConfig.apiBaseUrl` to the deployed API path (prefer `/api` behind the same origin). This file is public configuration and must contain no credentials or secrets.
- Keep `/actuator/health` available to the platform health probe, while ensuring employee and compensation routes remain authenticated. Review log configuration to avoid credentials and salary values.
- Build from the committed lockfile with Java 17 and the supported Node/Angular toolchain, run frontend checks and `mvn clean verify` with PostgreSQL integration tests enabled, then verify login, search, profile edit, compensation update, and audit history in the deployed environment using synthetic data.
- Configure domain, TLS certificates, database backup/restore, monitoring, and an operational HR credential rotation procedure. Confirm the chosen provider, cost, data residency, and access controls before storing employee data.

Required decision before deployment: choose and authorize the application/database hosting platform and domain. Deployment evidence and the demo video are still outstanding.
