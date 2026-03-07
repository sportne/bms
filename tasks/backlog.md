# Backlog

## P0

- [ ] Add parser + semantic support for deferred front-end constructs (`varString`, `if`, nested `type`, `checksum`, `pad`).
- [ ] Implement Java backend emission for numeric and collection members (`bitField`, `float`, `scaledInt`, `array`, `vector`, `blobArray`, `blobVector`).
- [ ] Implement C++ backend emission for numeric and collection members (`bitField`, `float`, `scaledInt`, `array`, `vector`, `blobArray`, `blobVector`).
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
