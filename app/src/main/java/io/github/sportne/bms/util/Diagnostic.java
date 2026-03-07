package io.github.sportne.bms.util;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** A single human-readable warning or error produced by the compiler pipeline. */
public record Diagnostic(
    DiagnosticSeverity severity,
    String code,
    String message,
    String sourcePath,
    int line,
    int column)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public Diagnostic {
    severity = Objects.requireNonNull(severity, "severity");
    code = Objects.requireNonNull(code, "code");
    message = Objects.requireNonNull(message, "message");
    sourcePath = sourcePath == null ? "" : sourcePath;
  }

  /** Returns {@code true} when both line and column are known. */
  public boolean hasLocation() {
    return line > 0 && column > 0;
  }
}
