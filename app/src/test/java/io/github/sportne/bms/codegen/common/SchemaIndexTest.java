package io.github.sportne.bms.codegen.common;

import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.StringEncoding;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link SchemaIndex}. */
final class SchemaIndexTest {
  @Test
  void fromResolvedSchemaIndexesEachReusableTypeMapByName() {
    ResolvedMessageType messageType =
        new ResolvedMessageType("Header", "", "acme.telemetry", List.of());
    ResolvedFloat resolvedFloat =
        new ResolvedFloat("Float32", FloatSize.F32, FloatEncoding.IEEE754, null, null, "");
    ResolvedScaledInt resolvedScaledInt =
        new ResolvedScaledInt("Scaled", PrimitiveType.UINT16, BigDecimal.valueOf(0.1), null, "");
    ResolvedArray resolvedArray =
        new ResolvedArray("ArrayU8", new PrimitiveTypeRef(PrimitiveType.UINT8), 2, null, "");
    ResolvedVector resolvedVector =
        new ResolvedVector(
            "VectorU8",
            new PrimitiveTypeRef(PrimitiveType.UINT8),
            null,
            "",
            new ResolvedCountFieldLength("count"));
    ResolvedBlobArray resolvedBlobArray = new ResolvedBlobArray("BlobArray", 8, "");
    ResolvedBlobVector resolvedBlobVector =
        new ResolvedBlobVector("BlobVector", "", new ResolvedCountFieldLength("count"));
    ResolvedVarString resolvedVarString =
        new ResolvedVarString(
            "Name", StringEncoding.UTF8, "", new ResolvedCountFieldLength("count"));

    ResolvedSchema resolvedSchema =
        new ResolvedSchema(
            "acme.telemetry",
            List.of(messageType),
            List.of(),
            List.of(resolvedFloat),
            List.of(resolvedScaledInt),
            List.of(resolvedArray),
            List.of(resolvedVector),
            List.of(resolvedBlobArray),
            List.of(resolvedBlobVector),
            List.of(resolvedVarString),
            List.of(),
            List.of());

    SchemaIndex schemaIndex = SchemaIndex.fromResolvedSchema(resolvedSchema);
    assertSame(messageType, schemaIndex.messageTypeByName().get("Header"));
    assertSame(resolvedFloat, schemaIndex.reusableFloatByName().get("Float32"));
    assertSame(resolvedScaledInt, schemaIndex.reusableScaledIntByName().get("Scaled"));
    assertSame(resolvedArray, schemaIndex.reusableArrayByName().get("ArrayU8"));
    assertSame(resolvedVector, schemaIndex.reusableVectorByName().get("VectorU8"));
    assertSame(resolvedBlobArray, schemaIndex.reusableBlobArrayByName().get("BlobArray"));
    assertSame(resolvedBlobVector, schemaIndex.reusableBlobVectorByName().get("BlobVector"));
    assertSame(resolvedVarString, schemaIndex.reusableVarStringByName().get("Name"));
  }
}
