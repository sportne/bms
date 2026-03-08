# Roadmap: 0.1.0 To 1.0

This roadmap turns the high-level plan into clear, testable milestones.

## Release intent

- `0.1.0` is the first **near-parity** release:
  - Java stays fully working.
  - C++ closes the major backend gaps.
  - Runtime roundtrip tests are required for generated C++.
- `1.0.0` is a **stability** release:
  - stronger compatibility guarantees
  - hardened diagnostics and release process

## Milestone A: C++ numeric + collection parity

Goal: C++ can generate working encode/decode for numeric and collection members.
Status: complete

Scope:

- `bitField`, `float`, `scaledInt`
- `array`, `vector`, `blobArray`, `blobVector`
- reusable type refs used through `field@type`

Acceptance gate:

- C++ generation succeeds for numeric/collection fixtures.
- Generated C++ compiles.
- Runtime roundtrip tests pass for these constructs.

## Milestone B: C++ conditional parity

Goal: C++ matches Java behavior for conditional members.
Status: complete

Scope:

- `varString`, `pad`, `checksum`, `if`, nested `type`
- checksum algorithms and range behavior
- comparison operators and `and`/`or` logic in conditions

Acceptance gate:

- C++ generation succeeds for conditional fixtures.
- Generated C++ compiles.
- Runtime tests pass for branch behavior and checksum mismatch failures.

## Milestone C: runtime conformance harness

Goal: prove Java and C++ behave the same for shared fixtures.
Status: complete (local and test-suite verified)

Scope:

- compile generated C++ for canonical fixtures
- run encode/decode roundtrip tests in C++
- run cross-language conformance checks

Acceptance gate:

- same spec + same logical values produce the same bytes in Java and C++
- decode parity checks pass for canonical fixtures

## Milestone D: 0.1.0 release hardening

Goal: ship `0.1.0` with repeatable release steps and clear support statements.
Status: in progress (waiting on release-commit CI and tag)

Scope:

- docs updated to exact supported behavior
- fixture matrix frozen for parity checks
- release checklist completed

Acceptance gate:

- `./gradlew check` passes in CI
- native-image CI job is green
- fixture matrix and release checklist are complete

## Path to 1.0.0

After `0.1.0`:

- tighten compatibility from flexible to semver-stable
- improve diagnostic precision and CLI error summaries
- add release/migration playbooks for long-term maintenance
