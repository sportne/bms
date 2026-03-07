package io.github.sportne.bms.util;

import java.util.List;

/** Small helper methods for working with lists of diagnostics. */
public final class Diagnostics {
  private Diagnostics() {}

  /** Returns {@code true} when at least one diagnostic has {@link DiagnosticSeverity#ERROR}. */
  public static boolean hasErrors(List<Diagnostic> diagnostics) {
    return diagnostics.stream()
        .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
  }

  /** Formats one diagnostic as a single line of user-facing text. */
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
