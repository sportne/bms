package io.github.sportne.bms.parse;

import static io.github.sportne.bms.parse.SpecParserTestSupport.writeTempSpec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedIfBlock;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedSpecDocument;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorMatch;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.testutil.TestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Parser contract tests for supported specification slices.
 *
 * <p>These tests document what valid XML inputs parse successfully and what parsed-model shape is
 * expected.
 */
class SpecParserSupportedSliceTest {

  @Test
  void parserReadsSchemaNamespaceMessageOverrideAndFieldOrder() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/valid-foundation.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);

    assertEquals("acme.telemetry", parsedSchema.namespace());
    assertEquals(2, parsedSchema.messageTypes().size());

    ParsedMessageType header = parsedSchema.messageTypes().get(0);
    assertEquals("Header", header.name());
    assertEquals(2, header.fields().size());
    assertEquals("version", header.fields().get(0).name());
    assertEquals("sequence", header.fields().get(1).name());

    ParsedMessageType packet = parsedSchema.messageTypes().get(1);
    assertEquals("acme.telemetry.packet", packet.namespaceOverride());
  }

  @Test
  void parserReadsNumericSliceAndPreservesMemberOrder() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/numeric-slice-valid.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);

    assertEquals("acme.telemetry.numeric", parsedSchema.namespace());
    assertEquals(1, parsedSchema.reusableBitFields().size());
    assertEquals(1, parsedSchema.reusableFloats().size());
    assertEquals(1, parsedSchema.reusableScaledInts().size());
    assertEquals("statusWord", parsedSchema.reusableBitFields().get(0).name());
    assertEquals("TelemetryFloat", parsedSchema.reusableFloats().get(0).name());

    ParsedMessageType message = parsedSchema.messageTypes().get(0);
    assertEquals("TelemetryFrame", message.name());
    assertEquals(6, message.members().size());
    assertEquals("version", message.fields().get(0).name());
    assertTrue(message.members().get(1) instanceof ParsedBitField);
    assertEquals("statusBits", ((ParsedBitField) message.members().get(1)).name());
    assertEquals("temperature", ((ParsedFloat) message.members().get(2)).name());
    assertEquals(FloatEncoding.SCALED, ((ParsedFloat) message.members().get(2)).encoding());
    assertTrue(message.members().get(3) instanceof ParsedScaledInt);
    assertEquals("reusableTemperature", message.fields().get(1).name());
    assertEquals("reusableFloat", message.fields().get(2).name());
  }

  @Test
  void parserReadsCollectionSliceAndPreservesMemberOrder() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/collections-slice-valid.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);

    assertEquals("acme.telemetry.collections", parsedSchema.namespace());
    assertEquals(1, parsedSchema.reusableArrays().size());
    assertEquals(2, parsedSchema.reusableVectors().size());
    assertEquals(1, parsedSchema.reusableBlobArrays().size());
    assertEquals(1, parsedSchema.reusableBlobVectors().size());

    assertEquals("BytePair", parsedSchema.reusableArrays().get(0).name());
    assertEquals("ByteStream", parsedSchema.reusableVectors().get(0).name());
    assertEquals("Signature", parsedSchema.reusableBlobArrays().get(0).name());
    assertEquals("PayloadBlob", parsedSchema.reusableBlobVectors().get(0).name());

    ParsedMessageType message = parsedSchema.messageTypes().get(0);
    assertEquals("CollectionFrame", message.name());
    assertEquals(11, message.members().size());

    assertEquals("count", message.fields().get(0).name());
    assertEquals("blobCount", message.fields().get(1).name());

    assertTrue(message.members().get(2) instanceof ParsedArray);
    assertEquals("samples", ((ParsedArray) message.members().get(2)).name());

    assertTrue(message.members().get(3) instanceof ParsedVector);
    ParsedVector countedVector = (ParsedVector) message.members().get(3);
    assertEquals("events", countedVector.name());
    assertTrue(countedVector.lengthMode() instanceof ParsedCountFieldLength);
    assertEquals("count", ((ParsedCountFieldLength) countedVector.lengthMode()).ref());

    assertTrue(message.members().get(4) instanceof ParsedBlobArray);
    assertEquals("hash", ((ParsedBlobArray) message.members().get(4)).name());

    assertTrue(message.members().get(5) instanceof ParsedBlobVector);
    ParsedBlobVector countedBlobVector = (ParsedBlobVector) message.members().get(5);
    assertEquals("payload", countedBlobVector.name());
    assertTrue(countedBlobVector.lengthMode() instanceof ParsedCountFieldLength);
    assertEquals("blobCount", ((ParsedCountFieldLength) countedBlobVector.lengthMode()).ref());

    assertTrue(message.members().get(6) instanceof ParsedVector);
    ParsedVector terminatorPathVector = (ParsedVector) message.members().get(6);
    assertEquals("pathData", terminatorPathVector.name());
    assertTrue(terminatorPathVector.lengthMode() instanceof ParsedTerminatorField);
    ParsedTerminatorField firstNode = (ParsedTerminatorField) terminatorPathVector.lengthMode();
    assertEquals("outer", firstNode.name());
    assertTrue(firstNode.next() instanceof ParsedTerminatorField);
    ParsedTerminatorField secondNode = (ParsedTerminatorField) firstNode.next();
    assertEquals("inner", secondNode.name());
    assertTrue(secondNode.next() instanceof ParsedTerminatorMatch);
    assertEquals("0x00", ((ParsedTerminatorMatch) secondNode.next()).value());
  }

  @Test
  void parserReadsMilestoneThreeSlice() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/milestone-03-valid.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);

    assertEquals(1, parsedSchema.reusableVarStrings().size());
    assertEquals(1, parsedSchema.reusableChecksums().size());
    assertEquals(1, parsedSchema.reusablePads().size());
    assertEquals("LabelText", parsedSchema.reusableVarStrings().get(0).name());
    assertEquals("crc32", parsedSchema.reusableChecksums().get(0).algorithm());
    assertEquals(2, parsedSchema.reusablePads().get(0).bytes());

    ParsedMessageType message = parsedSchema.messageTypes().get(0);
    assertEquals(8, message.members().size());
    assertTrue(message.members().get(2) instanceof ParsedVarString);
    assertTrue(message.members().get(4) instanceof ParsedPad);
    assertTrue(message.members().get(5) instanceof ParsedChecksum);
    assertTrue(message.members().get(6) instanceof ParsedIfBlock);
    assertTrue(message.members().get(7) instanceof ParsedMessageType);

    ParsedIfBlock ifBlock = (ParsedIfBlock) message.members().get(6);
    assertEquals("version == 1", ifBlock.test());
    assertEquals(3, ifBlock.members().size());
    assertTrue(ifBlock.members().get(1) instanceof ParsedVarString);
  }

  @Test
  void parserNormalizesStructuredCompoundIfCondition() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/conditional-if-relational-valid.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);
    ParsedMessageType messageType = parsedSchema.messageTypes().get(0);
    ParsedIfBlock ifBlock = (ParsedIfBlock) messageType.members().get(5);

    assertEquals("version >= 2 and version <= 10", ifBlock.test());
  }

  @Test
  void parserNormalizesQNameFieldTypeValues() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema
            xmlns="http://example.com/binarymessage"
            xmlns:bm="http://example.com/binarymessage"
            namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <field name="version" type="bm:uint8" comment="version"/>
          </messageType>
        </schema>
        """;

    Path specPath = writeTempSpec(xml);
    try {
      ParsedSchema parsedSchema = parser.parse(specPath);
      assertEquals("uint8", parsedSchema.messageTypes().get(0).fields().get(0).typeName());
    } finally {
      Files.deleteIfExists(specPath);
    }
  }

  @Test
  void parserReadsRootImportsInDeclarationOrder() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <import path="shared-types.xml"/>
          <import path="collections.xml"/>
          <messageType name="Frame" comment="frame">
            <field name="version" type="uint8" comment="version"/>
          </messageType>
        </schema>
        """;

    Path specPath = writeTempSpec(xml);
    try {
      ParsedSpecDocument parsedSpecDocument = parser.parseDocument(specPath);
      assertEquals(2, parsedSpecDocument.imports().size());
      assertEquals("shared-types.xml", parsedSpecDocument.imports().get(0).path());
      assertEquals("collections.xml", parsedSpecDocument.imports().get(1).path());
      assertEquals(1, parsedSpecDocument.schema().messageTypes().size());
    } finally {
      Files.deleteIfExists(specPath);
    }
  }
}
