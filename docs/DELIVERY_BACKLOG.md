# Delivery Backlog – **Monzo Domain Crawler (MDC)**

## Legend

| Field                        | Meaning                                                 |
| ---------------------------- | ------------------------------------------------------- |
| **ID**                       | Short ticket code (`MDC-#`).                            |
| **Story**                    | “As a … I want … so that …”.                            |
| **Acceptance Criteria (AC)** | Definition of Done.                                     |
| **Priority**                 | **M**ust · **S**hould · **C**ould · **W**on't (MoSCoW). |
| **Size**                     | T‑shirt: **XS**, **S**, **M**, **L**                           |
| **Status**                   | **Not started** · **In progress** · **Done**.           |

---

## Core Scope 

| ID         | Story                                    | AC (summarised)                                      | Priority | Size | Status      |
| ---------- | ---------------------------------------- | ---------------------------------------------------- | -------- | ---- | ----------- |
| **MDC‑1**  | Gradle 21 skeleton                       | `build.gradle`; `./gradlew test` green               | M        | XS   | Done |
| **MDC‑2**  | `docker‑compose.yml` (crawler and redis) | Two services; Redis health‑check; README quick‑start; Dockerfile | M        | XS   | Done |
| **MDC‑3**  | `FrontierQueue` abstraction              | `push`, `pop`, `isEmpty`; Javadoc                    | M        | XS   | Done |
| **MDC‑4**  | `RedisFrontierQueue` impl + dedupe       | `LPUSH`/`BRPOP`; atomic dedupe via `SADD` (Lua)      | M        | S    | Not started |
| **MDC‑5**  | Simple config constants                  | Hard‑coded defaults; overridable via env vars        | M        | XS   | Not started |
| **MDC‑6**  | Concurrency via virtual threads          | Fixed-size pool; configurable parallelism            | M        | S    | Not started |
| **MDC‑7**  | HTML fetch + parse                       | Java `HttpClient`; Jsoup extracts absolute links     | M        | S    | Not started |
| **MDC‑8**  | Same‑domain filter                       | Enqueue only links whose host endsWith(seedHost)     | M        | XS   | Not started |
| **MDC‑9**  | Output results                           | Print visited URL and its links to stdout            | M        | XS   | Not started |
| **MDC‑10** | Basic robots.txt check                   | `crawler-commons`; fetch once/host; 5 s timeout      | M        | S    | Not started |
| **MDC‑11** | Unit tests (queue + parser)              | JUnit; target coverage ≥50 %                         | M        | XS   | Not started |
| **MDC‑12** | README “Notable Decisions”               | How to run; trade‑offs; future work list             | M        | XS   | Not started |


---

## Nice‑to‑Have Enhancements

| ID         | Story                         | AC (summarised)                    | Priority | Size | Status      |
| ---------- | ----------------------------- | ---------------------------------- | -------- | ---- | ----------- |
| **MDC‑13** | Integration tests              |  Testcontainers           | S        | S   | Not started |
| **MDC‑14** | GitHub Actions CI             | `gradle build` workflow            | S        | XS   | Not started |
| **MDC‑15** | Graceful shutdown             | SIGTERM stops intake; await ≤2 s   | S        | S    | Not started |
| **MDC‑16** | Prometheus metrics endpoint   | Micrometer core; expose `/metrics` | S        | S    | Not started |

