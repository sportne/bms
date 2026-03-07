package io.github.sportne.bms.model.resolved;

import java.util.Arrays;

public enum PrimitiveType {
  UINT8("uint8", "short", "std::uint8_t"),
  UINT16("uint16", "int", "std::uint16_t"),
  UINT32("uint32", "long", "std::uint32_t"),
  UINT64("uint64", "long", "std::uint64_t"),
  INT8("int8", "byte", "std::int8_t"),
  INT16("int16", "short", "std::int16_t"),
  INT32("int32", "int", "std::int32_t"),
  INT64("int64", "long", "std::int64_t");

  private final String schemaName;
  private final String javaTypeName;
  private final String cppTypeName;

  PrimitiveType(String schemaName, String javaTypeName, String cppTypeName) {
    this.schemaName = schemaName;
    this.javaTypeName = javaTypeName;
    this.cppTypeName = cppTypeName;
  }

  public String schemaName() {
    return schemaName;
  }

  public String javaTypeName() {
    return javaTypeName;
  }

  public String cppTypeName() {
    return cppTypeName;
  }

  public static PrimitiveType fromSchemaName(String schemaName) {
    return Arrays.stream(values())
        .filter(primitiveType -> primitiveType.schemaName.equals(schemaName))
        .findFirst()
        .orElse(null);
  }
}
