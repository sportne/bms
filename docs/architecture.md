# Architecture

## Overview

The BMS compiler is a two-phase pipeline:

1. Front end
   - XSD validation
   - XML parsing
   - semantic validation
   - normalization

2. Back end
   - Java generator
   - C++ generator

## Modules

### cli
Command-line entry point.

### validate
XSD validation and high-level spec loading.

### parser
XML parsing into raw model objects.

### model
AST / semantic model classes.

### semantic
Cross-reference resolution and semantic checks.

### codegen.java
Java code generation.

### codegen.cpp
C++ code generation.

## Output style

Generated code should be deterministic:
- stable file names
- stable field ordering
- stable formatting
- no timestamps in generated files

## Error handling

Validation errors should:
- include file path
- include spec element name when possible
- explain the rule that failed
- suggest the likely fix
