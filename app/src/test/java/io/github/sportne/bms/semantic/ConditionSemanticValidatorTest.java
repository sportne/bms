package io.github.sportne.bms.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedIfCondition;
import io.github.sportne.bms.util.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link ConditionSemanticValidator}. */
final class ConditionSemanticValidatorTest {
  @Test
  void resolveIfConditionBuildsConditionForValidInput() {
    List<Diagnostic> diagnostics = new ArrayList<>();
    ResolvedIfCondition condition =
        ConditionSemanticValidator.resolveIfCondition(
            "Frame",
            "status == 1 and count > 0",
            Map.of("status", PrimitiveType.UINT8, "count", PrimitiveType.UINT16),
            "spec.xml",
            diagnostics);

    assertNotNull(condition);
    assertTrue(diagnostics.isEmpty());
  }

  @Test
  void resolveIfConditionReportsUnknownField() {
    List<Diagnostic> diagnostics = new ArrayList<>();
    ResolvedIfCondition condition =
        ConditionSemanticValidator.resolveIfCondition(
            "Frame",
            "missing == 1",
            Map.of("status", PrimitiveType.UINT8),
            "spec.xml",
            diagnostics);

    assertNull(condition);
    assertEquals(1, diagnostics.size());
    assertTrue(diagnostics.getFirst().message().contains("unknown primitive field: missing"));
  }

  @Test
  void resolveIfConditionReportsOutOfRangeLiteral() {
    List<Diagnostic> diagnostics = new ArrayList<>();
    ResolvedIfCondition condition =
        ConditionSemanticValidator.resolveIfCondition(
            "Frame",
            "status == 999",
            Map.of("status", PrimitiveType.UINT8),
            "spec.xml",
            diagnostics);

    assertNull(condition);
    assertEquals(1, diagnostics.size());
    assertTrue(diagnostics.getFirst().message().contains("out of range"));
  }
}
