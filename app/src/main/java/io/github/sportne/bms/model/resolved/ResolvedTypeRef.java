package io.github.sportne.bms.model.resolved;

/**
 * Union type for resolved field references.
 *
 * <p>After semantic resolution, every field points to one of these concrete reference kinds.
 */
public sealed interface ResolvedTypeRef
    permits PrimitiveTypeRef,
        MessageTypeRef,
        FloatTypeRef,
        ScaledIntTypeRef,
        ArrayTypeRef,
        VectorTypeRef,
        BlobArrayTypeRef,
        BlobVectorTypeRef,
        VarStringTypeRef {}
