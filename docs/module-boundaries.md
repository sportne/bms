# Module Boundaries (Current Project Layout)

This project does not currently use Java Platform Module System (`module-info.java`).

Why:
- The project is early-stage and still changing quickly.
- Keeping classpath setup simple makes local builds and tests easier for new contributors.
- GraalVM compatibility goals are already met without JPMS right now.

Even without JPMS, the codebase is split into clear logical modules by package.

## Logical modules by package

- `io.github.sportne.bms.validate`
  - XSD validation stage.
- `io.github.sportne.bms.parse`
  - XML parsing stage (StAX) that builds parsed-model objects.
- `io.github.sportne.bms.semantic`
  - Semantic checks and normalization into the resolved model.
- `io.github.sportne.bms.model.parsed`
  - Parsed-model data types (close to XML).
- `io.github.sportne.bms.model.resolved`
  - Resolved-model data types (generator input).
- `io.github.sportne.bms.codegen.java`
  - Java output generation.
- `io.github.sportne.bms.codegen.cpp`
  - C++ output generation.
- `io.github.sportne.bms.cli`
  - Command-line interface.
- `io.github.sportne.bms.util`
  - Shared diagnostics and helper utilities.

## Rule to keep

Generators should only read the resolved model package (`model.resolved`).
They should not depend on parser internals or XML classes.
