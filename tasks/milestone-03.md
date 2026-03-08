# Milestone 03: C++ Numeric + Collection Parity

This milestone upgrades C++ from foundation scaffold to working numeric and collection backend output.

## Target outcome

- C++ generator emits real encode/decode logic for numeric and collection members.
- Unsupported diagnostics are removed for member kinds covered in this milestone.

## Work items

- [ ] Implement C++ encode/decode emission for:
  - [ ] `bitField`
  - [ ] `float`
  - [ ] `scaledInt`
  - [ ] `array`
  - [ ] `vector`
  - [ ] `blobArray`
  - [ ] `blobVector`
- [ ] Support reusable numeric and collection refs via `field@type`.
- [ ] Keep deterministic output ordering and formatting.
- [ ] Add C++ generator goldens for numeric/collection fixtures.
- [ ] Add generated-C++ compile tests for numeric/collection fixtures.
- [ ] Add generated-C++ runtime roundtrip tests for numeric/collection fixtures.

## Acceptance checks

- [ ] `bms generate --cpp` succeeds for `numeric-slice-valid.xml`.
- [ ] `bms generate --cpp` succeeds for `collections-slice-valid.xml`.
- [ ] Generated C++ compiles in CI for milestone fixtures.
- [ ] Runtime roundtrip tests pass for milestone fixtures.
