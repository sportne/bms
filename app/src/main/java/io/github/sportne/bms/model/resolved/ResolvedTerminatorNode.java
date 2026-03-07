package io.github.sportne.bms.model.resolved;

/** Union type for recursive nodes inside a resolved {@code terminatorField} path. */
public sealed interface ResolvedTerminatorNode
    permits ResolvedTerminatorField, ResolvedTerminatorMatch {}
