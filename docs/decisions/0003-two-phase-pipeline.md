# ADR 0003: Use a two-phase compiler pipeline

## Status
Accepted

## Context
Parsing and generation complexity will grow.

## Decision
Split the implementation into front end and back end.

## Consequences
Benefits:
- easier testing
- easier multi-language code generation
- semantic checks centralized

Costs:
- more model classes up front