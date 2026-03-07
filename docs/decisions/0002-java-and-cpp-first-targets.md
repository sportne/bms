# ADR 0002: Generate Java and C++ first

## Status
Accepted

## Context
The first useful milestone is code generation for two practical target languages.

## Decision
Target Java and C++ in the first release.

## Consequences
Benefits:
- covers a common mixed-language environment
- keeps scope bounded
- validates portability of the semantic model

Costs:
- generator abstraction needed from the beginning