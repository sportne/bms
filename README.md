# Binary Message Schema (BMS)

Binary Message Schema (BMS) is an XML-based specification language for defining binary wire formats and generating code for serialization and deserialization.

## Project goals

- Define binary message layouts in a machine-readable specification.
- Preserve existing wire formats exactly.
- Generate type-safe code for multiple languages.
- Start with Java and C++ generation.
- Keep the implementation simple enough to move quickly using modern coding LLMs.

## Current status

Early-stage compiler / code generator project.

The repository contains:
- the BMS XML Schema (XSD)
- example BMS message specifications
- architecture and semantic-model documentation
- task lists for an initial Java implementation

## Initial scope

The first working version should:
- validate a BMS XML file against the XSD
- perform semantic validation beyond XSD
- generate Java code
- generate C++ code
- include tests using example specifications and golden outputs

## Non-goals for the first version

- full IDE integration
- every possible future target language
- schema editor UI
- runtime reflection framework

## Repository structure

```text
spec/     Schema and example message specifications
src/      Java implementation of parser, semantic model, and generators
docs/     Architecture, decisions, and development notes
tasks/    Milestones and actionable implementation tasks
examples/ Small generated-code usage examples
```
