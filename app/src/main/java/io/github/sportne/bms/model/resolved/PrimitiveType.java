package io.github.sportne.bms.model.resolved;

import java.util.Arrays;

/**
 * Built-in scalar integer types supported by the current compiler slice.
 *
 * <p>Each enum value stores names for the schema, Java output, and C++ output.
 */
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

  /**
   * Creates one primitive type mapping entry.
   *
   * @param schemaName type name used in XML
   * @param javaTypeName Java type name used by Java generation
   * @param cppTypeName C++ type name used by C++ generation
   */
  PrimitiveType(String schemaName, String javaTypeName, String cppTypeName) {
    this.schemaName = schemaName;
    this.javaTypeName = javaTypeName;
    this.cppTypeName = cppTypeName;
  }

  /** Returns the type name used in BMS XML. */
  public String schemaName() {
    return schemaName;
  }

  /** Returns the Java type name used by the Java generator. */
  public String javaTypeName() {
    return javaTypeName;
  }

  /** Returns the C++ type name used by the C++ generator. */
  public String cppTypeName() {
    return cppTypeName;
  }

  /**
   * Looks up a primitive type by XML schema name, or returns {@code null} when unknown.
   *
   * @param schemaName primitive type name from XML (for example {@code uint16})
   * @return matching primitive type, or {@code null} when not recognized
   */
  public static PrimitiveType fromSchemaName(String schemaName) {
    return Arrays.stream(values())
        .filter(primitiveType -> primitiveType.schemaName.equals(schemaName))
        .findFirst()
        .orElse(null);
  }
}
