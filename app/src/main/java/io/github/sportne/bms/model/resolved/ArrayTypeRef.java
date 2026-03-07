package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to a reusable array definition declared at schema scope.
 *
 * @param arrayTypeName resolved array type name
 */
public record ArrayTypeRef(String arrayTypeName) implements ResolvedTypeRef {

  /**
   * Creates a reference to a reusable array type.
   *
   * @param arrayTypeName reusable array type name
   */
  public ArrayTypeRef {
    arrayTypeName = Objects.requireNonNull(arrayTypeName, "arrayTypeName");
  }
}
