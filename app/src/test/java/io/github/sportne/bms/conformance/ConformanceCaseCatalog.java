package io.github.sportne.bms.conformance;

import java.util.List;

/** Catalog of canonical cross-language conformance cases used by Milestone 05 tests. */
final class ConformanceCaseCatalog {

  /** Utility class; not meant to be instantiated. */
  private ConformanceCaseCatalog() {}

  /**
   * Returns the full valid fixture matrix for cross-language conformance checks.
   *
   * @return ordered conformance case list
   */
  static List<ConformanceCase> cases() {
    return List.of(
        foundationPacketCase(),
        numericTelemetryCase(),
        collectionsCase(),
        varStringPadCase(),
        conditionalBackendCase(),
        relationalConditionalCase(),
        checksumCrc16Case(),
        checksumCrc32Case(),
        checksumCrc64Case(),
        checksumSha256Case(),
        allSupportedCase(),
        coverageCase());
  }

  /**
   * Builds the foundation packet conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase foundationPacketCase() {
    return new ConformanceCase(
        "foundation_packet",
        "specs/valid-foundation.xml",
        "acme.telemetry.packet.Packet",
        "acme/telemetry/packet/Packet.hpp",
        "acme::telemetry::packet::Packet",
        (target, classLoader) -> {
          Object header =
              ConformanceRuntimeSupport.newInstance(classLoader, "acme.telemetry.Header");
          ConformanceRuntimeSupport.setNumericField(header, "version", 3);
          ConformanceRuntimeSupport.setNumericField(header, "sequence", 513);
          ConformanceRuntimeSupport.setFieldValue(target, "header", header);
          ConformanceRuntimeSupport.setNumericField(target, "payloadLength", 1024);
        },
        (decoded, unusedClassLoader) -> {
          Object decodedHeader = ConformanceRuntimeSupport.getFieldValue(decoded, "header");
          ConformanceRuntimeSupport.assertNumericFieldEquals(decodedHeader, "version", 3);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decodedHeader, "sequence", 513);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "payloadLength", 1024);
        },
        """
        source.header.version = static_cast<std::uint8_t>(3);
        source.header.sequence = static_cast<std::uint16_t>(513);
        source.payloadLength = static_cast<std::uint32_t>(1024);
        """,
        """
        if (decoded.header.version != static_cast<std::uint8_t>(3)) {
          return 11;
        }
        if (decoded.header.sequence != static_cast<std::uint16_t>(513)) {
          return 12;
        }
        if (decoded.payloadLength != static_cast<std::uint32_t>(1024)) {
          return 13;
        }
        """);
  }

  /**
   * Builds the numeric telemetry conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase numericTelemetryCase() {
    return new ConformanceCase(
        "numeric_telemetry",
        "specs/numeric-slice-valid.xml",
        "acme.telemetry.numeric.TelemetryFrame",
        "acme/telemetry/numeric/TelemetryFrame.hpp",
        "acme::telemetry::numeric::TelemetryFrame",
        (target, unusedClassLoader) -> {
          ConformanceRuntimeSupport.setNumericField(target, "version", 2);
          ConformanceRuntimeSupport.setNumericField(target, "statusBits", 5);
          ConformanceRuntimeSupport.setFloatingField(target, "temperature", 25.3d);
          ConformanceRuntimeSupport.setFloatingField(target, "voltage", 12.34d);
          ConformanceRuntimeSupport.setFloatingField(target, "reusableTemperature", -1.25d);
          ConformanceRuntimeSupport.setFloatingField(target, "reusableFloat", 1.5d);
        },
        (decoded, unusedClassLoader) -> {
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "version", 2);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "statusBits", 5);
          ConformanceRuntimeSupport.assertFloatingFieldEquals(decoded, "temperature", 25.3d, 0.11d);
          ConformanceRuntimeSupport.assertFloatingFieldEquals(decoded, "voltage", 12.34d, 0.02d);
          ConformanceRuntimeSupport.assertFloatingFieldEquals(
              decoded, "reusableTemperature", -1.25d, 0.02d);
          ConformanceRuntimeSupport.assertFloatingFieldEquals(
              decoded, "reusableFloat", 1.5d, 0.0001d);
        },
        """
        source.version = static_cast<std::uint8_t>(2);
        source.statusBits = static_cast<std::uint8_t>(5);
        source.temperature = 25.3;
        source.voltage = 12.34;
        source.reusableTemperature = -1.25;
        source.reusableFloat = 1.5;
        """,
        """
        if (decoded.version != static_cast<std::uint8_t>(2)) {
          return 21;
        }
        if (decoded.statusBits != static_cast<std::uint8_t>(5)) {
          return 22;
        }
        if (std::abs(decoded.temperature - 25.3) > 0.11) {
          return 23;
        }
        if (std::abs(decoded.voltage - 12.34) > 0.02) {
          return 24;
        }
        if (std::abs(decoded.reusableTemperature + 1.25) > 0.02) {
          return 25;
        }
        if (std::abs(decoded.reusableFloat - 1.5) > 0.0001) {
          return 26;
        }
        """);
  }

  /**
   * Builds the collection-slice conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase collectionsCase() {
    return new ConformanceCase(
        "collections_frame",
        "specs/collections-slice-valid.xml",
        "acme.telemetry.collections.CollectionFrame",
        "acme/telemetry/collections/CollectionFrame.hpp",
        "acme::telemetry::collections::CollectionFrame",
        (target, unusedClassLoader) -> {
          ConformanceRuntimeSupport.setNumericField(target, "count", 3);
          ConformanceRuntimeSupport.setNumericField(target, "blobCount", 2);
          ConformanceRuntimeSupport.setNumericArrayField(
              target, "samples", new long[] {1, 2, 3, 4});
          ConformanceRuntimeSupport.setNumericArrayField(target, "events", new long[] {5, 6, 7});
          ConformanceRuntimeSupport.setByteArrayField(
              target, "hash", new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
          ConformanceRuntimeSupport.setByteArrayField(target, "payload", new byte[] {9, 10});
          ConformanceRuntimeSupport.setNumericArrayField(target, "pathData", new long[] {11, 12});
          ConformanceRuntimeSupport.setNumericArrayField(
              target, "reusableArrayField", new long[] {13, 14});
          ConformanceRuntimeSupport.setNumericArrayField(
              target, "reusableVectorField", new long[] {21, 22, 23});
          ConformanceRuntimeSupport.setByteArrayField(
              target,
              "reusableBlobArrayField",
              new byte[] {31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46});
          ConformanceRuntimeSupport.setByteArrayField(
              target, "reusableBlobVectorField", new byte[] {51, 52, 53});
        },
        (decoded, unusedClassLoader) -> {
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "count", 3);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "blobCount", 2);
          ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
              decoded, "samples", new long[] {1, 2, 3, 4});
          ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
              decoded, "events", new long[] {5, 6, 7});
          ConformanceRuntimeSupport.assertByteArrayFieldEquals(
              decoded, "hash", new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
          ConformanceRuntimeSupport.assertByteArrayFieldEquals(
              decoded, "payload", new byte[] {9, 10});
          ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
              decoded, "pathData", new long[] {11, 12});
          ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
              decoded, "reusableArrayField", new long[] {13, 14});
          ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
              decoded, "reusableVectorField", new long[] {21, 22, 23});
          ConformanceRuntimeSupport.assertByteArrayFieldEquals(
              decoded,
              "reusableBlobArrayField",
              new byte[] {31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46});
          ConformanceRuntimeSupport.assertByteArrayFieldEquals(
              decoded, "reusableBlobVectorField", new byte[] {51, 52, 53});
        },
        """
        source.count = static_cast<std::uint16_t>(3);
        source.blobCount = static_cast<std::uint8_t>(2);
        source.samples = std::array<std::uint8_t, 4>{1U, 2U, 3U, 4U};
        source.events = std::vector<std::uint8_t>{5U, 6U, 7U};
        source.hash = std::array<std::uint8_t, 8>{0U, 1U, 2U, 3U, 4U, 5U, 6U, 7U};
        source.payload = std::vector<std::uint8_t>{9U, 10U};
        source.pathData = std::vector<std::uint8_t>{11U, 12U};
        source.reusableArrayField = std::array<std::uint8_t, 2>{13U, 14U};
        source.reusableVectorField = std::vector<std::uint8_t>{21U, 22U, 23U};
        source.reusableBlobArrayField =
            std::array<std::uint8_t, 16>{31U, 32U, 33U, 34U, 35U, 36U, 37U, 38U,
                                          39U, 40U, 41U, 42U, 43U, 44U, 45U, 46U};
        source.reusableBlobVectorField = std::vector<std::uint8_t>{51U, 52U, 53U};
        """,
        """
        if (decoded.count != static_cast<std::uint16_t>(3)) {
          return 31;
        }
        if (decoded.blobCount != static_cast<std::uint8_t>(2)) {
          return 32;
        }
        if (decoded.samples != std::array<std::uint8_t, 4>{1U, 2U, 3U, 4U}) {
          return 33;
        }
        if (decoded.events != std::vector<std::uint8_t>{5U, 6U, 7U}) {
          return 34;
        }
        if (decoded.hash != std::array<std::uint8_t, 8>{0U, 1U, 2U, 3U, 4U, 5U, 6U, 7U}) {
          return 35;
        }
        if (decoded.payload != std::vector<std::uint8_t>{9U, 10U}) {
          return 36;
        }
        if (decoded.pathData != std::vector<std::uint8_t>{11U, 12U}) {
          return 37;
        }
        if (decoded.reusableArrayField != std::array<std::uint8_t, 2>{13U, 14U}) {
          return 38;
        }
        if (decoded.reusableVectorField != std::vector<std::uint8_t>{21U, 22U, 23U}) {
          return 39;
        }
        if (decoded.reusableBlobArrayField !=
            std::array<std::uint8_t, 16>{31U, 32U, 33U, 34U, 35U, 36U, 37U, 38U,
                                         39U, 40U, 41U, 42U, 43U, 44U, 45U, 46U}) {
          return 40;
        }
        if (decoded.reusableBlobVectorField != std::vector<std::uint8_t>{51U, 52U, 53U}) {
          return 41;
        }
        """);
  }

  /**
   * Builds the varString and pad conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase varStringPadCase() {
    return new ConformanceCase(
        "conditional_varstring_pad",
        "specs/varstring-pad-slice-valid.xml",
        "acme.telemetry.conditional.ConditionalFrame",
        "acme/telemetry/conditional/ConditionalFrame.hpp",
        "acme::telemetry::conditional::ConditionalFrame",
        (target, unusedClassLoader) -> {
          ConformanceRuntimeSupport.setNumericField(target, "nameLength", 4);
          ConformanceRuntimeSupport.setNumericField(target, "reusableLength", 5);
          ConformanceRuntimeSupport.setFieldValue(target, "inlineName", "ABCD");
          ConformanceRuntimeSupport.setFieldValue(target, "inlineTag", "TAG");
          ConformanceRuntimeSupport.setFieldValue(target, "reusableLabel", "HELLO");
        },
        (decoded, unusedClassLoader) -> {
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "nameLength", 4);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "reusableLength", 5);
          ConformanceRuntimeSupport.assertStringFieldEquals(decoded, "inlineName", "ABCD");
          ConformanceRuntimeSupport.assertStringFieldEquals(decoded, "inlineTag", "TAG");
          ConformanceRuntimeSupport.assertStringFieldEquals(decoded, "reusableLabel", "HELLO");
        },
        """
        source.nameLength = static_cast<std::uint8_t>(4);
        source.reusableLength = static_cast<std::uint16_t>(5);
        source.inlineName = "ABCD";
        source.inlineTag = "TAG";
        source.reusableLabel = "HELLO";
        """,
        """
        if (decoded.nameLength != static_cast<std::uint8_t>(4)) {
          return 51;
        }
        if (decoded.reusableLength != static_cast<std::uint16_t>(5)) {
          return 52;
        }
        if (decoded.inlineName != std::string("ABCD")) {
          return 53;
        }
        if (decoded.inlineTag != std::string("TAG")) {
          return 54;
        }
        if (decoded.reusableLabel != std::string("HELLO")) {
          return 55;
        }
        """);
  }

  /**
   * Builds the checksum/if/nested conditional conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase conditionalBackendCase() {
    return new ConformanceCase(
        "conditional_backend",
        "specs/conditional-backend-valid.xml",
        "acme.telemetry.conditional.backend.ConditionalBackendFrame",
        "acme/telemetry/conditional/backend/ConditionalBackendFrame.hpp",
        "acme::telemetry::conditional::backend::ConditionalBackendFrame",
        (target, unusedClassLoader) -> {
          ConformanceRuntimeSupport.setNumericField(target, "version", 1);
          ConformanceRuntimeSupport.setNumericField(target, "payload", 9);
          ConformanceRuntimeSupport.setNumericField(target, "modeValue", 7);
          ConformanceRuntimeSupport.setNumericField(target, "nestedValue", 4660);
          ConformanceRuntimeSupport.setNumericField(target, "alwaysValue", 3);
        },
        (decoded, unusedClassLoader) -> {
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "version", 1);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "payload", 9);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "modeValue", 7);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "nestedValue", 4660);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "alwaysValue", 3);
        },
        """
        source.version = static_cast<std::uint8_t>(1);
        source.payload = static_cast<std::uint8_t>(9);
        source.modeValue = static_cast<std::uint8_t>(7);
        source.nestedValue = static_cast<std::uint16_t>(4660);
        source.alwaysValue = static_cast<std::uint8_t>(3);
        """,
        """
        if (decoded.version != static_cast<std::uint8_t>(1)) {
          return 61;
        }
        if (decoded.payload != static_cast<std::uint8_t>(9)) {
          return 62;
        }
        if (decoded.modeValue != static_cast<std::uint8_t>(7)) {
          return 63;
        }
        if (decoded.nestedValue != static_cast<std::uint16_t>(4660)) {
          return 64;
        }
        if (decoded.alwaysValue != static_cast<std::uint8_t>(3)) {
          return 65;
        }
        """);
  }

  /**
   * Builds the relational and compound conditional conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase relationalConditionalCase() {
    return new ConformanceCase(
        "conditional_relational",
        "specs/conditional-if-relational-valid.xml",
        "acme.telemetry.conditional.relational.ConditionalRelationalFrame",
        "acme/telemetry/conditional/relational/ConditionalRelationalFrame.hpp",
        "acme::telemetry::conditional::relational::ConditionalRelationalFrame",
        (target, unusedClassLoader) -> {
          ConformanceRuntimeSupport.setNumericField(target, "version", 2);
          ConformanceRuntimeSupport.setNumericField(target, "ltMode", 11);
          ConformanceRuntimeSupport.setNumericField(target, "lteMode", 12);
          ConformanceRuntimeSupport.setNumericField(target, "gtMode", 13);
          ConformanceRuntimeSupport.setNumericField(target, "gteMode", 14);
          ConformanceRuntimeSupport.setNumericField(target, "betweenMode", 15);
          ConformanceRuntimeSupport.setNumericField(target, "compoundMode", 16);
        },
        (decoded, unusedClassLoader) -> {
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "version", 2);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "ltMode", 11);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "lteMode", 12);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "gtMode", 13);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "gteMode", 14);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "betweenMode", 15);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "compoundMode", 16);
        },
        """
        source.version = static_cast<std::uint8_t>(2);
        source.ltMode = static_cast<std::uint8_t>(11);
        source.lteMode = static_cast<std::uint8_t>(12);
        source.gtMode = static_cast<std::uint8_t>(13);
        source.gteMode = static_cast<std::uint8_t>(14);
        source.betweenMode = static_cast<std::uint8_t>(15);
        source.compoundMode = static_cast<std::uint8_t>(16);
        """,
        """
        if (decoded.version != static_cast<std::uint8_t>(2)) {
          return 71;
        }
        if (decoded.ltMode != static_cast<std::uint8_t>(11)) {
          return 72;
        }
        if (decoded.lteMode != static_cast<std::uint8_t>(12)) {
          return 73;
        }
        if (decoded.gtMode != static_cast<std::uint8_t>(13)) {
          return 74;
        }
        if (decoded.gteMode != static_cast<std::uint8_t>(14)) {
          return 75;
        }
        if (decoded.betweenMode != static_cast<std::uint8_t>(15)) {
          return 76;
        }
        if (decoded.compoundMode != static_cast<std::uint8_t>(16)) {
          return 77;
        }
        """);
  }

  /**
   * Builds one checksum-only conformance case.
   *
   * @param id case identifier
   * @param fixture fixture resource path
   * @param javaClass generated Java class name
   * @param cppType generated C++ qualified type name
   * @param cppHeader generated C++ header path
   * @return conformance case definition
   */
  private static ConformanceCase checksumCase(
      String id, String fixture, String javaClass, String cppType, String cppHeader) {
    return new ConformanceCase(
        id,
        fixture,
        javaClass,
        cppHeader,
        cppType,
        (target, unusedClassLoader) -> {
          ConformanceRuntimeSupport.setNumericField(target, "version", 2);
          ConformanceRuntimeSupport.setNumericField(target, "payload", 7);
        },
        (decoded, unusedClassLoader) -> {
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "version", 2);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "payload", 7);
        },
        """
        source.version = static_cast<std::uint8_t>(2);
        source.payload = static_cast<std::uint8_t>(7);
        """,
        """
        if (decoded.version != static_cast<std::uint8_t>(2)) {
          return 81;
        }
        if (decoded.payload != static_cast<std::uint8_t>(7)) {
          return 82;
        }
        """);
  }

  /**
   * Builds the crc16 checksum conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase checksumCrc16Case() {
    return checksumCase(
        "checksum_crc16",
        "specs/checksum-crc16-valid.xml",
        "acme.telemetry.conditional.algorithms.ChecksumCrc16Frame",
        "acme::telemetry::conditional::algorithms::ChecksumCrc16Frame",
        "acme/telemetry/conditional/algorithms/ChecksumCrc16Frame.hpp");
  }

  /**
   * Builds the crc32 checksum conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase checksumCrc32Case() {
    return checksumCase(
        "checksum_crc32",
        "specs/checksum-crc32-valid.xml",
        "acme.telemetry.conditional.algorithms.ChecksumCrc32Frame",
        "acme::telemetry::conditional::algorithms::ChecksumCrc32Frame",
        "acme/telemetry/conditional/algorithms/ChecksumCrc32Frame.hpp");
  }

  /**
   * Builds the crc64 checksum conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase checksumCrc64Case() {
    return checksumCase(
        "checksum_crc64",
        "specs/checksum-crc64-valid.xml",
        "acme.telemetry.conditional.algorithms.ChecksumCrc64Frame",
        "acme::telemetry::conditional::algorithms::ChecksumCrc64Frame",
        "acme/telemetry/conditional/algorithms/ChecksumCrc64Frame.hpp");
  }

  /**
   * Builds the sha256 checksum conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase checksumSha256Case() {
    return checksumCase(
        "checksum_sha256",
        "specs/checksum-sha256-valid.xml",
        "acme.telemetry.conditional.algorithms.ChecksumSha256Frame",
        "acme::telemetry::conditional::algorithms::ChecksumSha256Frame",
        "acme/telemetry/conditional/algorithms/ChecksumSha256Frame.hpp");
  }

  /**
   * Builds the all-supported mixed-feature conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase allSupportedCase() {
    return new ConformanceCase(
        "all_supported",
        "specs/java-e2e-all-supported-valid.xml",
        "acme.telemetry.e2e.AllSupportedFrame",
        "acme/telemetry/e2e/AllSupportedFrame.hpp",
        "acme::telemetry::e2e::AllSupportedFrame",
        (target, unusedClassLoader) -> {
          ConformanceRuntimeSupport.setNumericField(target, "version", 2);
          ConformanceRuntimeSupport.setNumericField(target, "count", 2);
          ConformanceRuntimeSupport.setNumericField(target, "nameLength", 4);
          ConformanceRuntimeSupport.setNumericField(target, "statusBits", 3);
          ConformanceRuntimeSupport.setFloatingField(target, "ratio", 1.5d);
          ConformanceRuntimeSupport.setFloatingField(target, "temperature", 25.3d);
          ConformanceRuntimeSupport.setNumericArrayField(target, "fixedValues", new long[] {1, 2});
          ConformanceRuntimeSupport.setNumericArrayField(target, "samples", new long[] {257, 513});
          ConformanceRuntimeSupport.setByteArrayField(target, "payload", new byte[] {10, 20, 30});
          ConformanceRuntimeSupport.setByteArrayField(target, "tail", new byte[] {7, 8, 9});
          ConformanceRuntimeSupport.setFieldValue(target, "title", "BMS!");
          ConformanceRuntimeSupport.setNumericField(target, "modeValue", 4);
          ConformanceRuntimeSupport.setNumericField(target, "nestedValue", 1025);
          ConformanceRuntimeSupport.setNumericField(target, "alwaysValue", 9);
        },
        (decoded, unusedClassLoader) -> {
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "version", 2);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "count", 2);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "nameLength", 4);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "statusBits", 3);
          ConformanceRuntimeSupport.assertFloatingFieldEquals(decoded, "ratio", 1.5d, 0.0001d);
          ConformanceRuntimeSupport.assertFloatingFieldEquals(decoded, "temperature", 25.3d, 0.11d);
          ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
              decoded, "fixedValues", new long[] {1, 2});
          ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
              decoded, "samples", new long[] {257, 513});
          ConformanceRuntimeSupport.assertByteArrayFieldEquals(
              decoded, "payload", new byte[] {10, 20, 30});
          ConformanceRuntimeSupport.assertByteArrayFieldEquals(
              decoded, "tail", new byte[] {7, 8, 9});
          ConformanceRuntimeSupport.assertStringFieldEquals(decoded, "title", "BMS!");
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "modeValue", 4);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "nestedValue", 1025);
          ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "alwaysValue", 9);
        },
        """
        source.version = static_cast<std::uint8_t>(2);
        source.count = static_cast<std::uint8_t>(2);
        source.nameLength = static_cast<std::uint8_t>(4);
        source.statusBits = static_cast<std::uint8_t>(3);
        source.ratio = 1.5;
        source.temperature = 25.3;
        source.fixedValues = std::array<std::uint8_t, 2>{1U, 2U};
        source.samples = std::vector<std::uint16_t>{257U, 513U};
        source.payload = std::array<std::uint8_t, 3>{10U, 20U, 30U};
        source.tail = std::vector<std::uint8_t>{7U, 8U, 9U};
        source.title = "BMS!";
        source.modeValue = static_cast<std::uint8_t>(4);
        source.nestedValue = static_cast<std::uint16_t>(1025);
        source.alwaysValue = static_cast<std::uint8_t>(9);
        """,
        """
        if (decoded.version != static_cast<std::uint8_t>(2)) {
          return 91;
        }
        if (decoded.count != static_cast<std::uint8_t>(2)) {
          return 92;
        }
        if (decoded.nameLength != static_cast<std::uint8_t>(4)) {
          return 93;
        }
        if (decoded.statusBits != static_cast<std::uint8_t>(3)) {
          return 94;
        }
        if (std::abs(decoded.ratio - 1.5) > 0.0001) {
          return 95;
        }
        if (std::abs(decoded.temperature - 25.3) > 0.11) {
          return 96;
        }
        if (decoded.fixedValues != std::array<std::uint8_t, 2>{1U, 2U}) {
          return 97;
        }
        if (decoded.samples != std::vector<std::uint16_t>{257U, 513U}) {
          return 98;
        }
        if (decoded.payload != std::array<std::uint8_t, 3>{10U, 20U, 30U}) {
          return 99;
        }
        if (decoded.tail != std::vector<std::uint8_t>{7U, 8U, 9U}) {
          return 100;
        }
        if (decoded.title != std::string("BMS!")) {
          return 101;
        }
        if (decoded.modeValue != static_cast<std::uint8_t>(4)) {
          return 102;
        }
        if (decoded.nestedValue != static_cast<std::uint16_t>(1025)) {
          return 103;
        }
        if (decoded.alwaysValue != static_cast<std::uint8_t>(9)) {
          return 104;
        }
        """);
  }

  /**
   * Builds the generator-coverage conformance case.
   *
   * @return conformance case definition
   */
  private static ConformanceCase coverageCase() {
    return new ConformanceCase(
        "coverage_extended",
        "specs/java-generator-coverage-valid.xml",
        "acme.telemetry.coverage.ExtendedFrame",
        "acme/telemetry/coverage/ExtendedFrame.hpp",
        "acme::telemetry::coverage::ExtendedFrame",
        coverageJavaConfigurer(),
        coverageJavaAsserter(),
        coverageCppSourceSetupSnippet(),
        coverageCppDecodedAssertionSnippet());
  }

  /**
   * Returns Java setup logic for the coverage fixture.
   *
   * @return Java setup callback
   */
  private static ConformanceCase.JavaConfigurer coverageJavaConfigurer() {
    return (target, classLoader) -> {
      ConformanceRuntimeSupport.setNumericField(target, "count64", 2);
      ConformanceRuntimeSupport.setNumericField(target, "status16", 3);
      ConformanceRuntimeSupport.setNumericField(target, "status32", 257);
      ConformanceRuntimeSupport.setNumericField(target, "status64", 17);
      ConformanceRuntimeSupport.setFloatingField(target, "halfIeeeInline", 1.5d);
      ConformanceRuntimeSupport.setFloatingField(target, "doubleIeeeInline", 2.5d);
      ConformanceRuntimeSupport.setFloatingField(target, "halfScaledInline", 12.5d);
      ConformanceRuntimeSupport.setFloatingField(target, "doubleScaledInline", 15.0d);
      ConformanceRuntimeSupport.setFloatingField(target, "i8ScaledInline", -4.0d);
      ConformanceRuntimeSupport.setFloatingField(target, "u8ScaledInline", 6.0d);
      ConformanceRuntimeSupport.setFloatingField(target, "i32ScaledInline", 7.0d);
      ConformanceRuntimeSupport.setFloatingField(target, "u32ScaledInline", 8.0d);
      ConformanceRuntimeSupport.setFloatingField(target, "i64ScaledInline", 9.0d);
      ConformanceRuntimeSupport.setFloatingField(target, "halfIeeeRef", 3.0d);
      ConformanceRuntimeSupport.setFloatingField(target, "doubleScaledRef", 5.0d);
      ConformanceRuntimeSupport.setFloatingField(target, "i32ScaledRef", -10.0d);

      Object childA = child(classLoader, 7);
      Object childB = child(classLoader, 8);
      Object childC = child(classLoader, 9);
      Object childD = child(classLoader, 10);
      ConformanceRuntimeSupport.setObjectArrayField(
          target, "childArray", new Object[] {childA, childB});
      ConformanceRuntimeSupport.setNumericArrayField(target, "floatArray", new long[] {1, 2});
      ConformanceRuntimeSupport.setNumericArrayField(target, "scaledArray", new long[] {3, 4});
      ConformanceRuntimeSupport.setObjectArrayField(
          target, "childVector", new Object[] {childC, childD});
      ConformanceRuntimeSupport.setNumericArrayField(target, "floatVector", new long[] {5, 6});
      ConformanceRuntimeSupport.setNumericArrayField(target, "scaledVector", new long[] {-1, 2});
      ConformanceRuntimeSupport.setNumericArrayField(target, "u16Terminated", new long[] {11, 12});
      ConformanceRuntimeSupport.setNumericArrayField(target, "u32Terminated", new long[] {13, 14});
      ConformanceRuntimeSupport.setNumericArrayField(target, "u64Terminated", new long[] {15, 16});
      ConformanceRuntimeSupport.setNumericArrayField(target, "i16Terminated", new long[] {-2, 3});
      ConformanceRuntimeSupport.setNumericArrayField(target, "i32Terminated", new long[] {-4, 5});
      ConformanceRuntimeSupport.setNumericArrayField(target, "i64Terminated", new long[] {-6, 7});
      ConformanceRuntimeSupport.setNumericArrayField(target, "i8Terminated", new long[] {-2, 3});
      ConformanceRuntimeSupport.setByteArrayField(target, "countedBlob", new byte[] {21, 22});
      ConformanceRuntimeSupport.setByteArrayField(target, "hexBlob", new byte[] {31, 32});
    };
  }

  /**
   * Returns Java decoded-value assertions for the coverage fixture.
   *
   * @return Java assertion callback
   */
  private static ConformanceCase.JavaAsserter coverageJavaAsserter() {
    return (decoded, unusedClassLoader) -> {
      ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "count64", 2);
      ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "status16", 3);
      ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "status32", 257);
      ConformanceRuntimeSupport.assertNumericFieldEquals(decoded, "status64", 17);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(decoded, "halfIeeeInline", 1.5d, 0.02d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(
          decoded, "doubleIeeeInline", 2.5d, 0.0001d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(
          decoded, "halfScaledInline", 12.5d, 0.01d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(
          decoded, "doubleScaledInline", 15.0d, 0.0001d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(
          decoded, "i8ScaledInline", -4.0d, 0.0001d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(decoded, "u8ScaledInline", 6.0d, 0.0001d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(
          decoded, "i32ScaledInline", 7.0d, 0.0001d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(
          decoded, "u32ScaledInline", 8.0d, 0.0001d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(
          decoded, "i64ScaledInline", 9.0d, 0.0001d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(decoded, "halfIeeeRef", 3.0d, 0.02d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(
          decoded, "doubleScaledRef", 5.0d, 0.0001d);
      ConformanceRuntimeSupport.assertFloatingFieldEquals(decoded, "i32ScaledRef", -10.0d, 0.0001d);
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "floatArray", new long[] {1, 2});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "scaledArray", new long[] {3, 4});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "floatVector", new long[] {5, 6});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "scaledVector", new long[] {-1, 2});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "u16Terminated", new long[] {11, 12});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "u32Terminated", new long[] {13, 14});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "u64Terminated", new long[] {15, 16});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "i16Terminated", new long[] {-2, 3});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "i32Terminated", new long[] {-4, 5});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "i64Terminated", new long[] {-6, 7});
      ConformanceRuntimeSupport.assertNumericArrayFieldEquals(
          decoded, "i8Terminated", new long[] {-2, 3});
      ConformanceRuntimeSupport.assertByteArrayFieldEquals(
          decoded, "countedBlob", new byte[] {21, 22});
      ConformanceRuntimeSupport.assertByteArrayFieldEquals(decoded, "hexBlob", new byte[] {31, 32});
    };
  }

  /**
   * Returns C++ source setup snippet for the coverage fixture.
   *
   * @return C++ setup snippet
   */
  private static String coverageCppSourceSetupSnippet() {
    return """
        source.count64 = static_cast<std::uint64_t>(2);
        source.status16 = static_cast<std::uint16_t>(3);
        source.status32 = static_cast<std::uint32_t>(257);
        source.status64 = static_cast<std::uint64_t>(17);
        source.halfIeeeInline = 1.5;
        source.doubleIeeeInline = 2.5;
        source.halfScaledInline = 12.5;
        source.doubleScaledInline = 15.0;
        source.i8ScaledInline = -4.0;
        source.u8ScaledInline = 6.0;
        source.i32ScaledInline = 7.0;
        source.u32ScaledInline = 8.0;
        source.i64ScaledInline = 9.0;
        source.halfIeeeRef = 3.0;
        source.doubleScaledRef = 5.0;
        source.i32ScaledRef = -10.0;
        ::acme::telemetry::coverage::shared::Child childA{};
        childA.value = static_cast<std::uint8_t>(7);
        ::acme::telemetry::coverage::shared::Child childB{};
        childB.value = static_cast<std::uint8_t>(8);
        ::acme::telemetry::coverage::shared::Child childC{};
        childC.value = static_cast<std::uint8_t>(9);
        ::acme::telemetry::coverage::shared::Child childD{};
        childD.value = static_cast<std::uint8_t>(10);
        source.childArray = std::array<::acme::telemetry::coverage::shared::Child, 2>{childA, childB};
        source.floatArray = std::array<double, 2>{1.0, 2.0};
        source.scaledArray = std::array<double, 2>{3.0, 4.0};
        source.childVector =
            std::vector<::acme::telemetry::coverage::shared::Child>{childC, childD};
        source.floatVector = std::vector<double>{5.0, 6.0};
        source.scaledVector = std::vector<double>{-1.0, 2.0};
        source.u16Terminated = std::vector<std::uint16_t>{11U, 12U};
        source.u32Terminated = std::vector<std::uint32_t>{13U, 14U};
        source.u64Terminated = std::vector<std::uint64_t>{15U, 16U};
        source.i16Terminated = std::vector<std::int16_t>{-2, 3};
        source.i32Terminated = std::vector<std::int32_t>{-4, 5};
        source.i64Terminated = std::vector<std::int64_t>{-6, 7};
        source.i8Terminated = std::vector<std::int8_t>{-2, 3};
        source.countedBlob = std::vector<std::uint8_t>{21U, 22U};
        source.hexBlob = std::vector<std::uint8_t>{31U, 32U};
        """;
  }

  /**
   * Returns C++ decoded-value assertion snippet for the coverage fixture.
   *
   * @return C++ assertion snippet
   */
  private static String coverageCppDecodedAssertionSnippet() {
    return """
        if (decoded.count64 != static_cast<std::uint64_t>(2)) {
          return 111;
        }
        if (decoded.status16 != static_cast<std::uint16_t>(3)) {
          return 112;
        }
        if (decoded.status32 != static_cast<std::uint32_t>(257)) {
          return 113;
        }
        if (decoded.status64 != static_cast<std::uint64_t>(17)) {
          return 114;
        }
        if (decoded.childArray.at(0).value != static_cast<std::uint8_t>(7)) {
          return 115;
        }
        if (decoded.childArray.at(1).value != static_cast<std::uint8_t>(8)) {
          return 116;
        }
        if (decoded.childVector.size() != 2U) {
          return 117;
        }
        if (decoded.childVector.at(0).value != static_cast<std::uint8_t>(9)) {
          return 118;
        }
        if (decoded.childVector.at(1).value != static_cast<std::uint8_t>(10)) {
          return 119;
        }
        if (decoded.floatArray != std::array<double, 2>{1.0, 2.0}) {
          return 120;
        }
        if (decoded.scaledArray != std::array<double, 2>{3.0, 4.0}) {
          return 121;
        }
        if (decoded.floatVector != std::vector<double>{5.0, 6.0}) {
          return 122;
        }
        if (decoded.scaledVector != std::vector<double>{-1.0, 2.0}) {
          return 123;
        }
        if (decoded.u16Terminated != std::vector<std::uint16_t>{11U, 12U}) {
          return 124;
        }
        if (decoded.u32Terminated != std::vector<std::uint32_t>{13U, 14U}) {
          return 125;
        }
        if (decoded.u64Terminated != std::vector<std::uint64_t>{15U, 16U}) {
          return 126;
        }
        if (decoded.i16Terminated != std::vector<std::int16_t>{-2, 3}) {
          return 127;
        }
        if (decoded.i32Terminated != std::vector<std::int32_t>{-4, 5}) {
          return 128;
        }
        if (decoded.i64Terminated != std::vector<std::int64_t>{-6, 7}) {
          return 129;
        }
        if (decoded.i8Terminated != std::vector<std::int8_t>{-2, 3}) {
          return 130;
        }
        if (decoded.countedBlob != std::vector<std::uint8_t>{21U, 22U}) {
          return 131;
        }
        if (decoded.hexBlob != std::vector<std::uint8_t>{31U, 32U}) {
          return 132;
        }
        """;
  }

  /**
   * Builds one child message for the coverage case.
   *
   * @param classLoader generated-class class loader
   * @param value unsigned byte value to assign
   * @return configured child instance
   * @throws Exception when reflection setup fails
   */
  private static Object child(ClassLoader classLoader, int value) throws Exception {
    Object child =
        ConformanceRuntimeSupport.newInstance(classLoader, "acme.telemetry.coverage.shared.Child");
    ConformanceRuntimeSupport.setNumericField(child, "value", value);
    return child;
  }
}
