package io.github.sportne.bms.model.parsed;

/** Union type for recursive nodes inside a parsed {@code terminatorField} path. */
public sealed interface ParsedTerminatorNode permits ParsedTerminatorField, ParsedTerminatorMatch {}
