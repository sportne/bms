# Milestone 02: Front-End Foundation + Numeric + Collections + Conditional Slice

This milestone builds the compiler front end in small, testable steps.

## Completed

- [x] Create Gradle project skeleton.
- [x] Add CLI entry point (`validate`, `generate`).
- [x] Implement XSD validator service.
- [x] Implement XML parser for root schema.
- [x] Implement parser for `messageType` and `field`.
- [x] Implement parser for `bitField`, `float`, `scaledInt`.
- [x] Implement parser for `array`, `vector`, `blobArray`, `blobVector`, and recursive `terminatorField`.
- [x] Implement parser for `varString`, `pad`, `checksum`, `if`, and nested `type`.
- [x] Create parsed model classes.
- [x] Create resolved model classes.
- [x] Implement reference resolution for primitives, message types, and reusable numeric types.
- [x] Extend reference resolution so `field@type` can point to reusable collection definitions.
- [x] Implement semantic validation for namespaces, duplicate names, unknown references, and numeric slice rules.
- [x] Implement semantic validation for collection rules, count-field references, and terminator-path structure.
- [x] Implement semantic validation for conditional/nested structures and varString count-field rules.
- [x] Add parser/semantic/CLI/generator tests for the supported slice.

## Acceptance Checks For Current Slice

- [x] `bms validate` succeeds for foundation specs.
- [x] `bms validate` succeeds for numeric-slice specs (`bitField`, `float`, `scaledInt`).
- [x] `bms validate` succeeds for collection-slice specs (`array`, `vector`, `blobArray`, `blobVector`, `terminatorField`).
- [x] `bms validate` succeeds for conditional-slice specs (`varString`, `pad`, `checksum`, `if`, nested `type`).
- [x] Message member declaration order is preserved exactly in parsed and resolved models.
- [x] Java/C++ generation is deterministic.
- [x] Java generator emits deterministic code for numeric and collection backend slices.
- [x] Java generator emits deterministic conditional code for `varString`, `pad`, `checksum`, `if`, and nested `type`.
- [x] Java generator supports checksum algorithms `crc16`, `crc32`, `crc64`, and `sha256`.
- [x] Java generator supports `if@test` operators `==`, `!=`, `<`, `<=`, `>`, and `>=` for numeric literals.
- [x] Java generator and semantic validation report clear diagnostics for invalid conditional combinations (for example legacy `&&`/`||` text operators or invalid checksum ranges).
- [x] Generated Java source is validated end-to-end by compiling generated classes and running encode/decode roundtrip tests.
- [x] C++ generator still fails with clear diagnostics for non-foundation backend emission.

## Next Milestones

- Milestone 03: `tasks/milestone-03.md` (C++ numeric + collection parity)
- Milestone 04: `tasks/milestone-04.md` (C++ conditional parity)
- Milestone 05: `tasks/milestone-05.md` (runtime conformance + `0.1.0` release hardening)
