# Milestone 04: C++ Conditional Parity

This milestone closes the remaining C++ parity gap for conditional constructs.

## Target outcome

- C++ generator emits working conditional encode/decode behavior that matches Java semantics.

## Work items

- [x] Implement C++ encode/decode emission for:
  - [x] `varString`
  - [x] `pad`
  - [x] `checksum`
  - [x] `if`
  - [x] nested `type`
- [x] Match Java checksum semantics for:
  - [x] `crc16`
  - [x] `crc32`
  - [x] `crc64`
  - [x] `sha256`
- [x] Match Java conditional semantics for:
  - [x] structured comparisons
  - [x] text `and`/`or` compound conditions
  - [x] rejection diagnostics for unsupported forms
- [x] Add C++ generator goldens for conditional fixtures.
- [x] Add generated-C++ compile tests for conditional fixtures.
- [x] Add generated-C++ runtime tests for:
  - [x] branch true/false paths
  - [x] checksum mismatch failures

## Acceptance checks

- [x] `bms generate --cpp` succeeds for `varstring-pad-slice-valid.xml`.
- [x] `bms generate --cpp` succeeds for `conditional-backend-valid.xml`.
- [x] C++ runtime tests pass for conditional fixtures.

## Evidence snapshot

- `CppCodeGeneratorTest` contains deterministic golden checks for `varstring-pad`, `conditional-backend`, and relational conditional fixtures.
- `CppGeneratedCompileTest` compiles generated C++ for conditional fixtures and checksum algorithm fixtures (`crc32`, `crc64`, `sha256`).
- `CppGeneratedRuntimeE2ETest` verifies conditional roundtrips, true/false branch behavior, and checksum mismatch exceptions.
