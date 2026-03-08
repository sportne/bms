package io.github.sportne.bms.codegen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedIfComparison;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link PrimitiveFieldIndex}. */
final class PrimitiveFieldIndexTest {
  @Test
  void collectFindsPrimitiveFieldsRecursivelyAndKeepsFirstDefinition() {
    ResolvedMessageType messageType =
        new ResolvedMessageType(
            "Packet",
            "packet",
            "acme.telemetry",
            List.of(
                new ResolvedField(
                    "count", new PrimitiveTypeRef(PrimitiveType.UINT16), null, null, null, ""),
                new ResolvedIfBlock(
                    new ResolvedIfComparison(
                        "count", PrimitiveType.UINT16, IfComparisonOperator.GT, BigInteger.ZERO),
                    List.of(
                        new ResolvedField(
                            "value",
                            new PrimitiveTypeRef(PrimitiveType.INT32),
                            null,
                            null,
                            null,
                            ""))),
                new ResolvedMessageType(
                    "Nested",
                    "",
                    "acme.telemetry",
                    List.of(
                        new ResolvedField(
                            "count",
                            new PrimitiveTypeRef(PrimitiveType.UINT8),
                            null,
                            null,
                            null,
                            "")))));

    Map<String, PrimitiveType> indexed = PrimitiveFieldIndex.collect(messageType);
    assertEquals(PrimitiveType.UINT16, indexed.get("count"));
    assertEquals(PrimitiveType.INT32, indexed.get("value"));
    assertTrue(indexed.containsKey("count"));
    assertTrue(indexed.containsKey("value"));
  }
}
