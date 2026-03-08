package io.github.sportne.bms.semantic;

import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertHasDiagnostic;
import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertHasDiagnosticContaining;
import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertResolutionFails;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.IfLogicalOperator;
import io.github.sportne.bms.model.StringEncoding;
import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedIfBlock;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedIfComparison;
import io.github.sportne.bms.model.resolved.ResolvedIfLogicalCondition;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedPad;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import io.github.sportne.bms.util.BmsException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Semantic resolver tests for collections, length modes, and conditional-member resolution. */
class SemanticResolverCollectionsAndConditionalRulesTest {

  @Test
  void semanticResolverResolvesCollectionTypeReferences() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(
                        new ParsedField("count", "uint16", null, null, null, "count"),
                        new ParsedArray("samples", "uint8", 4, null, "inline array"),
                        new ParsedVector(
                            "events",
                            "uint8",
                            null,
                            "inline counted vector",
                            new ParsedCountFieldLength("count")),
                        new ParsedBlobArray("hash", 8, "inline blob array"),
                        new ParsedBlobVector(
                            "payload", "inline blob vector", new ParsedTerminatorValueLength("00")),
                        new ParsedField("reusableArray", "ReusableArray", null, null, null, "a"),
                        new ParsedField("reusableVector", "ReusableVector", null, null, null, "v"),
                        new ParsedField(
                            "reusableBlobArray", "ReusableBlobArray", null, null, null, "ba"),
                        new ParsedField(
                            "reusableBlobVector", "ReusableBlobVector", null, null, null, "bv")))),
            List.of(),
            List.of(),
            List.of(),
            List.of(new ParsedArray("ReusableArray", "uint8", 2, null, "Reusable array")),
            List.of(
                new ParsedVector(
                    "ReusableVector",
                    "uint8",
                    null,
                    "Reusable vector",
                    new ParsedCountFieldLength("futureCount"))),
            List.of(new ParsedBlobArray("ReusableBlobArray", 16, "Reusable blob array")),
            List.of(
                new ParsedBlobVector(
                    "ReusableBlobVector",
                    "Reusable blob vector",
                    new ParsedTerminatorValueLength("FF"))));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");
    var frame = resolved.messageTypes().get(0);

    assertEquals(1, resolved.reusableArrays().size());
    assertEquals(1, resolved.reusableVectors().size());
    assertEquals(1, resolved.reusableBlobArrays().size());
    assertEquals(1, resolved.reusableBlobVectors().size());

    var fieldByName =
        frame.fields().stream()
            .collect(java.util.stream.Collectors.toMap(field -> field.name(), field -> field));
    assertInstanceOf(ArrayTypeRef.class, fieldByName.get("reusableArray").typeRef());
    assertInstanceOf(VectorTypeRef.class, fieldByName.get("reusableVector").typeRef());
    assertInstanceOf(BlobArrayTypeRef.class, fieldByName.get("reusableBlobArray").typeRef());
    assertInstanceOf(BlobVectorTypeRef.class, fieldByName.get("reusableBlobVector").typeRef());
  }

  @Test
  void semanticResolverRejectsVectorCountFieldRefToLaterField() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(
                        new ParsedVector(
                            "events",
                            "uint8",
                            null,
                            "inline counted vector",
                            new ParsedCountFieldLength("count")),
                        new ParsedField("count", "uint16", null, null, null, "count")))));

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_COUNT_FIELD_REF");
  }

  @Test
  void semanticResolverAllowsReusableVectorCountFieldSyntaxOnlyRef() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new ParsedVector(
                    "ReusableVector",
                    "uint8",
                    null,
                    "Reusable vector",
                    new ParsedCountFieldLength("futureCount"))),
            List.of(),
            List.of());

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");

    assertEquals(1, resolved.reusableVectors().size());
    ParsedLengthMode parsedMode = parsedSchema.reusableVectors().get(0).lengthMode();
    assertInstanceOf(ParsedCountFieldLength.class, parsedMode);
    assertInstanceOf(
        ResolvedCountFieldLength.class, resolved.reusableVectors().get(0).lengthMode());
  }

  @Test
  void semanticResolverRejectsTerminatorFieldPathWithoutMatchLeaf() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(
                        new ParsedVector(
                            "pathData",
                            "uint8",
                            null,
                            "path vector",
                            new ParsedTerminatorField(
                                "outer", new ParsedTerminatorField("inner", null)))))));

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_TERMINATOR_FIELD_PATH");
  }

  @Test
  void semanticResolverRejectsDuplicateTopLevelNameBetweenFieldAndArray() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(
                        new ParsedField("payload", "uint8", null, null, null, "payload field"),
                        new ParsedArray("payload", "uint8", 4, null, "payload array")))));

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_MEMBER_NAME");
  }

  @Test
  void semanticResolverRejectsUnknownCollectionElementType() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(new ParsedArray("samples", "MissingElement", 4, null, "samples")))));

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_UNKNOWN_TYPE");
  }

  @Test
  void semanticResolverResolvesMilestoneThreeMembers() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedField("version", "uint8", null, null, null, "version"),
                        new ParsedField("nameLength", "uint8", null, null, null, "name length"),
                        new ParsedVarString(
                            "name",
                            StringEncoding.UTF8,
                            "name",
                            new ParsedCountFieldLength("nameLength")),
                        new ParsedField(
                            "label", "ReusableLabel", null, null, null, "reusable varString"),
                        new ParsedPad(2, "alignment"),
                        new ParsedChecksum("crc16", "0..7", "checksum"),
                        new ParsedIfBlock(
                            "version == 1",
                            List.of(
                                new ParsedField("mode", "uint8", null, null, null, "mode"),
                                new ParsedMessageType(
                                    "ConditionalType",
                                    "conditional type",
                                    null,
                                    List.of(
                                        new ParsedField(
                                            "payload", "uint16", null, null, null, "payload"))))),
                        new ParsedMessageType(
                            "InlineType",
                            "inline type",
                            null,
                            List.of(
                                new ParsedField(
                                    "value", "uint8", null, null, null, "nested value")))))),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new ParsedVarString(
                    "ReusableLabel",
                    StringEncoding.ASCII,
                    "label",
                    new ParsedTerminatorValueLength("00"))),
            List.of(new ParsedChecksum("crc32", "0..3", "root checksum")),
            List.of(new ParsedPad(1, "root pad")));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");
    var message = resolved.messageTypes().get(0);

    assertEquals(1, resolved.reusableVarStrings().size());
    assertEquals(1, resolved.reusableChecksums().size());
    assertEquals(1, resolved.reusablePads().size());

    var fieldByName =
        message.fields().stream()
            .collect(java.util.stream.Collectors.toMap(field -> field.name(), field -> field));
    assertInstanceOf(VarStringTypeRef.class, fieldByName.get("label").typeRef());
    assertTrue(message.members().get(2) instanceof ResolvedVarString);
    assertTrue(message.members().get(4) instanceof ResolvedPad);
    assertTrue(message.members().get(5) instanceof ResolvedChecksum);
    assertTrue(message.members().get(6) instanceof ResolvedIfBlock);
    assertTrue(message.members().get(7) instanceof ResolvedMessageType);
  }

  @Test
  void semanticResolverRejectsVarStringCountFieldRefToLaterField() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedVarString(
                            "name",
                            StringEncoding.UTF8,
                            "name",
                            new ParsedCountFieldLength("nameLength")),
                        new ParsedField("nameLength", "uint8", null, null, null, "length")))));

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_COUNT_FIELD_REF");
  }

  @Test
  void semanticResolverRejectsInvalidConditionalAndNestedStructure() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedIfBlock(" ", List.of()),
                        new ParsedMessageType("Nested", "nested", "bad..namespace", List.of())))));

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_IF_TEST");
    assertHasDiagnostic(exception, "SEMANTIC_EMPTY_IF_BLOCK");
    assertHasDiagnostic(exception, "SEMANTIC_EMPTY_NESTED_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_NAMESPACE");
  }

  @Test
  void semanticResolverAllowsNameReuseBetweenMessageAndIfScope() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedField("value", "uint8", null, null, null, "parent value"),
                        new ParsedIfBlock(
                            "value == 1",
                            List.of(
                                new ParsedField(
                                    "value", "uint8", null, null, null, "if-scope value")))))));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");

    assertEquals(2, resolved.messageTypes().get(0).members().size());
    assertInstanceOf(ResolvedIfBlock.class, resolved.messageTypes().get(0).members().get(1));
  }

  @Test
  void semanticResolverParsesAndOrConditionsWithExpectedPrecedence() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedField("a", "uint8", null, null, null, "a"),
                        new ParsedField("b", "uint8", null, null, null, "b"),
                        new ParsedField("c", "uint8", null, null, null, "c"),
                        new ParsedIfBlock(
                            "a == 1 or b == 2 and c == 3",
                            List.of(
                                new ParsedField("value", "uint8", null, null, null, "value")))))));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");
    ResolvedMessageType messageType = resolved.messageTypes().get(0);
    ResolvedIfBlock ifBlock = (ResolvedIfBlock) messageType.members().get(3);

    assertInstanceOf(ResolvedIfLogicalCondition.class, ifBlock.condition());
    ResolvedIfLogicalCondition root = (ResolvedIfLogicalCondition) ifBlock.condition();
    assertEquals(IfLogicalOperator.OR, root.operator());
    assertInstanceOf(ResolvedIfComparison.class, root.left());
    assertEquals("a", ((ResolvedIfComparison) root.left()).fieldName());
    assertInstanceOf(ResolvedIfLogicalCondition.class, root.right());
    assertEquals(IfLogicalOperator.AND, ((ResolvedIfLogicalCondition) root.right()).operator());
  }

  @Test
  void semanticResolverParsesParenthesizedAndOrConditions() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedField("a", "uint8", null, null, null, "a"),
                        new ParsedField("b", "uint8", null, null, null, "b"),
                        new ParsedField("c", "uint8", null, null, null, "c"),
                        new ParsedIfBlock(
                            "(a == 1 or b == 2) and c == 3",
                            List.of(
                                new ParsedField("value", "uint8", null, null, null, "value")))))));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");
    ResolvedMessageType messageType = resolved.messageTypes().get(0);
    ResolvedIfBlock ifBlock = (ResolvedIfBlock) messageType.members().get(3);

    assertInstanceOf(ResolvedIfLogicalCondition.class, ifBlock.condition());
    ResolvedIfLogicalCondition root = (ResolvedIfLogicalCondition) ifBlock.condition();
    assertEquals(IfLogicalOperator.AND, root.operator());
    assertInstanceOf(ResolvedIfLogicalCondition.class, root.left());
    assertEquals(IfLogicalOperator.OR, ((ResolvedIfLogicalCondition) root.left()).operator());
    assertInstanceOf(ResolvedIfComparison.class, root.right());
    assertEquals("c", ((ResolvedIfComparison) root.right()).fieldName());
  }

  @Test
  void semanticResolverRejectsLegacyLogicalSymbolsInTextConditions() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedField("a", "uint8", null, null, null, "a"),
                        new ParsedField("b", "uint8", null, null, null, "b"),
                        new ParsedIfBlock(
                            "a == 1 && b == 2",
                            List.of(
                                new ParsedField("value", "uint8", null, null, null, "value")))))));

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnosticContaining(
        exception, "SEMANTIC_INVALID_IF_TEST", "Use 'and' and 'or' instead");
  }
}
