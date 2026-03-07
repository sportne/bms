# Milestone 02: Front-End Foundation + Numeric + Collections Slice

This milestone builds the compiler front end in small, testable steps.

## Completed

- [x] Create Gradle project skeleton.
- [x] Add CLI entry point (`validate`, `generate`).
- [x] Implement XSD validator service.
- [x] Implement XML parser for root schema.
- [x] Implement parser for `messageType` and `field`.
- [x] Implement parser for `bitField`, `float`, `scaledInt`.
- [x] Implement parser for `array`, `vector`, `blobArray`, `blobVector`, and recursive `terminatorField`.
- [x] Create parsed model classes.
- [x] Create resolved model classes.
- [x] Implement reference resolution for primitives, message types, and reusable numeric types.
- [x] Extend reference resolution so `field@type` can point to reusable collection definitions.
- [x] Implement semantic validation for namespaces, duplicate names, unknown references, and numeric slice rules.
- [x] Implement semantic validation for collection rules, count-field references, and terminator-path structure.
- [x] Add parser/semantic/CLI/generator tests for the supported slice.

## Acceptance Checks For Current Slice

- [x] `bms validate` succeeds for foundation specs.
- [x] `bms validate` succeeds for numeric-slice specs (`bitField`, `float`, `scaledInt`).
- [x] `bms validate` succeeds for collection-slice specs (`array`, `vector`, `blobArray`, `blobVector`, `terminatorField`).
- [x] Message member declaration order is preserved exactly in parsed and resolved models.
- [x] Java/C++ generation is deterministic.
- [x] Java/C++ generators fail with clear diagnostics for unsupported numeric and collection backend emission.

## Remaining In Milestone 02

- [ ] Implement parser + semantic support for `varString`, `checksum`, `pad`, `if`, and nested `type`.
