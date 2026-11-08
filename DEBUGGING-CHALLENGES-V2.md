
## Delivery verification

Verified on November 5, 2026, using Java 21 and PostgreSQL 16.13 with isolated processes and databases:

- Before introducing the incidents, all 75 existing backend tests and 24 shipping tests passed.
- 70 scenario checks were run using HTTP and SQL: 10 against the healthy baseline, 10 with all incidents enabled, and five rounds of 10 retaining the five original incidents and enabling one new incident at a time. In those rounds, the other four new scenarios continued to behave correctly.
- The delivered version contains all ten incidents simultaneously, compiles, and builds its JAR. Both services start; readiness returns `UP`, OpenAPI returns `200`, and all four Flyway migrations apply successfully.
- In the intentionally defective version, the 75 existing backend tests were run: 57 passed and 18 failed due to the incidents; none were skipped and the test runner reported no execution errors. `./mvnw.cmd test` and `./mvnw.cmd failsafe:integration-test failsafe:verify` were run separately so unit-test failures would not prevent integration tests from running. Both commands exit with test failures, as expected for this exercise.
- Tests, migrations, Compose files, and the remote service were not modified. HTTP/SQL reproduction also covers scenarios not detected by the existing test suite.

The processes, containers, and data used to verify this delivery were temporary and have been removed. To practice, start the isolated environment described above and create your own data. Changes remain uncommitted in this worktree; nothing has been pushed. Verification probes and code copies used during testing are not included.
