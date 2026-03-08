# Milestone 05: Runtime Conformance + 0.1.0 Release Hardening

This milestone prepares the project to ship `0.1.0` after parity work is done.

## Target outcome

- Java and C++ are proven consistent for canonical fixtures.
- Release process is repeatable and documented.

## Work items

- [x] Build cross-language conformance tests:
  - [x] same logical inputs produce same byte arrays in Java and C++
  - [x] decode parity checks on shared fixtures
- [x] Add CI wiring for generated-C++ runtime tests.
- [x] Update README support table to exact shipped behavior.
- [x] Freeze and verify `tasks/fixture-matrix-0.1.0.md`.
- [ ] Complete `tasks/release-0.1.0-checklist.md`.

## Acceptance checks

- [x] Gate 1 (Milestone 03) passes.
- [x] Gate 2 (Milestone 04) passes.
- [x] Gate 3 (cross-language conformance) passes.
- [ ] Gate 4 (`./gradlew check` + native CI) passes.

## Evidence snapshot

- `CrossLanguageConformanceTest` now checks all required valid fixtures:
  - foundation, numeric, collections, conditionals, checksum algorithms, mixed all-supported, and coverage-heavy fixture.
- Conformance protocol per case:
  - Java encode bytes == C++ encode bytes
  - C++ decode assertions run on Java bytes
  - Java decode assertions run on C++ bytes
- Local `./gradlew check --no-daemon` passes with the conformance suite included.
