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

/**
 * Contract tests for XSD validation behavior.
 *
 * <p>These tests define what the first compiler stage must accept and reject before parsing.
 */
class SpecValidatorTest {
  @TempDir Path tempDir;

  /** Contract: the foundation example passes pure XSD validation. */
  @Test
  void validSpecPassesXsdValidation() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/valid-foundation.xml");

    assertFalse(Diagnostics.hasErrors(validator.validate(specPath)));
  }

  /** Contract: schema namespace is required by the XSD contract. */
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

  /** Contract: message-level namespace override remains valid against the current XSD. */
  @Test
  void messageNamespaceOverrideRemainsValidAgainstXsd() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/valid-foundation.xml");

    validator.validateOrThrow(specPath);
  }

  /** Contract: numeric slice elements are valid at the XSD layer. */
  @Test
  void numericSliceSpecPassesXsdValidation() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/numeric-slice-valid.xml");

    validator.validateOrThrow(specPath);
  }

  /** Contract: collection + terminator slice elements are valid at the XSD layer. */
  @Test
  void collectionSliceSpecPassesXsdValidation() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/collections-slice-valid.xml");

    validator.validateOrThrow(specPath);
  }

  /** Contract: milestone-03 elements are valid at the XSD layer. */
  @Test
  void milestoneThreeSpecPassesXsdValidation() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/milestone-03-valid.xml");

    validator.validateOrThrow(specPath);
  }

  /** Contract: bitField now requires a `name` attribute by XSD contract. */
  @Test
  void missingBitFieldNameFailsXsdValidation() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/missing-bitfield-name.xml");

    BmsException exception =
        assertThrows(BmsException.class, () -> validator.validateOrThrow(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().startsWith("XSD")));
  }

  /** Contract: logicalOperator in structured `if` comparisons must use enum values from XSD. */
  @Test
  void invalidIfLogicalOperatorFailsXsdValidation() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path specPath = TestSupport.resourcePath("specs/if-invalid-logical-operator.xml");

    BmsException exception =
        assertThrows(BmsException.class, () -> validator.validateOrThrow(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().startsWith("XSD")));
  }

  /** Contract: missing input files produce an IO diagnostic instead of crashing. */
  @Test
  void validatorReportsIoErrorForMissingSpecPath() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path missingPath = tempDir.resolve("missing.xml");

    assertFalse(Files.exists(missingPath));
    assertTrue(
        validator.validate(missingPath).stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("XSD_IO_ERROR")));
  }

  /** Contract: creating a validator with a missing XSD path fails with a clear diagnostic. */
  @Test
  void fromXsdFailsWhenSchemaPathDoesNotExist() {
    Path missingSchemaPath = tempDir.resolve("missing-schema.xsd");

    BmsException exception =
        assertThrows(BmsException.class, () -> SpecValidator.fromXsd(missingSchemaPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("VALIDATOR_SCHEMA_LOAD_FAILED")));
  }

  /** Contract: malformed XML is surfaced as an XSD-stage diagnostic. */
  @Test
  void malformedXmlProducesXsdDiagnostic() throws Exception {
    SpecValidator validator = SpecValidator.fromXsd(TestSupport.repositoryXsdPath());
    Path malformedSpec = TestSupport.resourcePath("specs/malformed.xml");

    assertTrue(
        validator.validate(malformedSpec).stream()
            .anyMatch(diagnostic -> diagnostic.code().startsWith("XSD")));
  }
}
