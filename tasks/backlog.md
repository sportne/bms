# Consolidated Task Tracker

This file is the single place to track what is still open.

Older milestone and release-checklist files were consolidated into this tracker.
Update progress here first.

## Current release blockers (`0.1.0`)

- [X] CI `check` workflow is green on the release commit.
- [X] CI native-image workflow is green on the release commit.
- [X] Git tag `0.1.0` points to the exact release commit.
- [X] Record migration notes from pre-`0.1.0` snapshots.

## Next priority (`1.0.0` hardening)

- [ ] Freeze compatibility policy for XSD, semantics, CLI, and generated APIs.
- [ ] Improve diagnostics with clearer line/column details where possible.
- [ ] Improve CLI multi-error summaries and next-step guidance.
- [ ] Publish a release playbook and migration/support policy.
- [ ] Add release-check automation for repeatable validation.
- [ ] Add optional formatting pass for generated Java and C++ output.

## Additional tasks

- [x] Address gradle deprecation warnings.
- [ ] Demonstrate project with real world message specification.
- [x] Updating project documentation to reflect current project status.
- [ ] Create users guide / man page.
- [ ] Setup code analysis on generated code to the same standard as the code in the project. Use clang-tidy and cppcheck as in `../cpp-helper-libs/`.
- [ ] Wire up basic tests as part of check that use the natively compiled version of the application that only run when the correct toolchain (graalvm) are available.

## Later enhancements

- [ ] Add Python generator.
- [ ] Add Rust generator.
- [ ] Add HTML documentation generation from BMS specs.
- [ ] Add schema linting command.
- [ ] Add import/export helpers.
- [ ] Enhance error handling in generated code.
- [ ] Add verbose debug tool that takes a message specification and a file that may or may not meet the specifiction.

## Completed milestones snapshot

- [x] Java baseline, deterministic generation, and Java runtime end-to-end checks.
- [x] C++ numeric + collection parity.
- [x] C++ conditional parity.
- [x] Cross-language conformance harness and fixture matrix freeze.

## Supporting documents

- Backlog tracker: `tasks/backlog.md`
- `1.0.0` hardening roadmap: `tasks/roadmap-1.0.0-hardening.md`
