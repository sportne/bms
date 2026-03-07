package io.github.sportne.bms.util;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * A single human-readable warning or error produced by the compiler pipeline.
 *
 * @param severity severity level ({@code ERROR} or {@code WARNING})
 * @param code stable machine-readable code (for example {@code SEMANTIC_UNKNOWN_TYPE})
 * @param message human-readable detail text
 * @param sourcePath source file path related to this diagnostic (empty when unknown)
 * @param line 1-based line number, or negative/zero when unknown
 * @param column 1-based column number, or negative/zero when unknown
 */
public record Diagnostic(
    DiagnosticSeverity severity,
    String code,
    String message,
    String sourcePath,
    int line,
    int column)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates one immutable diagnostic record.
   *
   * @param severity severity level
   * @param code stable machine-readable code
   * @param message human-readable detail text
   * @param sourcePath source file path, or empty when unknown
   * @param line 1-based line number, or non-positive when unknown
   * @param column 1-based column number, or non-positive when unknown
   */
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
