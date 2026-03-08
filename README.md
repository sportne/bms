# Binary Message Schema (BMS)

Binary Message Schema (BMS) is an XML-based specification language for defining binary wire formats and generating code for serialization and deserialization.

If you are new to compilers: you can think of BMS as a translator.
It reads an XML spec file, checks it for mistakes, and writes source code in
Java or C++ that follows the binary format exactly.

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
- compiler pipeline foundation (validate → parse → semantic resolve → generate)
- architecture and semantic-model documentation
- task lists for incremental milestones

## Initial scope

The first working version should:
- validate a BMS XML file against the XSD
- perform semantic validation beyond XSD
- generate Java code
- generate C++ code
- include tests using example specifications and golden outputs

## CLI

```text
bms validate <spec.xml>
bms generate <spec.xml> --java <out/java> [--cpp <out/cpp>]
```

## How the pipeline works

The current foundation runs these steps in order:

1. Validate XML against the XSD.
2. Parse XML into a parsed model.
3. Run semantic checks (for example: unknown types, duplicate fields, namespace format).
4. Build a resolved model that generators can safely use.
5. Generate Java and/or C++ output files.

This order matters because each step simplifies the next step.

Current front-end parser/semantic support includes:
- `field`
- `bitField` (`flag`, `segment`, `variant`)
- `float`
- `scaledInt`
- `array`
- `vector`
- `blobArray`
- `blobVector`
- `terminatorField`/`terminatorMatch`
- `varString`
- `pad`
- `checksum`
- `if`
- nested `type`

Current Java backend emission supports:
- foundation scalar fields and message references
- numeric slice (`bitField`, `float`, `scaledInt`)
- collection slice (`array`, `vector`, `blobArray`, `blobVector`)
- conditional slice (`varString`, `pad`, `checksum`, `if`, nested `type`)

Current Java backend conditional notes:
- `checksum` currently supports `crc16` and `crc32`
- checksum `range` must be `start..end` with non-negative integer indexes and `start <= end`
- `if@test` currently supports `field == literal` and `field != literal` with numeric literals
- members inside `if` and nested `type` are emitted as fields on the generated class in wire order
- flattened member names must be unique after expansion or generation fails with diagnostics

Current C++ backend emission is intentionally narrower:
- foundation scalar fields and message references are generated
- non-foundation member kinds fail with clear diagnostics during `generate`

The spec now requires `schema@namespace` and allows `messageType@namespace` override.

## Namespace behavior (important)

- `schema@namespace` is required.
- `messageType@namespace` is optional.
- If `messageType@namespace` is present, it fully overrides the schema namespace.
- Namespace format is dot-delimited identifiers, for example: `acme.telemetry.v1`.
- Java generator uses that value directly as the package name.
- C++ generator converts dots to namespace nesting (`acme::telemetry::v1`).

## Quick start

1. Validate a spec:
   `./gradlew :app:run --args='validate spec/examples/foundation-valid.xml'`
2. Generate Java and C++ code:
   `./gradlew :app:run --args='generate spec/examples/foundation-valid.xml --java .tmp/out-java --cpp .tmp/out-cpp'`
3. Run checks and tests:
   `./gradlew check`

## Non-goals for the first version

- full IDE integration
- every possible future target language
- schema editor UI
- runtime reflection framework

## Repository structure

```text
spec/     Schema and specification assets
app/      Java compiler implementation and tests
docs/     Architecture, decisions, and development notes
tasks/    Milestones and actionable implementation tasks
```

Start with [docs/foundation-walkthrough.md](docs/foundation-walkthrough.md) for a beginner-friendly tour.
