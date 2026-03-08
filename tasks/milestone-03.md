# Milestone 03: C++ Numeric + Collection Parity

This milestone upgrades C++ from foundation scaffold to working numeric and collection backend output.

## Target outcome

- C++ generator emits real encode/decode logic for numeric and collection members.
- Unsupported diagnostics are removed for member kinds covered in this milestone.

## Work items

- [x] Implement C++ encode/decode emission for:
  - [x] `bitField`
  - [x] `float`
  - [x] `scaledInt`
  - [x] `array`
  - [x] `vector`
  - [x] `blobArray`
  - [x] `blobVector`
- [x] Support reusable numeric and collection refs via `field@type`.
- [x] Keep deterministic output ordering and formatting.
- [x] Add C++ generator goldens for numeric/collection fixtures.
- [x] Add generated-C++ compile tests for numeric/collection fixtures.
- [x] Add generated-C++ runtime roundtrip tests for numeric/collection fixtures.

## Acceptance checks

- [x] `bms generate --cpp` succeeds for `numeric-slice-valid.xml`.
- [x] `bms generate --cpp` succeeds for `collections-slice-valid.xml`.
- [x] Generated C++ compiles in CI for milestone fixtures.
- [x] Runtime roundtrip tests pass for milestone fixtures.

## Evidence snapshot

- `CppCodeGeneratorTest` has deterministic golden checks for numeric and collection fixtures.
- `CppGeneratedCompileTest` compiles generated numeric and collection C++ with a C++20 compiler.
- `CppGeneratedRuntimeE2ETest` runs encode/decode roundtrip checks for numeric and collection fixtures.
