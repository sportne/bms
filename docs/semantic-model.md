# Semantic Model

## Goal

Represent a BMS specification in a language-neutral internal model that is convenient for validation and generation.

## Core entities

- Schema
- MessageType
- Field
- BitField
- Flag
- Segment
- Variant
- FloatType
- ScaledIntType
- ArrayType
- VectorType
- BlobArrayType
- BlobVectorType
- VarStringType
- Checksum
- Pad
- ConditionalBlock
- TerminatorPath

## Design rules

- All named reusable types should have globally unique names.
- All field-like items inside a message should preserve declaration order.
- Primitive types should be represented explicitly, not as free strings.
- Decimal scales should use BigDecimal.
- Checksum algorithm and string encoding should be enums in the semantic model.
- Unresolved references should never reach code generation.

## Recommended model split

Use two model layers:

### Parsed model
Close to XML shape.

### Resolved model
References resolved, defaults applied, validated and normalized.

Code generation should consume only the resolved model.
