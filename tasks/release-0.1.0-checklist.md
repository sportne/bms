# Release Checklist: 0.1.0

Use this checklist when preparing the `0.1.0` tag.

## Code + tests

- [x] `./gradlew check --no-daemon` passes locally.
- [ ] CI `check` workflow is green on the release commit.
- [ ] CI native-image workflow is green on the release commit.
- [x] Fixture matrix in `tasks/fixture-matrix-0.1.0.md` is fully green.

## Parity proof

- [x] C++ Milestone 03 acceptance checks complete.
- [x] C++ Milestone 04 acceptance checks complete.
- [x] Cross-language conformance tests are green.

## Documentation

- [x] README support status matches the shipped behavior exactly.
- [x] Roadmap and backlog status are updated to reflect what was completed.
- [x] Any known limitations are documented clearly in plain language.

## Versioning + release notes

- [x] Version is set to `0.1.0`.
- [x] Changelog/release notes summarize features, fixes, and known limits.
- [ ] Git tag `v0.1.0` points to the exact release commit.

## Post-release follow-up

- [x] Open follow-up items for `1.0.0` hardening work.
- [ ] Record any migration notes needed from pre-`0.1.0` snapshots.
