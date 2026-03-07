package io.github.sportne.bms.model.resolved;

/**
 * Union type for members allowed inside a resolved message.
 *
 * <p>Generators must respect the list order because message layout is order-sensitive.
 */
public sealed interface ResolvedMessageMember
    permits ResolvedField,
        ResolvedBitField,
        ResolvedFloat,
        ResolvedScaledInt,
        ResolvedArray,
        ResolvedVector,
        ResolvedBlobArray,
        ResolvedBlobVector {}
