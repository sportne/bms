package io.github.sportne.bms.util;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

public final class BmsException extends Exception {
  @Serial private static final long serialVersionUID = 1L;

  private final List<Diagnostic> diagnostics;

  public BmsException(String message, List<Diagnostic> diagnostics) {
    super(Objects.requireNonNull(message, "message"));
    this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }

  public List<Diagnostic> diagnostics() {
    return diagnostics;
  }
}
