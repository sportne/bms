# Code Generation Strategy

## Principle

Use a shared semantic model and independent language back ends.

## Java output

Generate:
- one type file per message
- serializer/deserializer helpers
- endian utility
- checksum utility

Possible package layout:

```text
com.example.generated.messages
com.example.generated.runtime
```

C++ output

Generate:

header and source per message or grouped by spec

runtime helpers for endian and checksum

Possible layout:

include/generated/
src/generated/
Templates vs programmatic generation

Prefer templates for:

file structure

repetitive method shapes

comments

Prefer programmatic generation for:

offset calculations

nested loops

condition generation

bit packing expressions

V1 simplification

Start with direct code generation for:

primitive fields

fixed arrays

vectors with countField

bitField flags and segments

fixedString and varString

checksum and pad

Add more complex recursive and conditional cases once the golden tests are stable.
