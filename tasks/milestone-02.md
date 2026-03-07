# Milestone 02: Front-End Foundation + Numeric Slice

This milestone builds the compiler front end in small, testable steps.

## Completed

- [x] Create Gradle project skeleton.
- [x] Add CLI entry point (`validate`, `generate`).
- [x] Implement XSD validator service.
- [x] Implement XML parser for root schema.
- [x] Implement parser for `messageType` and `field`.
- [x] Implement parser for `bitField`, `float`, `scaledInt`.
- [x] Create parsed model classes.
- [x] Create resolved model classes.
- [x] Implement reference resolution for primitives, message types, and reusable numeric types.
- [x] Implement semantic validation for namespaces, duplicate names, unknown references, and numeric slice rules.
- [x] Add parser/semantic/CLI/generator tests for the supported slice.

## Acceptance Checks For Current Slice

- [x] `bms validate` succeeds for foundation specs.
- [x] `bms validate` succeeds for numeric-slice specs (`bitField`, `float`, `scaledInt`).
- [x] Message member declaration order is preserved exactly in parsed and resolved models.
- [x] Java/C++ generation is deterministic.
- [x] Java/C++ generators fail with clear diagnostics for unsupported numeric backend emission.

## Remaining In Milestone 02

- [ ] Implement parser + semantic support for `array`, `vector`, and `terminatorField`.
- [ ] Implement parser + semantic support for `varString`, `checksum`, `pad`, and conditional blocks.
- [ ] Implement parser + semantic support for `blobArray` and `blobVector`.
