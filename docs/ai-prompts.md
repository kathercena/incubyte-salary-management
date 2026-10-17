# Selected AI collaboration prompt excerpts

These are short **verbatim excerpts** from user instructions in the available Codex conversation history. The descriptions of their purpose are editorial summaries. This file is a curated set of examples, not an exhaustive transcript; no credentials or secret values are included.

| Area | Verbatim prompt excerpt | Engineering problem addressed |
| --- | --- | --- |
| Stack and compatibility | "Use Java 17 consistently. Do not change the project to Java 21 or install more tools without a demonstrated need." | Kept implementation and troubleshooting within the verified Java 17 environment. |
| Evidence discipline | "Do not claim tests, builds, deployment or commits succeeded unless verified." | Required reports and command output to support verification claims. |
| Test quality | "Do not silently skip the tests or weaken the test setup." | Kept PostgreSQL/Testcontainers integration tests meaningful when Docker discovery failed. |
| Money precision | "Determine whether the current API/UI can faithfully preserve every amount permitted by the API contract." | Triggered tracing monetary values across `BigDecimal`, JSON serialization, JavaScript, display, and edit/save behavior; the supported API response uses decimal strings with regression coverage. |
| Concurrency | "if the backend reports a stale version, inform the user and require a refresh rather than silently overwriting changes." | Preserved optimistic concurrency semantics through the UI and API. |
| Database safety | "If the target, migration version, or initial count differs, STOP without writing anything." | Required a read-only target/version/count gate before the explicitly approved synthetic seed. |
| TLS | "Never fall back to `sslmode=require` or disable certificate validation." | Kept Neon JDBC hostname and certificate verification enabled with `verify-full`. |
| Windows timezone diagnosis | "after setting `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` in the PowerShell session." | Recorded the host-specific timezone workaround used when PostgreSQL rejected the Windows timezone alias. |
| Scope control | "Do not modify source code, configuration, existing documentation, tests, or deployment resources." | Kept the AI artifact task limited to these documentation files. |

The project evidence corresponding to these requests is summarized in [`ai-collaboration.md`](ai-collaboration.md), with links to the implementation and test locations. Prompt excerpts express user constraints and goals; they do not by themselves prove that a check passed or an operation completed.
