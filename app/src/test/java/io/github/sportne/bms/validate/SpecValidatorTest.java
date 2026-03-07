package io.github.sportne.bms.validate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.testutil.TestSupport;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostics;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecValidatorTest {
  @TempDir Path tempDir;

  @Test
  void validSpecPassesXsdValidation() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/valid-foundation.xml");

    assertFalse(Diagnostics.hasErrors(validator.validate(specPath)));
  }

  @Test
  void missingSchemaNamespaceFailsXsdValidation() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/missing-schema-namespace.xml");

    BmsException exception =
        assertThrows(BmsException.class, () -> validator.validateOrThrow(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().startsWith("XSD")));
  }

  @Test
  void messageNamespaceOverrideRemainsValidAgainstXsd() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/valid-foundation.xml");

    validator.validateOrThrow(specPath);
  }

  @Test
  void validatorReportsIoErrorForMissingSpecPath() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path missingPath = tempDir.resolve("missing.xml");

    assertFalse(Files.exists(missingPath));
    assertTrue(
        validator.validate(missingPath).stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("XSD_IO_ERROR")));
  }

  @Test
  void fromXsdFailsWhenSchemaPathDoesNotExist() {
    Path missingSchemaPath = tempDir.resolve("missing-schema.xsd");

    BmsException exception =
        assertThrows(BmsException.class, () -> SpecValidator.fromXsd(missingSchemaPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("VALIDATOR_SCHEMA_LOAD_FAILED")));
  }

  @Test
  void malformedXmlProducesXsdDiagnostic() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path malformedSpec = TestSupport.resourcePath("specs/malformed.xml");

    assertTrue(
        validator.validate(malformedSpec).stream()
            .anyMatch(diagnostic -> diagnostic.code().startsWith("XSD")));
  }
}
