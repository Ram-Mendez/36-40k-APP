# Daily backend lab — operating contract

## Scope

- Repository: `Ram-Mendez/36-40k-APP`.
- Control branch: `training/control`.
- Initial main snapshot inspected on 2026-11-09: `a6bc8058415a21c3dc75564974e375e69bdab9a9`.
- Target role: backend Java / Spring Boot / PostgreSQL, Spanish market, 36–40k EUR.
- Initial observed stack: Java 21, Spring Boot 3.5.12, Maven, PostgreSQL 16, core API and independent shipping service. Re-read the actual files before every change; README claims are not execution evidence.
- The user has one incident left from an earlier batch. Its identity and unpushed local changes are unknown. Do not mark it solved, modify the earlier batches, or depend on its completion.
- This setup commit changes documentation only. No application code, tests, runtime, database, existing branches or tags have been changed or validated by this setup.

## Write boundaries

1. Writes are authorized only in this repository and in automation-owned `training/control` and NEW `training/YYYY-MM-DD/*` branches created by this task. Existing branches with that prefix must be inspected before reuse; do not assume ownership.
2. Never push or merge into `main`, other user branches, or the existing exercise tags `ejercicios/debug-v1` and `ejercicios/debug-v2`. Never force-push, reset, delete branches/tags, overwrite user commits, modify the user's Windows worktrees, or erase volumes/data.
3. Read the remote branch head again before each ref update and use an expected-head check where supported. On concurrent changes, stop that update and report the conflict.
4. Do not create paid services, purchase credits, change billing, deploy to production, use external customer data, or use production secrets. Any test environment must be disposable and local to its runner.
5. Do not trigger the full Jenkins/Kind pipeline for every generated branch. Prefer lightweight validation in an available isolated environment; keep CI changes limited to training branches and report authorization or execution blockers.

## Batch construction

Create one date-keyed batch per run, in Europe/Madrid time. Read the repository, earlier batch manifests and the control index first. A retry must resume the same batch safely rather than create duplicates or overwrite user work.

Use an explicit baseline commit selected from the current integrated application. Do not assume that a historical README, old CI run or the current main branch proves the baseline healthy. Reuse a previously validated baseline when appropriate and record its SHA and evidence; never silently stack unresolved intentional faults from yesterday onto a new batch. User fixes may be incorporated only when visible in GitHub and verified, without merging into main automatically.

Create these independent branches from the declared baseline:

- `training/YYYY-MM-DD/incidents`: five new intentional, realistic, independent backend defects, plus the incident tickets and batch manifest.
- `training/YYYY-MM-DD/team-a`: a small, coherent business change from a simulated teammate, plus its change request.
- `training/YYYY-MM-DD/team-b`: a second small, coherent change supporting the day's Git integration exercise. Occasionally introduce a controlled, resolvable overlap with team-a; do not manufacture a conflict every day.

All three branches must contain actual useful code changes, not documentation-only placeholders. Do not publish a successful batch summary until the remote branch heads and the changed code files have been confirmed. If only some work is completed, publish that exact partial state and the blocker.

Do not create a pull request targeting main with intentional regressions. Do not merge the branches for the user. If CI requires a PR and authorization permits it, use a separately created training-only baseline branch and a clearly marked draft PR; avoid unnecessary pipeline runs.

## Workload and incident selection

Each batch contains exactly five incident tickets, one bounded business/change request and one Git integration exercise, designed as a queue rather than an obligation to finish everything that day. Aim for two straightforward, two intermediate and one more demanding incident, calibrated from the user's visible progress.

Rotate through existing code and real domain behavior: SQL/JdbcTemplate, joins and aggregates, pagination and ordering, transaction boundaries/rollback, concurrency and inventory integrity, authorization, validation/error contracts, REST dependency failures, idempotency, date/money handling, integration tests, observability and query performance. Prioritize backend work; no frontend exercises and no infrastructure rebuilds for their own sake.

Avoid repeating the same fault mechanism with renamed entities. Review prior batches and exercise history, including INC-001 through INC-010 where available. New IDs use `LAB-YYYYMMDD-01` through `LAB-YYYYMMDD-05` to avoid collisions.

The application should still compile and start; defects should be observed through legitimate business requests, results, persisted state or controlled concurrent operations, rather than trivial compilation errors. Do not disable authentication globally, expose the service publicly or seed destructive behavior. Preserve existing migrations, tests and their assertions; failures may be expected, but do not hide them to obtain green CI.

No fault labels, solution comments, explanatory commit messages, root-cause maps or teacher-answer files in student branches. Git history is inherently inspectable: do not claim that the diff can be hidden from the repository owner.

## Ticket format

