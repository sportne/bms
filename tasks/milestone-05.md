# Milestone 05: Runtime Conformance + 0.1.0 Release Hardening

This milestone prepares the project to ship `0.1.0` after parity work is done.

## Target outcome

- Java and C++ are proven consistent for canonical fixtures.
- Release process is repeatable and documented.

## Work items

- [ ] Build cross-language conformance tests:
  - [ ] same logical inputs produce same byte arrays in Java and C++
  - [ ] decode parity checks on shared fixtures
- [ ] Add CI wiring for generated-C++ runtime tests.
- [ ] Update README support table to exact shipped behavior.
- [ ] Freeze and verify `tasks/fixture-matrix-0.1.0.md`.
- [ ] Complete `tasks/release-0.1.0-checklist.md`.

## Acceptance checks

- [ ] Gate 1 (Milestone 03) passes.
- [ ] Gate 2 (Milestone 04) passes.
- [ ] Gate 3 (cross-language conformance) passes.
- [ ] Gate 4 (`./gradlew check` + native CI) passes.
