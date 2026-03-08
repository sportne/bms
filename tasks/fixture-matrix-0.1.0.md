# Fixture Matrix For 0.1.0

This file freezes which fixtures define parity gates for `0.1.0`.

## Rules

- Do not remove fixtures in this list during `0.1.0` work.
- New fixtures are allowed, but this baseline must keep passing.
- Java and C++ should both pass the same construct categories where supported.

## Foundation

- `valid-foundation.xml`
- `missing-schema-namespace.xml`
- `invalid-root.xml`
- `unknown-field-type.xml`

## Numeric

- `numeric-slice-valid.xml`
- `field-invalid-endian.xml`

## Collections

- `collections-slice-valid.xml`
- `conditional-if-relational-invalid.xml` (range/validation coverage for conditions used in collections-heavy flows)

## Conditionals

- `varstring-pad-slice-valid.xml`
- `conditional-backend-valid.xml`
- `conditional-if-relational-valid.xml`
- `conditional-if-unsupported-test.xml`
- `if-invalid-logical-operator.xml`

## Checksum algorithms

- `checksum-crc16-valid.xml`
- `checksum-crc32-valid.xml`
- `checksum-crc64-valid.xml`
- `checksum-sha256-valid.xml`
- `checksum-invalid-range.xml`
- `checksum-crc64-invalid-range.xml`
- `checksum-sha256-invalid-range.xml`

## Full mixed coverage

- `java-e2e-all-supported-valid.xml`
- `java-generator-coverage-valid.xml`

## Unsupported/fail-fast parser behavior

- `unsupported-root-unknown.xml`
- `unsupported-message-unknown.xml`

## Mapping to gates

- Gate 1 (C++ numeric/collection parity): foundation + numeric + collections fixtures
- Gate 2 (C++ conditional parity): conditional + checksum fixtures
- Gate 3 (cross-language conformance): full mixed coverage fixtures
- Gate 4 (`0.1.0` release): all sections above must stay green in CI
