package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.resolved.PrimitiveType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-message mutable state used while resolving members in declaration order.
 *
 * <p>This object tracks names and previously declared primitive count fields so checks for
 * duplicates and `countField@ref` keep the same behavior for nested scopes.
 */
final class MessageResolutionContext {
  final Set<String> namedMembers = new HashSet<>();
  final Set<String> previousPrimitiveFieldNames = new HashSet<>();
  final Map<String, PrimitiveType> primitiveFieldByName;

  /**
   * Creates state for a top-level message resolution pass.
   *
   * @param primitiveFieldByName primitive field lookup map used by condition validation
   */
  MessageResolutionContext(Map<String, PrimitiveType> primitiveFieldByName) {
    this.primitiveFieldByName = Map.copyOf(primitiveFieldByName);
  }

  /**
   * Creates state for a nested scope that inherits count-field visibility.
   *
   * @param inheritedCountFields primitive scalar fields visible to the nested scope
   * @param primitiveFieldByName primitive field lookup map used by condition validation
   */
  MessageResolutionContext(
      Set<String> inheritedCountFields, Map<String, PrimitiveType> primitiveFieldByName) {
    previousPrimitiveFieldNames.addAll(inheritedCountFields);
    this.primitiveFieldByName = Map.copyOf(primitiveFieldByName);
  }
}
