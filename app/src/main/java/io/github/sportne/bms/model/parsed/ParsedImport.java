package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Parsed representation of one schema-level import declaration.
 *
 * @param path relative or absolute path literal from {@code import@path}
 */
public record ParsedImport(String path) {
  /**
   * Creates one parsed import declaration.
   *
   * @param path non-blank import path literal
   */
  public ParsedImport {
    path = Objects.requireNonNull(path, "path");
  }
}
