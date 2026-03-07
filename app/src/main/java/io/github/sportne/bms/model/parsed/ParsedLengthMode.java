package io.github.sportne.bms.model.parsed;

/**
 * Union type for vector/blobVector length strategies.
 *
 * <p>This models the XML one-of choice: count field, terminator value, or terminator field path.
 */
public sealed interface ParsedLengthMode
    permits ParsedCountFieldLength, ParsedTerminatorValueLength, ParsedTerminatorField {}
