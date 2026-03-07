package io.github.sportne.bms.util;

import java.util.List;

/** Small helper methods for working with lists of diagnostics. */
public final class Diagnostics {
  /** Utility class; not meant to be instantiated. */
  private Diagnostics() {}

  /**
   * Returns {@code true} when at least one diagnostic has {@link DiagnosticSeverity#ERROR}.
   *
   * @param diagnostics diagnostics list to scan
   * @return {@code true} when the list contains one or more errors
   */
  public static boolean hasErrors(List<Diagnostic> diagnostics) {
    return diagnostics.stream()
        .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
  }

  /**
   * Formats one diagnostic as a single line of user-facing text.
   *
   * @param diagnostic diagnostic object to format
   * @return human-readable one-line diagnostic text
   */
  public static String format(Diagnostic diagnostic) {
    StringBuilder builder = new StringBuilder();
    builder.append(diagnostic.severity()).append(" [").append(diagnostic.code()).append("] ");
    if (!diagnostic.sourcePath().isEmpty()) {
      builder.append(diagnostic.sourcePath());
      if (diagnostic.hasLocation()) {
        builder.append(':').append(diagnostic.line()).append(':').append(diagnostic.column());
      }
      builder.append(' ');
    }
    builder.append(diagnostic.message());
    return builder.toString();
  }
}
