package io.github.sportne.bms.util;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * Checked exception used for expected compiler failures.
 *
 * <p>Each exception carries one or more diagnostics so the CLI can print useful messages.
 */
public final class BmsException extends Exception {
  @Serial private static final long serialVersionUID = 1L;

  private final List<Diagnostic> diagnostics;

  /**
   * Creates an exception with a human-readable summary and detailed diagnostics.
   *
   * @param message short summary shown at the top of CLI output
   * @param diagnostics detailed warnings/errors collected during processing
   */
  public BmsException(String message, List<Diagnostic> diagnostics) {
    super(Objects.requireNonNull(message, "message"));
    this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }

  /** Returns immutable diagnostics that explain the failure in detail. */
  public List<Diagnostic> diagnostics() {
    return diagnostics;
  }
}