Write concise professional Spanish, as work items from support, QA or another developer. Use only what a colleague could genuinely report:

- ID and title, priority, affected environment/build.
- Observed behavior and business impact.
- Minimum necessary request/data/reproduction conditions, when known.
- Expected behavior and objectively verifiable acceptance criteria.
- Actual available evidence (HTTP response, requestId, query result or logs).

No 'contexto amigable', motivational introductions, tutorial sections, difficulty explanations, suggested solution, faulty class/method/line, answer key or spoon-fed debugging checklist. Distinguish a designed reproduction from a reproduction actually executed. Never fabricate logs, timings, request IDs, IDs of remote resources or test results.

The daily delivery contains `docs/training/YYYY-MM-DD/TICKETS.md`, `MANIFEST.md` and `VALIDATION.md` on the incidents branch, with the branch names and SHAs, baseline, statuses and safe retrieval instructions. State external behavior without explaining the defect implementation.

For Git, give the outcome required (integrate two changes, retain both behaviors, backport one change, or revert a faulty release), not a full solved command sequence. Rotate fetch/switch/tracking, pull --ff-only, merge/conflict resolution, cherry-pick, revert and bisect. Do not recommend pulling unrelated branches into the active main branch.

Provide safe PowerShell entry commands using a NEW worktree after fetching, with real refs resolved from this delivery. If a destination already exists, do not delete or overwrite it. Keep runtime ports, Compose project names and test databases separate from the user's existing 8086/9096/5436 environment; do not stop their running services.

## Validation

Attempt baseline checks, compilation, startup, isolated reproduction of each defect and preservation of unrelated behavior using the available execution environment. Run existing unit/integration tests when possible; PostgreSQL-backed integration coverage must not be replaced with H2. Separate compilation failure, expected regression test failure, unexpected test failure and environmental blockage.

Each validation item must be labeled `PASS`, `FAIL_EXPECTED`, `FAIL_UNEXPECTED` or `NOT_EXECUTED`, with command, commit SHA and actual evidence. Do not present unexecuted changes as validated, or a partially prepared batch as five verified bugs. If execution is unavailable, code may be published as `UNVALIDATED` with explicit warnings and commands to validate; do not invent success.

After writing, fetch the changed files and branch heads to verify publication. Record actual links/SHAs and report access/approval/quota blockers. An inaccessible connector is not permission to bypass authorization.

## Engineering baseline and source register

Apply current production-oriented engineering rather than an imaginary universal '2026 standard': compatible supported dependencies, incremental maintainable design, precise API contracts, real PostgreSQL behavior, authorization boundaries, reversible schema evolution, reproducible tests, bounded timeouts/retries and useful observability. Do not upgrade frameworks indiscriminately or add Kafka, AI, Kubernetes or more microservices without a concrete requirement.

The initial application pins Spring Boot 3.5.12. Compare its maintained patch line and compatibility against current official documentation before proposing a separate maintenance change; do not silently switch this lab to Spring Boot 4 or another JDK.

Official reference entry points, checked during setup on 2026-11-09 (recheck relevance and versions at use time):

- Spring Boot production features: https://docs.spring.io/spring-boot/reference/actuator/index.html
- Spring Boot / Testcontainers: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
- Spring support policy: https://spring.io/support
- PostgreSQL isolation: https://www.postgresql.org/docs/current/transaction-iso.html
- OWASP API Security: https://owasp.org/API-Security/editions/2023/en/0x11-t10/

The user's request to consider 1,000 real projects is a target for an evidence-backed reference corpus, NOT a claim that 1,000 repositories have already been reviewed. Start the verified external-project count at zero. Progressively inspect up to three relevant production-oriented public repositories per run when useful, de-duplicate them, and record repository URL, inspected paths/commit, inspection date, pattern observed, applicability and limitations in `docs/training/REFERENCE-CORPUS.md` on the control branch. Metadata-only listings and tutorials do not count as inspected real projects; distinguish demos from deployed open-source products. Do not copy incompatible licensed code, infer private commercial experience, or claim statistical representativeness.

Stop counting at the actual number evidenced; report that number honestly rather than filling a quota. The quality and reproducibility of this user's exercises take priority over growing the corpus.

## State and delivery

Maintain `docs/training/INDEX.json` on the control branch with date, baseline SHA, published branches/SHAs, ticket IDs and topic fingerprints, validation status, known blockers and verified external-project count. Never record unknown user completion as done. Check remote state on every run, not just this index.

Notify the user with a short Spanish delivery: batch date, actual branches, ticket-document location, verification status and any blocking condition. Do not reveal causes or fixes. Do not claim that their local application changed, that their machine must stay on, or that a scheduled run is guaranteed to succeed despite permissions, approvals or quota.
