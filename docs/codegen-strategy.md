# Code Generation Strategy

## Goal

Generate readable, deterministic source code from the resolved BMS model.

Deterministic means:
- same input spec -> same output files
- stable field order
- no timestamps or random values in generated output

## Current back ends

The compiler currently generates:
- Java source files
- C++ header/source files

Both back ends consume the resolved model only. They do not read XML directly.

## Current feature coverage

Java and C++ generation both cover:
- foundation scalar/message members
- numeric members (`bitField`, `float`, `scaledInt`)
- collection members (`array`, `vector`, `blobArray`, `blobVector`)
- conditional members (`varString`, `pad`, `checksum`, `if`, nested `type`)

If a spec breaks a supported contract (for example invalid checksum range or
invalid conditional expression), generation fails with diagnostics instead of
silently producing wrong code.

## Implementation style

The project uses explicit programmatic emitters in Java.

Why:
- keeps behavior easy to trace in code review
- avoids reflection-heavy template engines
- fits GraalVM-native compatibility goals

## Rule to keep

Generators must preserve wire layout order from `messageType` declarations.
Reordering members is not allowed unless schema semantics explicitly require it.
