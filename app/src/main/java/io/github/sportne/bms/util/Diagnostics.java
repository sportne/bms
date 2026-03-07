package io.github.sportne.bms.util;

import java.util.List;

public final class Diagnostics {
  private Diagnostics() {}

  public static boolean hasErrors(List<Diagnostic> diagnostics) {
    return diagnostics.stream()
        .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
  }

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
