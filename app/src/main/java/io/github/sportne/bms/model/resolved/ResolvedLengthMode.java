package io.github.sportne.bms.model.resolved;

/**
 * Union type for resolved vector/blobVector length strategies.
 *
 * <p>This is the semantic-checked form of the XML one-of length choice.
 */
public sealed interface ResolvedLengthMode
    permits ResolvedCountFieldLength, ResolvedTerminatorValueLength, ResolvedTerminatorField {}
