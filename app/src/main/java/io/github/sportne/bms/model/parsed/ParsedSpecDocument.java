package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of one XML schema file.
 *
 * @param schema parsed schema definitions
 * @param imports schema-level imports in declaration order
 */
public record ParsedSpecDocument(ParsedSchema schema, List<ParsedImport> imports) {
  /**
   * Creates one parsed document.
   *
   * @param schema parsed schema definitions
   * @param imports schema-level imports
   */
  public ParsedSpecDocument {
    schema = Objects.requireNonNull(schema, "schema");
    imports = List.copyOf(Objects.requireNonNull(imports, "imports"));
  }
}
