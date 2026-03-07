package io.github.sportne.bms.model.resolved;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Contract tests for {@link PrimitiveType}. */
class PrimitiveTypeTest {

  /** Contract: known schema names map to primitive definitions with stable target type names. */
  @Test
  void fromSchemaNameMapsKnownValues() {
    assertEquals(PrimitiveType.UINT8, PrimitiveType.fromSchemaName("uint8"));
    assertEquals(PrimitiveType.INT64, PrimitiveType.fromSchemaName("int64"));
    assertEquals("uint8", PrimitiveType.UINT8.schemaName());
    assertEquals("short", PrimitiveType.UINT8.javaTypeName());
    assertEquals("std::uint8_t", PrimitiveType.UINT8.cppTypeName());
  }

  /** Contract: unknown schema names return {@code null}. */
  @Test
  void fromSchemaNameReturnsNullForUnknownValue() {
    assertNull(PrimitiveType.fromSchemaName("float32"));
  }
}
