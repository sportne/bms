package io.github.sportne.bms.model.resolved;

/**
 * Union type for one resolved {@code if} condition expression.
 *
 * <p>Semantic resolution builds this tree so generators do not need to parse raw condition text.
 */
public sealed interface ResolvedIfCondition
    permits ResolvedIfComparison, ResolvedIfLogicalCondition {}
