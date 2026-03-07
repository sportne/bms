# AGENTS.md

## Project

This repository implements tooling for **Binary Message Schema (BMS)**.

BMS is an XML-based language for describing binary wire formats and generating serialization/deserialization code.

The implementation is a **small compiler/code generator**.

Initial targets:
- Java
- C++

Implementation language:
- Java

---

# Core Architecture

The tool follows a strict compiler-style pipeline:

1. XSD validation
2. XML parsing
3. Parsed model construction
4. Semantic validation / normalization
5. Resolved model construction
6. Code generation

Generators operate **only on the resolved model**.

---

# Major Constraints

### GraalVM compatibility

The project must remain compatible with **GraalVM native-image**.

Avoid:
- reflection-heavy libraries
- JAXB
- Spring
- runtime classpath scanning
- dynamic proxies
- annotation-driven frameworks

Prefer:
- plain Java
- explicit object construction
- minimal dependencies

---

### Deterministic generation

Generated code must be deterministic.

Do NOT:
- include timestamps
- include random IDs
- depend on map iteration order
- reorder message fields

---

### Message order matters

Order of fields in `messageType` corresponds to **binary layout**.

Never reorder elements unless explicitly required.

---

# XML Handling

Do NOT introduce JAXB.

Use:

- XSD validation with standard Java APIs
- direct XML parsing (prefer **StAX**)

Parsers should be explicit and small.

---

# Model Layers

Two model layers must exist:

### Parsed model
Represents the XML structure.

### Resolved model
Used by generators.

Responsibilities:
- resolve type references
- apply defaults
- validate semantics

Generators must not depend on XML parsing classes.

---

# Code Generation

Generated code should be:

- readable
- stable
- framework-free
- deterministic

Java output must avoid reflection and frameworks.

C++ output should remain portable and conservative.

---

# Dependencies

Dependencies should remain minimal.

Before adding one, ask:

1. Is it necessary?
2. Is it GraalVM compatible?
3. Can the JDK handle this instead?

Avoid heavy frameworks.

---

# Tests

All changes should include tests.

Preferred tests:

- parser tests
- semantic validation tests
- generator golden-file tests
- end-to-end spec → code tests

Generated output must remain stable.

---

# Good LLM Tasks

Examples:

- implement parsing of one XML element
- add one semantic validation rule
- extend one generator feature
- add tests for an existing component

---

# Bad LLM Tasks

Avoid tasks that:

- redesign the architecture
- introduce frameworks
- silently change schema semantics
- combine parser + semantics + generator changes in one step

---

# When modifying the schema

If editing the XSD (which should be very, very rare):

- ensure referenced types exist
- avoid duplicate attributes
- prefer enumerations over free strings
- update examples if needed
- document semantic rules that cannot be enforced in XSD

---

# Implementation priority

When uncertain, prioritize:

1. Correct binary representation
2. Clear semantic model
3. Deterministic generation
4. Simplicity
5. Performance