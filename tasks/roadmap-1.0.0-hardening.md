# 1.0.0 Hardening Kickoff

> Tracking note: open work is consolidated in `tasks/backlog.md`.

This file defines the first three work slices after `0.1.0`.

The goal is simple: move from "feature complete enough for early users" to "stable and predictable for long-term users."

## Slice 1: Compatibility policy freeze

Define exactly what counts as a breaking change for:
- XSD changes
- semantic behavior changes
- CLI contract changes
- generated Java/C++ API shape changes

Deliverables:
- written compatibility rules
- semver interpretation for this project
- migration note template for future breaks

## Slice 2: Diagnostics quality upgrade

Improve error quality so users can fix specs faster.

Focus areas:
- line and column information where possible
- cleaner multi-error summaries
- clearer action-oriented message text

Deliverables:
- updated diagnostic format guide
- tests that lock expected error text structure

## Slice 3: Release playbook and migration policy

Make releases repeatable and easy to audit.

Focus areas:
- release checklist automation
- changelog and tag workflow
- migration guidance for existing users

Deliverables:
- release playbook document
- automation steps for release validation
- migration/support policy document
