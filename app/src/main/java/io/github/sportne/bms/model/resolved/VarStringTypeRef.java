package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to a reusable {@code varString} definition declared at schema scope.
 *
 * @param varStringTypeName resolved reusable varString type name
 */
public record VarStringTypeRef(String varStringTypeName) implements ResolvedTypeRef {

  /**
   * Creates a reference to a reusable varString type.
   *
   * @param varStringTypeName reusable varString type name
   */
  public VarStringTypeRef {
    varStringTypeName = Objects.requireNonNull(varStringTypeName, "varStringTypeName");
  }
}
