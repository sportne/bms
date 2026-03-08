package io.github.sportne.bms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.testutil.TestSupport;
import io.github.sportne.bms.util.BmsException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for multi-file spec loading and composition. */
class BmsCompilerMultiFileTest {

  @TempDir Path tempDir;

  @Test
  void compilerResolvesCrossFileMessageReferencesAndSourceFileNamespaceFallback() throws Exception {
    writeSpec(
        "shared.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.shared">
          <messageType name="Header" comment="shared header">
            <field name="version" type="uint8" comment="version"/>
          </messageType>
        </schema>
        """);
    Path rootSpec =
        writeSpec(
            "root.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.root">
              <import path="shared.xml"/>
              <messageType name="Packet" comment="root packet">
                <field name="header" type="Header" comment="header ref"/>
              </messageType>
            </schema>
            """);

    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    var resolvedSchema = compiler.compile(rootSpec);

    Map<String, ResolvedMessageType> messageTypeByName =
        resolvedSchema.messageTypes().stream()
            .collect(Collectors.toMap(ResolvedMessageType::name, messageType -> messageType));
    assertEquals(2, messageTypeByName.size());
    assertEquals("acme.telemetry.shared", messageTypeByName.get("Header").effectiveNamespace());
    assertEquals("acme.telemetry.root", messageTypeByName.get("Packet").effectiveNamespace());
  }

  @Test
  void compilerResolvesRecursiveImports() throws Exception {
    writeSpec(
        "leaf.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.leaf">
          <messageType name="LeafType" comment="leaf">
            <field name="value" type="uint8" comment="value"/>
          </messageType>
        </schema>
        """);
    writeSpec(
        "mid.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.mid">
          <import path="leaf.xml"/>
          <messageType name="MidType" comment="mid">
            <field name="leaf" type="LeafType" comment="leaf ref"/>
          </messageType>
        </schema>
        """);
    Path rootSpec =
        writeSpec(
            "root.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.root">
              <import path="mid.xml"/>
              <messageType name="RootType" comment="root">
                <field name="mid" type="MidType" comment="mid ref"/>
              </messageType>
            </schema>
            """);

    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    var resolvedSchema = compiler.compile(rootSpec);
    assertEquals(3, resolvedSchema.messageTypes().size());
  }

  @Test
  void compilerRejectsImportCyclesWithClearDiagnostic() throws Exception {
    Path firstSpec =
        writeSpec(
            "a.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.a">
              <import path="b.xml"/>
              <messageType name="AType" comment="a">
                <field name="value" type="uint8" comment="value"/>
              </messageType>
            </schema>
            """);
    writeSpec(
        "b.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.b">
          <import path="a.xml"/>
          <messageType name="BType" comment="b">
            <field name="value" type="uint8" comment="value"/>
          </messageType>
        </schema>
        """);

    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    try {
      compiler.compile(firstSpec);
      fail("Expected import cycle to fail.");
    } catch (BmsException exception) {
      assertTrue(
          exception.diagnostics().stream()
              .anyMatch(diagnostic -> diagnostic.code().equals("IMPORT_CYCLE_DETECTED")));
      assertTrue(
          exception.diagnostics().stream()
              .anyMatch(
                  diagnostic ->
                      diagnostic.message().contains("a.xml")
                          && diagnostic.message().contains("b.xml")));
    }
  }

  @Test
  void compilerRejectsDuplicateTypeNamesAcrossDifferentSchemaNamespaces() throws Exception {
    writeSpec(
        "first.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.first">
          <messageType name="DupFrame" comment="dup one">
            <field name="version" type="uint8" comment="version"/>
          </messageType>
        </schema>
        """);
    Path secondSpec =
        writeSpec(
            "second.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.second">
              <messageType name="DupFrame" comment="dup two">
                <field name="version" type="uint8" comment="version"/>
              </messageType>
            </schema>
            """);
    Path rootSpec =
        writeSpec(
            "root.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.root">
              <import path="first.xml"/>
              <import path="second.xml"/>
              <messageType name="RootFrame" comment="root">
                <field name="version" type="uint8" comment="version"/>
              </messageType>
            </schema>
            """);

    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    try {
      compiler.compile(rootSpec);
      fail("Expected duplicate type names to fail.");
    } catch (BmsException exception) {
      assertTrue(
          exception.diagnostics().stream()
              .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_MESSAGE_TYPE")));
      assertTrue(
          exception.diagnostics().stream()
              .anyMatch(
                  diagnostic ->
                      diagnostic.code().equals("SEMANTIC_DUPLICATE_MESSAGE_TYPE")
                          && diagnostic.sourcePath().equals(secondSpec.toAbsolutePath().toString())
                          && diagnostic.message().contains("First defined in")));
    }
  }

  @Test
  void compilerReportsUnknownTypeFromReferencingFile() throws Exception {
    Path sharedSpec =
        writeSpec(
            "shared.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.shared">
              <messageType name="SharedFrame" comment="shared">
                <field name="missing" type="DoesNotExist" comment="missing"/>
              </messageType>
            </schema>
            """);
    Path rootSpec =
        writeSpec(
            "root.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry.root">
              <import path="shared.xml"/>
              <messageType name="RootFrame" comment="root">
                <field name="version" type="uint8" comment="version"/>
              </messageType>
            </schema>
            """);

    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    try {
      compiler.compile(rootSpec);
      fail("Expected unknown type to fail.");
    } catch (BmsException exception) {
      assertTrue(
          exception.diagnostics().stream()
              .anyMatch(
                  diagnostic ->
                      diagnostic.code().equals("SEMANTIC_UNKNOWN_TYPE")
                          && diagnostic
                              .sourcePath()
                              .equals(sharedSpec.toAbsolutePath().toString())));
    }
  }

  /**
   * Writes one XML spec file under the test temp directory.
   *
   * @param fileName output file name
   * @param xml XML file contents
   * @return path to the written file
   * @throws IOException if writing fails
   */
  private Path writeSpec(String fileName, String xml) throws IOException {
    Path specPath = tempDir.resolve(fileName);
    Files.writeString(specPath, xml, StandardCharsets.UTF_8);
    return specPath;
  }
}
