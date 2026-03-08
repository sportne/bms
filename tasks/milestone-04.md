# Milestone 04: C++ Conditional Parity

This milestone closes the remaining C++ parity gap for conditional constructs.

## Target outcome

- C++ generator emits working conditional encode/decode behavior that matches Java semantics.

## Work items

- [ ] Implement C++ encode/decode emission for:
  - [ ] `varString`
  - [ ] `pad`
  - [ ] `checksum`
  - [ ] `if`
  - [ ] nested `type`
- [ ] Match Java checksum semantics for:
  - [ ] `crc16`
  - [ ] `crc32`
  - [ ] `crc64`
  - [ ] `sha256`
- [ ] Match Java conditional semantics for:
  - [ ] structured comparisons
  - [ ] text `and`/`or` compound conditions
  - [ ] rejection diagnostics for unsupported forms
- [ ] Add C++ generator goldens for conditional fixtures.
- [ ] Add generated-C++ compile tests for conditional fixtures.
- [ ] Add generated-C++ runtime tests for:
  - [ ] branch true/false paths
  - [ ] checksum mismatch failures

## Acceptance checks

- [ ] `bms generate --cpp` succeeds for `varstring-pad-slice-valid.xml`.
- [ ] `bms generate --cpp` succeeds for `conditional-backend-valid.xml`.
- [ ] C++ runtime tests pass for conditional fixtures.
