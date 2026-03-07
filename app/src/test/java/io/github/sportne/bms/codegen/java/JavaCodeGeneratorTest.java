package io.github.sportne.bms.codegen.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.testutil.TestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaCodeGeneratorTest {

  @TempDir Path tempDir;

  @Test
  void javaGeneratorProducesDeterministicNamespaceAwareOutput() throws Exception {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedSchema schema = sampleSchema();

    generator.generate(schema, tempDir);
    generator.generate(schema, tempDir);

    Path headerPath = tempDir.resolve("acme/telemetry/Header.java");
    Path packetPath = tempDir.resolve("acme/telemetry/packet/Packet.java");

    assertTrue(Files.exists(headerPath));
    assertTrue(Files.exists(packetPath));

    String expectedHeader = TestSupport.readResource("golden/java/acme/telemetry/Header.java");
    String expectedPacket =
        TestSupport.readResource("golden/java/acme/telemetry/packet/Packet.java");

    assertEquals(expectedHeader, Files.readString(headerPath, StandardCharsets.UTF_8));
    assertEquals(expectedPacket, Files.readString(packetPath, StandardCharsets.UTF_8));
  }

  static ResolvedSchema sampleSchema() {
    ResolvedMessageType header =
        new ResolvedMessageType(
            "Header",
            "header",
            "acme.telemetry",
            List.of(
                new ResolvedField(
                    "version",
                    new PrimitiveTypeRef(PrimitiveType.UINT8),
                    null,
                    null,
                    null,
                    "version"),
                new ResolvedField(
                    "sequence",
                    new PrimitiveTypeRef(PrimitiveType.UINT16),
                    null,
                    io.github.sportne.bms.model.Endian.LITTLE,
                    null,
                    "sequence")));

    ResolvedMessageType packet =
        new ResolvedMessageType(
            "Packet",
            "packet",
            "acme.telemetry.packet",
            List.of(
                new ResolvedField(
                    "header", new MessageTypeRef("Header"), null, null, null, "header"),
                new ResolvedField(
                    "payloadLength",
                    new PrimitiveTypeRef(PrimitiveType.UINT32),
                    null,
                    null,
                    null,
                    "payloadLength")));

    return new ResolvedSchema("acme.telemetry", List.of(header, packet));
  }
}
