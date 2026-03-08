# Backlog

## P0

- [x] Implement Java backend emission for numeric and collection members (`bitField`, `float`, `scaledInt`, `array`, `vector`, `blobArray`, `blobVector`).
- [x] Implement Java staged conditional backend emission for `varString` and `pad` (Milestone 04A).
- [ ] Implement Java conditional backend emission for deferred members (`checksum`, `if`, nested `type`) (Milestone 04B).
- [ ] Implement C++ backend emission for numeric and collection members (`bitField`, `float`, `scaledInt`, `array`, `vector`, `blobArray`, `blobVector`).
- [ ] Implement C++ backend emission for conditional members (`varString`, `pad`, `checksum`, `if`, nested `type`).
- [ ] Expand end-to-end specs so at least one example covers each supported front-end construct.

## P1

- [ ] Add golden tests for new backend numeric code generation outputs.
- [ ] Improve diagnostics with richer source location details (line/column where possible).
- [ ] Improve CLI UX for multi-error summaries and next-step hints.
- [ ] Add optional formatting pass for generated Java and C++ output.

## P2

- [ ] Add Python generator.
- [ ] Add Rust generator.
- [ ] Add HTML documentation generation from BMS specs.
- [ ] Add schema linting command.
- [ ] Add import/export helpers.
