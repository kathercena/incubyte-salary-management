# AI collaboration record

## Scope

Codex was used as an implementation and review assistant for this assessment. The user supplied the requirements, stack constraints, milestone boundaries, safety rules, and approval decisions. AI assistance was directed at concrete repository work rather than autonomous product definition. This is a concise record of the available project history, not a complete transcript of every interaction.

## Where AI assistance was applied

- **Planning and architecture:** translated the assessment into a Java 17/Spring Boot modular monolith, Angular client, PostgreSQL schema, Flyway migration sequence, and reviewable milestones. The scope kept one HR Manager role, current compensation, explicit currency/period, and synthetic employee data; payroll, tax calculations, and FX conversion remained out of scope. See [`requirements-and-plan.md`](../requirements-and-plan.md).
- **API and persistence implementation:** assisted with employee and compensation DTOs, validation, filtering, paging, sorting allowlists, consistent API errors, HTTP Basic security, optimistic version checks, and append-only salary audit records. Human review was required for domain behavior and security boundaries. Evidence is in `backend/src/main/java/com/incubyte/salarymanagement/` and Flyway migrations under `backend/src/main/resources/db/migration/`.
- **Monetary precision:** review traced `BigDecimal` through JSON and JavaScript. Because JSON numeric values can lose precision in JavaScript, response amounts and Angular form values use decimal strings. Regression coverage checks a high-precision supported amount in `frontend/src/app/app.spec.ts` and backend API tests.
- **Concurrency and audit:** stale versions are rejected rather than silently overwriting another update; compensation updates persist audit entries transactionally. The employee API integration tests cover conflict behavior, audit history, and persistence.
- **Deterministic seed:** assistance implemented/reviewed an explicit one-shot launcher and transactional, fixed dataset for 10,000 synthetic employees. It is not invoked during normal application startup, recognizes an exact complete rerun as a no-op, and rejects partial/conflicting identities. Evidence is in `backend/src/main/java/com/incubyte/salarymanagement/seed/`, its integration tests, and the README seed instructions.
- **Troubleshooting and verification:** prompts and tool output were used to investigate an Angular Windows native build crash, test compilation, Testcontainers Docker discovery, Windows temporary-file permissions, Maven repository permissions, and PostgreSQL timezone handling. Changes were limited to evidence-supported fixes; dependency downgrades and weakening tests were rejected. The README documents the PowerShell `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` workaround for the host timezone alias.
- **Deployment preparation:** assistance reviewed the Render Blueprint, Java 17 non-root container, frontend runtime API configuration, exact-origin CORS, environment-based secrets, and pgJDBC `sslmode=verify-full` configuration. The live Render/Neon setup and manual browser exercise were user-reported; the AI did not create cloud resources in the deployment-readiness review.

## Human review and approval

The user chose the MVP scope and required Java 17, synthetic-only data, forward-only migrations, explicit reviewable commits, and no pushes without review. They reviewed and authorized local milestone commits individually. The user also explicitly approved the Neon seed only after requiring a read-only TLS, target, migration-version, and empty-table preflight. Production access and seeding were not inferred from ordinary implementation requests. No credentials are included in this record.

Product decisions requiring human ownership included the single HR Manager authentication model, salary periods, supported countries/currencies, deployment provider, synthetic-only demo posture, and the explicit approval boundary for production database writes.

## Verification and evidence provenance

| Claim | Evidence and source |
| --- | --- |
| Frontend build, TypeScript, and 24 frontend tests | Local command results in the project work history; Angular test files are `frontend/src/app/app.spec.ts` and `frontend/src/app/core/employee-api.service.spec.ts`. |
| Backend 23 tests, zero failures/errors/skips | Available Surefire reports: 15 employee API integration tests and 8 seed integration tests, each with zero failures, errors, and skips. The associated successful Maven lifecycle with Java 17 and the UTC environment option is recorded in the supplied verification history. Tests are configured for isolated PostgreSQL Testcontainers databases. |
| Neon target, migration, and seeded row checks | A read-only pgJDBC preflight and verification output in the interaction history reported `verify-full`, `neondb`, Flyway version 2, 10,000 employees, and 10,000 distinct IDs, employee numbers, and emails with compensation data. No password or connection string is reproduced here. |
| Deployed health and HR workflow | Manually tested and reported by the user: Render frontend/backend, health `UP`, sign-in, directory, profile editing, compensation editing, and audit history. These are manual live checks, not automated test results from this document-writing task. |
| Seed command process exit | The final read-only database check confirmed the complete 10,000-row state. An earlier wrapper did not preserve the seeder process exit code; this record therefore does not claim a particular exit code for that invocation. |

The record distinguishes repository evidence and automated output from user-reported manual verification. The assessment demo video was recorded and uploaded, as reported by the user, and its Google Drive link is included in the README. External reviewer access to that link has not been independently tested. This document does not claim that every test or deployment action was run during one session.

## Limitations and lessons

- Windows execution environments exposed tool-specific failures: Node's native Angular build crashed in the original checkout while a matching external copy built; Vitest needed a writable temporary directory; and Docker named-pipe permissions sometimes prevented Testcontainers discovery. These incidents were diagnosed separately from application logic, and successful checks are reported only when output or reports support them.
- Maven `BUILD SUCCESS` alone was not treated as proof of test execution; Surefire executed/skipped counts were checked. Testcontainers tests are not equivalent to mock-only tests.
- Read-only Neon checks were separated from the explicitly approved seed operation. The transcript does not support a claim about the seed process exit code, so only the resulting database state is reported.
- The deployment uses MVP HTTP Basic authentication and free-tier services. It is for synthetic assessment data and is not presented as a production-ready HR security or availability posture.
- The demo runbook is a written guide, not the video itself. The separately recorded and uploaded demo is linked from the README; external reviewer access has not been independently tested. Dedicated AI workflow artifacts were absent before this record was created.

## Related artifacts

- [`ai-prompts.md`](ai-prompts.md) contains selected prompt excerpts that are actually present in the conversation history. They are not a complete transcript.
- [`demo-runbook.md`](demo-runbook.md) describes the proposed HR workflow recording.
- [`deployment-readiness.md`](deployment-readiness.md) records the current deployed status and remaining operational/assessment limitations.
