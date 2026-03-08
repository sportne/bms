# Foundation Walkthrough

This document explains the current BMS foundation in plain language.

## What problem does BMS solve?

Some systems already use a binary message format that cannot change.
BMS lets you describe that format in XML and generate code that reads/writes the
same bytes.

## What is implemented today?

The current foundation supports a small but complete path:

- XSD validation
- XML parsing for core members (`field`, `bitField`, `float`, `scaledInt`)
- XML parsing for collections (`array`, `vector`, `blobArray`, `blobVector`, `terminatorField`)
- XML parsing for conditional/string members (`varString`, `pad`, `checksum`, `if`, nested `type`)
- semantic checks (namespaces, type references, duplicate names)
- Java code generation
- C++ code generation

The parser still fails fast for truly unknown XML elements that are not part of the schema.
Current Java and C++ generators both support foundation, numeric, collection,
and conditional members used by the fixture matrix.

## Why two models?

There are two internal models to keep responsibilities clear:

- Parsed model: looks like XML structure
- Resolved model: references are checked and ready for code generation

Generators only use the resolved model.
This keeps generator code simpler and safer.

## How namespace selection works

- `schema@namespace` sets the default namespace for all messages.
- `messageType@namespace` can override it for one message.
- Java output uses that value as package name.
- C++ output uses the same logical value, but converts `.` to `::`.

Example:

- logical namespace: `acme.telemetry.packet`
- Java package: `acme.telemetry.packet`
- C++ namespace: `acme::telemetry::packet`

## Where to read first in code

- `io.github.sportne.bms.cli.BmsCli`: command-line entry point
- `io.github.sportne.bms.BmsCompiler`: pipeline orchestration
- `io.github.sportne.bms.validate.SpecValidator`: XSD validation
- `io.github.sportne.bms.parse.SpecParser`: StAX parser
- `io.github.sportne.bms.semantic.SemanticResolver`: semantic checks
- `io.github.sportne.bms.codegen.java.JavaCodeGenerator`: Java output
- `io.github.sportne.bms.codegen.cpp.CppCodeGenerator`: C++ output
