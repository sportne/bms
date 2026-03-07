package io.github.sportne.bms.codegen.cpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.Endian;
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

class CppCodeGeneratorTest {

  @TempDir Path tempDir;

  @Test
  void cppGeneratorProducesDeterministicNamespaceAwareOutput() throws Exception {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedSchema schema = sampleSchema();

    generator.generate(schema, tempDir);
    generator.generate(schema, tempDir);

    Path headerHeaderPath = tempDir.resolve("acme/telemetry/Header.hpp");
    Path headerSourcePath = tempDir.resolve("acme/telemetry/Header.cpp");
    Path packetHeaderPath = tempDir.resolve("acme/telemetry/packet/Packet.hpp");
    Path packetSourcePath = tempDir.resolve("acme/telemetry/packet/Packet.cpp");

    assertTrue(Files.exists(headerHeaderPath));
    assertTrue(Files.exists(headerSourcePath));
    assertTrue(Files.exists(packetHeaderPath));
    assertTrue(Files.exists(packetSourcePath));

    assertEquals(
        TestSupport.readResource("golden/cpp/acme/telemetry/Header.hpp"),
        Files.readString(headerHeaderPath, StandardCharsets.UTF_8));
    assertEquals(
        TestSupport.readResource("golden/cpp/acme/telemetry/Header.cpp"),
        Files.readString(headerSourcePath, StandardCharsets.UTF_8));
    assertEquals(
        TestSupport.readResource("golden/cpp/acme/telemetry/packet/Packet.hpp"),
        Files.readString(packetHeaderPath, StandardCharsets.UTF_8));
    assertEquals(
        TestSupport.readResource("golden/cpp/acme/telemetry/packet/Packet.cpp"),
        Files.readString(packetSourcePath, StandardCharsets.UTF_8));
  }

  private static ResolvedSchema sampleSchema() {
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
                    Endian.LITTLE,
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
