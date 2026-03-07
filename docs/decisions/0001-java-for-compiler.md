# ADR 0001: Use Java for the BMS compiler

## Status
Accepted

## Context
We need to build a schema parser, semantic analyzer, and code generator quickly with good XML support.

## Decision
Implement the compiler in Java.

## Consequences
Benefits:
- strong XML/XSD tooling
- strong decimal support
- easy CLI packaging
- good fit for compiler-style code

Costs:
- additional effort to emit idiomatic C++
- some boilerplate if not using codegen libraries