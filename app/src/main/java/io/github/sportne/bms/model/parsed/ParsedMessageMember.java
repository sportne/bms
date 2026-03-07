package io.github.sportne.bms.model.parsed;

/**
 * Union type for members that can appear inside a parsed {@code messageType}.
 *
 * <p>The list order in {@link ParsedMessageType#members()} must match XML declaration order.
 */
public sealed interface ParsedMessageMember
    permits ParsedField,
        ParsedBitField,
        ParsedFloat,
        ParsedScaledInt,
        ParsedArray,
        ParsedVector,
        ParsedBlobArray,
        ParsedBlobVector {}
