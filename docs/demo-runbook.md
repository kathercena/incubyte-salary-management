# HR workflow demo runbook

This runbook describes a local demo using synthetic employees and the existing HTTP Basic MVP. It is a screen-flow guide, not evidence that a video has been recorded.

## Start and sign in

Follow the README local development steps to start PostgreSQL, configure `POSTGRES_PASSWORD`, `HR_MANAGER_USERNAME`, `HR_MANAGER_PASSWORD`, and `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` in the backend terminal, then start Spring Boot. In a separate terminal run `npm start` from `frontend/` and open `http://localhost:4200`. Sign in using the locally configured HR Manager credentials. Do not record or share the credentials.

For a repeatable dataset, follow the README's **Explicit synthetic employee seed** instructions using its disposable PostgreSQL container. The explicit seed creates exactly 10,000 synthetic employee rows and current compensation values; it is not run during ordinary application startup. Do not run the seed against the persistent Compose database for a demo unless intentionally using that development database.

## Suggested screen flow and talking points

1. Show the employee directory and its count. Search by a name, then by employee number; combine country and department filters. Point out that pagination and allowlisted sort fields are requested from the server.
2. Open an employee and review profile details, compensation with its currency and period, version, and salary audit history. The UI does not combine incompatible currencies or periods.
3. Choose **Add employee**. Enter synthetic profile data plus initial amount, currency, and salary period. Mention the uniqueness and validation checks enforced by the API. On success, the new employee is selected in the directory.
4. Edit the profile and save. Show that the compensation fields are separate from the profile request and current pay is unchanged.
5. Edit compensation with a decimal amount. Explain that the browser carries the amount as decimal text, and the backend stores it as PostgreSQL `NUMERIC`; show the resulting history entry.
6. To demonstrate concurrency, submit an update with an outdated version through the API or a second authorized session. The API rejects the stale write; the UI requires a refresh before retrying.
7. Sign out and show that employee information and in-memory credentials are cleared.

Use only synthetic data. Never show credentials in the recording. The demo video remains an assessment deliverable to record separately.
