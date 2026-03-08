package io.github.sportne.bms.codegen.cpp;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBitFlag;
import io.github.sportne.bms.model.resolved.ResolvedBitSegment;
import io.github.sportne.bms.model.resolved.ResolvedBitVariant;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorMatch;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorNode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates deterministic C++ source from the resolved model.
 *
 * <p>This milestone supports foundation, numeric, and collection members. Conditional and nested
 * members remain explicit unsupported diagnostics in C++ until milestone 04.
 */
public final class CppCodeGenerator {
  private static final String SHARED_CPP_HELPERS =
      """
      namespace {

      void requireReadable(
          std::span<const std::uint8_t> data,
          std::size_t cursor,
          std::size_t requiredBytes,
          const char* label) {
        if (cursor + requiredBytes > data.size()) {
          throw std::invalid_argument(
              std::string("Not enough bytes while decoding ") + label + '.');
        }
      }

      template <typename T>
      void writeIntegral(std::vector<std::uint8_t>& out, T value, bool littleEndian) {
        using Unsigned = std::make_unsigned_t<T>;
        Unsigned raw = static_cast<Unsigned>(value);
        constexpr std::size_t kBytes = sizeof(T);
        for (std::size_t index = 0; index < kBytes; index++) {
          std::size_t shiftIndex = littleEndian ? index : (kBytes - 1U - index);
          out.push_back(static_cast<std::uint8_t>((raw >> (shiftIndex * 8U)) & 0xFFU));
        }
      }

      template <typename T>
      T readIntegral(
          std::span<const std::uint8_t> data,
          std::size_t& cursor,
          bool littleEndian,
          const char* label) {
        using Unsigned = std::make_unsigned_t<T>;
        constexpr std::size_t kBytes = sizeof(T);
        requireReadable(data, cursor, kBytes, label);

        Unsigned raw = 0;
        for (std::size_t index = 0; index < kBytes; index++) {
          std::size_t shiftIndex = littleEndian ? index : (kBytes - 1U - index);
          raw |= static_cast<Unsigned>(data[cursor + index]) << (shiftIndex * 8U);
        }
        cursor += kBytes;
        return static_cast<T>(raw);
      }

      std::uint16_t floatToHalfBits(float value) {
        std::uint32_t bits = 0;
        std::memcpy(&bits, &value, sizeof(bits));
        std::uint32_t sign = (bits >> 31U) & 0x1U;
        std::int32_t exponent = static_cast<std::int32_t>((bits >> 23U) & 0xFFU) - 127 + 15;
        std::uint32_t mantissa = bits & 0x7FFFFFU;

        if (exponent <= 0) {
          if (exponent < -10) {
            return static_cast<std::uint16_t>(sign << 15U);
          }
          mantissa |= 0x800000U;
          std::uint32_t shifted = mantissa >> static_cast<std::uint32_t>(1 - exponent + 13);
          if ((mantissa >> static_cast<std::uint32_t>(1 - exponent + 12)) & 0x1U) {
            shifted += 1U;
          }
          return static_cast<std::uint16_t>((sign << 15U) | shifted);
        }
        if (exponent >= 31) {
          return static_cast<std::uint16_t>((sign << 15U) | 0x7C00U);
        }

        std::uint16_t half =
            static_cast<std::uint16_t>((sign << 15U) | (static_cast<std::uint32_t>(exponent) << 10U));
        half = static_cast<std::uint16_t>(half | static_cast<std::uint16_t>(mantissa >> 13U));
        if (mantissa & 0x1000U) {
          half = static_cast<std::uint16_t>(half + 1U);
        }
        return half;
      }

      float halfBitsToFloat(std::uint16_t bits) {
        std::uint32_t sign = (static_cast<std::uint32_t>(bits & 0x8000U)) << 16U;
        std::uint32_t exponent = (bits >> 10U) & 0x1FU;
        std::uint32_t mantissa = bits & 0x03FFU;
        std::uint32_t value = 0;

        if (exponent == 0) {
          if (mantissa == 0) {
            value = sign;
          } else {
            exponent = 1;
            while ((mantissa & 0x0400U) == 0U) {
              mantissa <<= 1U;
              exponent--;
            }
            mantissa &= 0x03FFU;
            value = sign | ((exponent + 127U - 15U) << 23U) | (mantissa << 13U);
          }
        } else if (exponent == 0x1FU) {
          value = sign | 0x7F800000U | (mantissa << 13U);
        } else {
          value = sign | ((exponent + 127U - 15U) << 23U) | (mantissa << 13U);
        }

        float result = 0.0F;
        std::memcpy(&result, &value, sizeof(result));
        return result;
      }

      void writeFloat16(std::vector<std::uint8_t>& out, float value, bool littleEndian) {
        writeIntegral<std::uint16_t>(out, floatToHalfBits(value), littleEndian);
      }

      float readFloat16(
          std::span<const std::uint8_t> data,
          std::size_t& cursor,
          bool littleEndian,
          const char* label) {
        return halfBitsToFloat(readIntegral<std::uint16_t>(data, cursor, littleEndian, label));
      }

      void writeFloat32(std::vector<std::uint8_t>& out, float value, bool littleEndian) {
        std::uint32_t bits = 0;
        std::memcpy(&bits, &value, sizeof(bits));
        writeIntegral<std::uint32_t>(out, bits, littleEndian);
      }

      float readFloat32(
          std::span<const std::uint8_t> data,
          std::size_t& cursor,
          bool littleEndian,
          const char* label) {
        std::uint32_t bits = readIntegral<std::uint32_t>(data, cursor, littleEndian, label);
        float value = 0.0F;
        std::memcpy(&value, &bits, sizeof(value));
        return value;
      }

      void writeFloat64(std::vector<std::uint8_t>& out, double value, bool littleEndian) {
        std::uint64_t bits = 0;
        std::memcpy(&bits, &value, sizeof(bits));
        writeIntegral<std::uint64_t>(out, bits, littleEndian);
      }

      double readFloat64(
          std::span<const std::uint8_t> data,
          std::size_t& cursor,
          bool littleEndian,
          const char* label) {
        std::uint64_t bits = readIntegral<std::uint64_t>(data, cursor, littleEndian, label);
        double value = 0.0;
        std::memcpy(&value, &bits, sizeof(value));
        return value;
      }

      template <typename T>
      T scaleToSignedRaw(double logicalValue, double scale, const char* fieldName) {
        if (scale == 0.0) {
          throw std::invalid_argument(std::string(fieldName) + ": scale must not be zero.");
        }
        double rounded = std::round(logicalValue / scale);
        if (rounded < static_cast<double>(std::numeric_limits<T>::lowest())
            || rounded > static_cast<double>(std::numeric_limits<T>::max())) {
          throw std::invalid_argument(
              std::string(fieldName) + ": scaled value is out of range for its integer base type.");
        }
        return static_cast<T>(rounded);
      }

      template <typename T>
      T scaleToUnsignedRaw(double logicalValue, double scale, const char* fieldName) {
        if (scale == 0.0) {
          throw std::invalid_argument(std::string(fieldName) + ": scale must not be zero.");
        }
        double rounded = std::round(logicalValue / scale);
        if (rounded < 0.0 || rounded > static_cast<double>(std::numeric_limits<T>::max())) {
          throw std::invalid_argument(
              std::string(fieldName) + ": scaled value is out of range for its integer base type.");
        }
        return static_cast<T>(rounded);
      }

      template <typename T>
      std::size_t requireCount(T value, const char* fieldName) {
        if constexpr (std::is_signed_v<T>) {
          if (value < 0) {
            throw std::invalid_argument(
                std::string("Count field ") + fieldName + " must not be negative.");
          }
        }
        using Unsigned = std::make_unsigned_t<T>;
        Unsigned count = static_cast<Unsigned>(value);
        if (count > static_cast<Unsigned>(std::numeric_limits<std::size_t>::max())) {
          throw std::invalid_argument(
              std::string("Count field ") + fieldName + " is too large for this platform.");
        }
        return static_cast<std::size_t>(count);
      }

      }  // namespace
      """;

  /**
   * Writes generated C++ header/source files into the provided output directory.
   *
   * @param schema resolved schema to generate from
   * @param outputDirectory target directory for generated C++ files
   * @throws BmsException if generation fails
   */
  public void generate(ResolvedSchema schema, Path outputDirectory) throws BmsException {
    GenerationContext generationContext = buildContext(schema);

    for (ResolvedMessageType messageType : schema.messageTypes()) {
      Path namespaceDirectory =
          outputDirectory.resolve(messageType.effectiveNamespace().replace('.', '/'));
      Path headerPath = namespaceDirectory.resolve(messageType.name() + ".hpp");
      Path sourcePath = namespaceDirectory.resolve(messageType.name() + ".cpp");
      ensureSupportedMembers(messageType, generationContext, sourcePath);

      String headerSource = renderHeader(messageType, generationContext);
      String cppSource = renderSource(messageType, generationContext);

      try {
        Files.createDirectories(namespaceDirectory);
        Files.writeString(headerPath, headerSource, StandardCharsets.UTF_8);
        Files.writeString(sourcePath, cppSource, StandardCharsets.UTF_8);
      } catch (IOException exception) {
        Diagnostic diagnostic =
            new Diagnostic(
                DiagnosticSeverity.ERROR,
                "GENERATOR_CPP_IO_ERROR",
                "Failed to write C++ source: " + exception.getMessage(),
                sourcePath.toString(),
                -1,
                -1);
        throw new BmsException("C++ code generation failed.", List.of(diagnostic));
      }
    }
  }

  /**
   * Builds reusable lookup maps used while validating and generating C++ output.
   *
   * @param schema resolved schema being generated
   * @return immutable generation context
   */
  private static GenerationContext buildContext(ResolvedSchema schema) {
    return new GenerationContext(
        indexMessageTypes(schema),
        mapFloats(schema.reusableFloats()),
        mapScaledInts(schema.reusableScaledInts()),
        mapArrays(schema.reusableArrays()),
        mapVectors(schema.reusableVectors()),
        mapBlobArrays(schema.reusableBlobArrays()),
        mapBlobVectors(schema.reusableBlobVectors()));
  }

  /**
   * Verifies that this generator supports every member and type in the message.
   *
   * @param messageType message being generated
   * @param generationContext reusable lookup maps
   * @param sourcePath output source path used in diagnostics
   * @throws BmsException if unsupported members or type references are found
   */
  private static void ensureSupportedMembers(
      ResolvedMessageType messageType, GenerationContext generationContext, Path sourcePath)
      throws BmsException {
    List<Diagnostic> diagnostics = new ArrayList<>();
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    for (ResolvedMessageMember member : messageType.members()) {
      checkMemberSupport(
          member,
          messageType,
          generationContext,
          primitiveFieldByName,
          sourcePath.toString(),
          diagnostics);
    }

    if (!diagnostics.isEmpty()) {
      throw new BmsException("C++ code generation failed due to unsupported members.", diagnostics);
    }
  }

  /**
   * Builds a lookup map from primitive field name to primitive type for one message.
   *
   * @param messageType message being generated
   * @return immutable primitive field lookup map
   */
  private static Map<String, PrimitiveType> primitiveFieldsByName(ResolvedMessageType messageType) {
    Map<String, PrimitiveType> primitiveFieldByName = new LinkedHashMap<>();
    for (ResolvedMessageMember member : messageType.members()) {
      if (member instanceof ResolvedField resolvedField
          && resolvedField.typeRef() instanceof PrimitiveTypeRef primitiveTypeRef) {
        primitiveFieldByName.putIfAbsent(resolvedField.name(), primitiveTypeRef.primitiveType());
      }
    }
    return Map.copyOf(primitiveFieldByName);
  }

  /**
   * Checks support for one message member.
   *
   * @param member member to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkMemberSupport(
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (member instanceof ResolvedField resolvedField) {
      checkFieldTypeSupport(
          resolvedField,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (member instanceof ResolvedBitField) {
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      checkFloatSupport(resolvedFloat, messageType, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedScaledInt) {
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      checkArraySupport(resolvedArray, messageType, generationContext, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      checkVectorSupport(
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (member instanceof ResolvedBlobArray) {
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      checkBlobVectorSupport(
          resolvedBlobVector, messageType, primitiveFieldByName, outputPath, diagnostics);
      return;
    }

    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(), member.getClass().getSimpleName(), outputPath));
  }

  /**
   * Checks support for one field member and its referenced type.
   *
   * @param resolvedField field to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkFieldTypeSupport(
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    if (typeRef instanceof PrimitiveTypeRef || typeRef instanceof MessageTypeRef) {
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      if (resolvedFloat == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath,
                "Reusable float was not found: " + floatTypeRef.floatTypeName()));
        return;
      }
      checkFloatSupport(resolvedFloat, messageType, outputPath, diagnostics);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      if (!generationContext
          .reusableScaledIntByName()
          .containsKey(scaledIntTypeRef.scaledIntTypeName())) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath,
                "Reusable scaledInt was not found: " + scaledIntTypeRef.scaledIntTypeName()));
      }
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath,
                "Reusable array was not found: " + arrayTypeRef.arrayTypeName()));
        return;
      }
      checkArraySupport(resolvedArray, messageType, generationContext, outputPath, diagnostics);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath,
                "Reusable vector was not found: " + vectorTypeRef.vectorTypeName()));
        return;
      }
      checkVectorSupport(
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      if (!generationContext
          .reusableBlobArrayByName()
          .containsKey(blobArrayTypeRef.blobArrayTypeName())) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath,
                "Reusable blobArray was not found: " + blobArrayTypeRef.blobArrayTypeName()));
      }
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      if (resolvedBlobVector == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath,
                "Reusable blobVector was not found: " + blobVectorTypeRef.blobVectorTypeName()));
      } else {
        checkBlobVectorSupport(
            resolvedBlobVector, messageType, primitiveFieldByName, outputPath, diagnostics);
      }
      return;
    }

    diagnostics.add(
        unsupportedTypeRefDiagnostic(
            messageType.name(),
            resolvedField.name(),
            typeRef.getClass().getSimpleName(),
            outputPath,
            "This type reference is not implemented in the C++ backend yet."));
  }

  /**
   * Checks support for one float definition.
   *
   * @param resolvedFloat float definition to validate
   * @param messageType parent message type
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkFloatSupport(
      ResolvedFloat resolvedFloat,
      ResolvedMessageType messageType,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      if (resolvedFloat.size() == FloatSize.F16
          || resolvedFloat.size() == FloatSize.F32
          || resolvedFloat.size() == FloatSize.F64) {
        return;
      }
    }
    if (resolvedFloat.encoding() == FloatEncoding.SCALED) {
      if (resolvedFloat.size() == FloatSize.F16
          || resolvedFloat.size() == FloatSize.F32
          || resolvedFloat.size() == FloatSize.F64) {
        return;
      }
    }
    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            "ResolvedFloat(" + resolvedFloat.encoding() + ", " + resolvedFloat.size() + ")",
            outputPath));
  }

  /**
   * Checks support for one array definition.
   *
   * @param resolvedArray array definition to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkArraySupport(
      ResolvedArray resolvedArray,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (isCollectionElementTypeSupported(resolvedArray.elementTypeRef(), generationContext)) {
      return;
    }
    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            "ResolvedArray(elementType="
                + resolvedArray.elementTypeRef().getClass().getSimpleName()
                + ")",
            outputPath));
  }

  /**
   * Checks support for one vector definition.
   *
   * @param resolvedVector vector definition to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkVectorSupport(
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!isCollectionElementTypeSupported(resolvedVector.elementTypeRef(), generationContext)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "ResolvedVector(elementType="
                  + resolvedVector.elementTypeRef().getClass().getSimpleName()
                  + ")",
              outputPath));
      return;
    }
    checkLengthModeSupport(
        resolvedVector.lengthMode(),
        resolvedVector.elementTypeRef(),
        messageType,
        primitiveFieldByName,
        outputPath,
        diagnostics,
        "vector " + resolvedVector.name());
  }

  /**
   * Checks support for one blob-vector definition.
   *
   * @param resolvedBlobVector blob-vector definition to validate
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkBlobVectorSupport(
      ResolvedBlobVector resolvedBlobVector,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    checkLengthModeSupport(
        resolvedBlobVector.lengthMode(),
        new PrimitiveTypeRef(PrimitiveType.UINT8),
        messageType,
        primitiveFieldByName,
        outputPath,
        diagnostics,
        "blobVector " + resolvedBlobVector.name());
  }

  /**
   * Checks support for one vector/blob-vector length mode.
   *
   * @param lengthMode resolved length mode
   * @param elementTypeRef element type reference
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerName member name used in diagnostics
   */
  private static void checkLengthModeSupport(
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics,
      String ownerName) {
    if (lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      if (!primitiveFieldByName.containsKey(resolvedCountFieldLength.ref())) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                ownerName + "(countField ref=\"" + resolvedCountFieldLength.ref() + "\")",
                outputPath));
      }
      return;
    }

    String literal = terminatorLiteral(lengthMode);
    if (literal == null) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminatorField path missing terminatorMatch)",
              outputPath));
      return;
    }
    validatePrimitiveTerminatorLiteral(
        elementTypeRef, literal, messageType, outputPath, diagnostics, ownerName);
  }

  /**
   * Checks that one terminator literal is valid for a primitive element type.
   *
   * @param elementTypeRef element type of vector/blob vector
   * @param literal terminator literal text
   * @param messageType parent message type
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerName member name used in diagnostics
   */
  private static void validatePrimitiveTerminatorLiteral(
      ResolvedTypeRef elementTypeRef,
      String literal,
      ResolvedMessageType messageType,
      String outputPath,
      List<Diagnostic> diagnostics,
      String ownerName) {
    if (!(elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminator modes require primitive element types)",
              outputPath));
      return;
    }
    try {
      BigInteger value = parseNumericLiteral(literal);
      if (!fitsPrimitiveRange(value, primitiveTypeRef.primitiveType())) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                ownerName + "(terminator literal out of range: " + literal + ")",
                outputPath));
      }
    } catch (NumberFormatException exception) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(invalid terminator literal: " + literal + ")",
              outputPath));
    }
  }

  /**
   * Returns whether one collection element type is supported in C++ milestone 03.
   *
   * @param elementTypeRef element type reference to inspect
   * @param generationContext reusable lookup maps
   * @return {@code true} when supported
   */
  private static boolean isCollectionElementTypeSupported(
      ResolvedTypeRef elementTypeRef, GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef || elementTypeRef instanceof MessageTypeRef) {
      return true;
    }
    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      return generationContext.reusableFloatByName().containsKey(floatTypeRef.floatTypeName());
    }
    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      return generationContext
          .reusableScaledIntByName()
          .containsKey(scaledIntTypeRef.scaledIntTypeName());
    }
    return false;
  }

  /**
   * Builds a stable lookup map from message type name to resolved message object.
   *
   * @param schema resolved schema that contains message types
   * @return immutable map keyed by message type name
   */
  private static Map<String, ResolvedMessageType> indexMessageTypes(ResolvedSchema schema) {
    Map<String, ResolvedMessageType> messageTypeByName = new LinkedHashMap<>();
    for (ResolvedMessageType messageType : schema.messageTypes()) {
      messageTypeByName.put(messageType.name(), messageType);
    }
    return Map.copyOf(messageTypeByName);
  }

  /**
   * Builds a stable lookup map from float type name to resolved float definition.
   *
   * @param reusableFloats reusable float definitions from schema scope
   * @return immutable float lookup map
   */
  private static Map<String, ResolvedFloat> mapFloats(List<ResolvedFloat> reusableFloats) {
    Map<String, ResolvedFloat> reusableFloatByName = new LinkedHashMap<>();
    for (ResolvedFloat resolvedFloat : reusableFloats) {
      reusableFloatByName.put(resolvedFloat.name(), resolvedFloat);
    }
    return Map.copyOf(reusableFloatByName);
  }

  /**
   * Builds a stable lookup map from scaled-int type name to definition.
   *
   * @param reusableScaledInts reusable scaled-int definitions from schema scope
   * @return immutable scaled-int lookup map
   */
  private static Map<String, ResolvedScaledInt> mapScaledInts(
      List<ResolvedScaledInt> reusableScaledInts) {
    Map<String, ResolvedScaledInt> reusableScaledIntByName = new LinkedHashMap<>();
    for (ResolvedScaledInt resolvedScaledInt : reusableScaledInts) {
      reusableScaledIntByName.put(resolvedScaledInt.name(), resolvedScaledInt);
    }
    return Map.copyOf(reusableScaledIntByName);
  }

  /**
   * Builds a stable lookup map from array type name to definition.
   *
   * @param reusableArrays reusable array definitions from schema scope
   * @return immutable array lookup map
   */
  private static Map<String, ResolvedArray> mapArrays(List<ResolvedArray> reusableArrays) {
    Map<String, ResolvedArray> reusableArrayByName = new LinkedHashMap<>();
    for (ResolvedArray resolvedArray : reusableArrays) {
      reusableArrayByName.put(resolvedArray.name(), resolvedArray);
    }
    return Map.copyOf(reusableArrayByName);
  }

  /**
   * Builds a stable lookup map from vector type name to definition.
   *
   * @param reusableVectors reusable vector definitions from schema scope
   * @return immutable vector lookup map
   */
  private static Map<String, ResolvedVector> mapVectors(List<ResolvedVector> reusableVectors) {
    Map<String, ResolvedVector> reusableVectorByName = new LinkedHashMap<>();
    for (ResolvedVector resolvedVector : reusableVectors) {
      reusableVectorByName.put(resolvedVector.name(), resolvedVector);
    }
    return Map.copyOf(reusableVectorByName);
  }

  /**
   * Builds a stable lookup map from blob-array type name to definition.
   *
   * @param reusableBlobArrays reusable blob-array definitions from schema scope
   * @return immutable blob-array lookup map
   */
  private static Map<String, ResolvedBlobArray> mapBlobArrays(
      List<ResolvedBlobArray> reusableBlobArrays) {
    Map<String, ResolvedBlobArray> reusableBlobArrayByName = new LinkedHashMap<>();
    for (ResolvedBlobArray resolvedBlobArray : reusableBlobArrays) {
      reusableBlobArrayByName.put(resolvedBlobArray.name(), resolvedBlobArray);
    }
    return Map.copyOf(reusableBlobArrayByName);
  }

  /**
   * Builds a stable lookup map from blob-vector type name to definition.
   *
   * @param reusableBlobVectors reusable blob-vector definitions from schema scope
   * @return immutable blob-vector lookup map
   */
  private static Map<String, ResolvedBlobVector> mapBlobVectors(
      List<ResolvedBlobVector> reusableBlobVectors) {
    Map<String, ResolvedBlobVector> reusableBlobVectorByName = new LinkedHashMap<>();
    for (ResolvedBlobVector resolvedBlobVector : reusableBlobVectors) {
      reusableBlobVectorByName.put(resolvedBlobVector.name(), resolvedBlobVector);
    }
    return Map.copyOf(reusableBlobVectorByName);
  }

  /**
   * Renders one C++ header file for a resolved message.
   *
   * @param messageType message type to render
   * @param generationContext reusable lookup maps
   * @return generated C++ header source
   */
  private static String renderHeader(
      ResolvedMessageType messageType, GenerationContext generationContext) {
    StringBuilder builder = new StringBuilder();
    builder.append("#pragma once\n\n");
    builder.append("#include <array>\n");
    builder.append("#include <cstddef>\n");
    builder.append("#include <cstdint>\n");
    builder.append("#include <span>\n");
    builder.append("#include <vector>\n");

    TreeSet<String> includePaths = new TreeSet<>();
    for (ResolvedMessageMember member : messageType.members()) {
      collectMessageIncludesForMember(
          member, messageType.effectiveNamespace(), generationContext, includePaths);
    }
    for (String includePath : includePaths) {
      if (!includePath.equals(headerIncludePath(messageType))) {
        builder.append("#include \"").append(includePath).append("\"\n");
      }
    }

    builder.append('\n');
    appendNamespaceOpen(builder, messageType.effectiveNamespace());
    builder.append("struct ").append(messageType.name()).append(" {\n");
    appendMemberDeclarations(builder, messageType, generationContext);
    builder.append("\n");
    appendBitFieldHelperDeclarations(builder, messageType);
    builder
        .append("  /**\n")
        .append("   * Encodes this message instance into wire bytes.\n")
        .append("   *\n")
        .append("   * @return encoded bytes in deterministic field order\n")
        .append("   */\n")
        .append("  std::vector<std::uint8_t> encode() const;\n\n")
        .append("  /**\n")
        .append("   * Decodes one full message from wire bytes.\n")
        .append("   *\n")
        .append("   * @param data encoded message bytes\n")
        .append("   * @return decoded message value\n")
        .append("   */\n")
        .append("  static ")
        .append(messageType.name())
        .append(" decode(std::span<const std::uint8_t> data);\n\n")
        .append("  /**\n")
        .append("   * Decodes one message from the current cursor position.\n")
        .append("   *\n")
        .append("   * @param data encoded message bytes\n")
        .append("   * @param cursor current decode cursor; advanced past this message\n")
        .append("   * @return decoded message value\n")
        .append("   */\n")
        .append("  static ")
        .append(messageType.name())
        .append(" decodeFrom(std::span<const std::uint8_t> data, std::size_t& cursor);\n");
    builder.append("};\n\n");
    appendNamespaceClose(builder, messageType.effectiveNamespace());
    return builder.toString();
  }

  /**
   * Collects cross-message includes needed for one resolved member.
   *
   * @param member member to inspect
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @param includePaths destination set of include paths
   */
  private static void collectMessageIncludesForMember(
      ResolvedMessageMember member,
      String currentNamespace,
      GenerationContext generationContext,
      Set<String> includePaths) {
    if (member instanceof ResolvedField resolvedField) {
      collectMessageIncludesForTypeRef(
          resolvedField.typeRef(), currentNamespace, generationContext, includePaths);
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      collectMessageIncludesForTypeRef(
          resolvedArray.elementTypeRef(), currentNamespace, generationContext, includePaths);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      collectMessageIncludesForTypeRef(
          resolvedVector.elementTypeRef(), currentNamespace, generationContext, includePaths);
    }
  }

  /**
   * Collects cross-message includes needed for one resolved type reference.
   *
   * @param typeRef type reference to inspect
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @param includePaths destination set of include paths
   */
  private static void collectMessageIncludesForTypeRef(
      ResolvedTypeRef typeRef,
      String currentNamespace,
      GenerationContext generationContext,
      Set<String> includePaths) {
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      ResolvedMessageType referenced =
          generationContext.messageTypeByName().get(messageTypeRef.messageTypeName());
      if (referenced != null && !referenced.effectiveNamespace().equals(currentNamespace)) {
        includePaths.add(headerIncludePath(referenced));
      }
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray != null) {
        collectMessageIncludesForTypeRef(
            resolvedArray.elementTypeRef(), currentNamespace, generationContext, includePaths);
      }
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector != null) {
        collectMessageIncludesForTypeRef(
            resolvedVector.elementTypeRef(), currentNamespace, generationContext, includePaths);
      }
    }
  }

  /**
   * Appends field declarations for all members that materialize as data fields.
   *
   * @param builder destination header builder
   * @param messageType message type being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendMemberDeclarations(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    for (ResolvedMessageMember member : messageType.members()) {
      String cppType = memberCppType(member, messageType.effectiveNamespace(), generationContext);
      if (cppType == null) {
        continue;
      }
      builder.append("  ").append(cppType).append(' ').append(memberName(member)).append("{};\n");
    }
  }

  /**
   * Returns the C++ field type for one resolved message member.
   *
   * @param member member whose field type is needed
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @return C++ field type, or {@code null} when member has no field declaration
   */
  private static String memberCppType(
      ResolvedMessageMember member, String currentNamespace, GenerationContext generationContext) {
    if (member instanceof ResolvedField resolvedField) {
      return toCppTypeForTypeRef(
          resolvedField.typeRef(), currentNamespace, generationContext, resolvedField.name());
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      return bitFieldStoragePrimitive(resolvedBitField.size()).cppTypeName();
    }
    if (member instanceof ResolvedFloat || member instanceof ResolvedScaledInt) {
      return "double";
    }
    if (member instanceof ResolvedArray resolvedArray) {
      return "std::array<"
          + collectionElementCppType(
              resolvedArray.elementTypeRef(),
              currentNamespace,
              generationContext,
              resolvedArray.name())
          + ", "
          + resolvedArray.length()
          + ">";
    }
    if (member instanceof ResolvedVector resolvedVector) {
      return "std::vector<"
          + collectionElementCppType(
              resolvedVector.elementTypeRef(),
              currentNamespace,
              generationContext,
              resolvedVector.name())
          + ">";
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      return "std::array<std::uint8_t, " + resolvedBlobArray.length() + ">";
    }
    if (member instanceof ResolvedBlobVector) {
      return "std::vector<std::uint8_t>";
    }
    return null;
  }

  /**
   * Resolves one field type reference into its generated C++ field type.
   *
   * @param typeRef resolved type reference
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @param fieldName field name used in fallback exceptions
   * @return C++ field type
   */
  private static String toCppTypeForTypeRef(
      ResolvedTypeRef typeRef,
      String currentNamespace,
      GenerationContext generationContext,
      String fieldName) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().cppTypeName();
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      return cppMessageTypeName(
          messageTypeRef.messageTypeName(), currentNamespace, generationContext);
    }
    if (typeRef instanceof FloatTypeRef || typeRef instanceof ScaledIntTypeRef) {
      return "double";
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray == null) {
        throw new IllegalStateException("Missing reusable array for " + fieldName + '.');
      }
      return "std::array<"
          + collectionElementCppType(
              resolvedArray.elementTypeRef(), currentNamespace, generationContext, fieldName)
          + ", "
          + resolvedArray.length()
          + ">";
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector == null) {
        throw new IllegalStateException("Missing reusable vector for " + fieldName + '.');
      }
      return "std::vector<"
          + collectionElementCppType(
              resolvedVector.elementTypeRef(), currentNamespace, generationContext, fieldName)
          + ">";
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      ResolvedBlobArray resolvedBlobArray =
          generationContext.reusableBlobArrayByName().get(blobArrayTypeRef.blobArrayTypeName());
      if (resolvedBlobArray == null) {
        throw new IllegalStateException("Missing reusable blobArray for " + fieldName + '.');
      }
      return "std::array<std::uint8_t, " + resolvedBlobArray.length() + ">";
    }
    if (typeRef instanceof BlobVectorTypeRef) {
      return "std::vector<std::uint8_t>";
    }
    if (typeRef instanceof VarStringTypeRef) {
      throw new IllegalStateException(
          "VarString type references are unsupported in C++ milestone 03.");
    }
    throw new IllegalStateException(
        "Unsupported type reference: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Resolves one collection element type into a C++ type.
   *
   * @param elementTypeRef resolved element type reference
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @param ownerName owner name used in fallback exceptions
   * @return C++ element type
   */
  private static String collectionElementCppType(
      ResolvedTypeRef elementTypeRef,
      String currentNamespace,
      GenerationContext generationContext,
      String ownerName) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().cppTypeName();
    }
    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      return cppMessageTypeName(
          messageTypeRef.messageTypeName(), currentNamespace, generationContext);
    }
    if (elementTypeRef instanceof FloatTypeRef || elementTypeRef instanceof ScaledIntTypeRef) {
      return "double";
    }
    throw new IllegalStateException(
        "Unsupported collection element type for "
            + ownerName
            + ": "
            + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Resolves one message type reference into a local or fully-qualified C++ type.
   *
   * @param messageTypeName referenced message type name
   * @param currentNamespace namespace of the current generated message
   * @param generationContext reusable lookup maps
   * @return C++ type expression
   */
  private static String cppMessageTypeName(
      String messageTypeName, String currentNamespace, GenerationContext generationContext) {
    ResolvedMessageType referenced = generationContext.messageTypeByName().get(messageTypeName);
    if (referenced == null) {
      return messageTypeName;
    }
    if (referenced.effectiveNamespace().equals(currentNamespace)) {
      return referenced.name();
    }
    return "::" + toCppNamespace(referenced.effectiveNamespace()) + "::" + referenced.name();
  }

  /**
   * Appends method declarations for generated bitfield flag/segment helpers.
   *
   * @param builder destination header builder
   * @param messageType message being rendered
   */
  private static void appendBitFieldHelperDeclarations(
      StringBuilder builder, ResolvedMessageType messageType) {
    for (ResolvedMessageMember member : messageType.members()) {
      if (!(member instanceof ResolvedBitField resolvedBitField)) {
        continue;
      }
      String bitFieldPascal = toPascalCase(resolvedBitField.name());
      for (ResolvedBitFlag resolvedBitFlag : resolvedBitField.flags()) {
        String flagPascal = toPascalCase(resolvedBitFlag.name());
        builder
            .append("  bool get")
            .append(bitFieldPascal)
            .append(flagPascal)
            .append("() const;\n");
        builder
            .append("  void set")
            .append(bitFieldPascal)
            .append(flagPascal)
            .append("(bool value);\n");
      }
      for (ResolvedBitSegment resolvedBitSegment : resolvedBitField.segments()) {
        String segmentPascal = toPascalCase(resolvedBitSegment.name());
        builder
            .append("  std::uint64_t get")
            .append(bitFieldPascal)
            .append(segmentPascal)
            .append("() const;\n");
        builder
            .append("  void set")
            .append(bitFieldPascal)
            .append(segmentPascal)
            .append("(std::uint64_t value);\n");
        for (ResolvedBitVariant resolvedBitVariant : resolvedBitSegment.variants()) {
          builder
              .append("  static constexpr std::uint64_t ")
              .append(
                  toUpperSnakeCase(
                      resolvedBitField.name()
                          + "_"
                          + resolvedBitSegment.name()
                          + "_"
                          + resolvedBitVariant.name()))
              .append(" = ")
              .append(cppUnsignedLiteral(resolvedBitVariant.value()))
              .append(";\n");
        }
      }
      builder.append('\n');
    }
  }

  /**
   * Renders one C++ source file for a resolved message.
   *
   * @param messageType message type to render
   * @param generationContext reusable lookup maps
   * @return generated C++ source text
   */
  private static String renderSource(
      ResolvedMessageType messageType, GenerationContext generationContext) {
    StringBuilder builder = new StringBuilder();
    builder.append("#include \"").append(headerIncludePath(messageType)).append("\"\n\n");
    builder.append("#include <algorithm>\n");
    builder.append("#include <cmath>\n");
    builder.append("#include <cstring>\n");
    builder.append("#include <limits>\n");
    builder.append("#include <stdexcept>\n");
    builder.append("#include <string>\n");
    builder.append("#include <type_traits>\n\n");
    appendNamespaceOpen(builder, messageType.effectiveNamespace());
    builder.append(SHARED_CPP_HELPERS).append('\n');
    appendBitFieldHelperDefinitions(builder, messageType);
    appendEncodeMethod(builder, messageType, generationContext);
    appendDecodeMethod(builder, messageType);
    appendDecodeFromMethod(builder, messageType, generationContext);
    appendNamespaceClose(builder, messageType.effectiveNamespace());
    return builder.toString();
  }

  /**
   * Appends generated bitfield helper method definitions.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   */
  private static void appendBitFieldHelperDefinitions(
      StringBuilder builder, ResolvedMessageType messageType) {
    for (ResolvedMessageMember member : messageType.members()) {
      if (!(member instanceof ResolvedBitField resolvedBitField)) {
        continue;
      }
      PrimitiveType storageType = bitFieldStoragePrimitive(resolvedBitField.size());
      String cppStorageType = storageType.cppTypeName();
      String bitFieldName = resolvedBitField.name();
      String bitFieldPascal = toPascalCase(bitFieldName);
      for (ResolvedBitFlag resolvedBitFlag : resolvedBitField.flags()) {
        String flagPascal = toPascalCase(resolvedBitFlag.name());
        long mask = 1L << resolvedBitFlag.position();
        builder
            .append("bool ")
            .append(messageType.name())
            .append("::get")
            .append(bitFieldPascal)
            .append(flagPascal)
            .append("() const {\n")
            .append("  return (static_cast<std::uint64_t>(")
            .append(bitFieldName)
            .append(") & ")
            .append(mask)
            .append("ULL) != 0ULL;\n")
            .append("}\n\n")
            .append("void ")
            .append(messageType.name())
            .append("::set")
            .append(bitFieldPascal)
            .append(flagPascal)
            .append("(bool value) {\n")
            .append("  std::uint64_t raw = static_cast<std::uint64_t>(")
            .append(bitFieldName)
            .append(");\n")
            .append("  if (value) {\n")
            .append("    raw |= ")
            .append(mask)
            .append("ULL;\n")
            .append("  } else {\n")
            .append("    raw &= ~")
            .append(mask)
            .append("ULL;\n")
            .append("  }\n")
            .append("  ")
            .append(bitFieldName)
            .append(" = static_cast<")
            .append(cppStorageType)
            .append(">(raw);\n")
            .append("}\n\n");
      }

      for (ResolvedBitSegment resolvedBitSegment : resolvedBitField.segments()) {
        String segmentPascal = toPascalCase(resolvedBitSegment.name());
        int width = resolvedBitSegment.toBit() - resolvedBitSegment.fromBit() + 1;
        String maskLiteral = segmentMaskLiteral(width);
        String shiftedMaskLiteral = shiftedSegmentMaskLiteral(width, resolvedBitSegment.fromBit());
        builder
            .append("std::uint64_t ")
            .append(messageType.name())
            .append("::get")
            .append(bitFieldPascal)
            .append(segmentPascal)
            .append("() const {\n")
            .append("  return (static_cast<std::uint64_t>(")
            .append(bitFieldName)
            .append(") >> ")
            .append(resolvedBitSegment.fromBit())
            .append(") & ")
            .append(maskLiteral)
            .append(";\n")
            .append("}\n\n")
            .append("void ")
            .append(messageType.name())
            .append("::set")
            .append(bitFieldPascal)
            .append(segmentPascal)
            .append("(std::uint64_t value) {\n")
            .append("  if (value > ")
            .append(maskLiteral)
            .append(") {\n")
            .append("    throw std::invalid_argument(\"")
            .append(bitFieldName)
            .append('.')
            .append(resolvedBitSegment.name())
            .append(" is out of range for its bit segment.\");\n")
            .append("  }\n")
            .append("  std::uint64_t raw = static_cast<std::uint64_t>(")
            .append(bitFieldName)
            .append(");\n")
            .append("  raw = (raw & ~")
            .append(shiftedMaskLiteral)
            .append(") | ((value & ")
            .append(maskLiteral)
            .append(") << ")
            .append(resolvedBitSegment.fromBit())
            .append(");\n")
            .append("  ")
            .append(bitFieldName)
            .append(" = static_cast<")
            .append(cppStorageType)
            .append(">(raw);\n")
            .append("}\n\n");
      }
    }
  }

  /**
   * Appends the public `encode()` method.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendEncodeMethod(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    builder
        .append("std::vector<std::uint8_t> ")
        .append(messageType.name())
        .append("::encode() const {\n")
        .append("  std::vector<std::uint8_t> out;\n");
    appendEncodeMembers(
        builder,
        messageType.members(),
        messageType,
        generationContext,
        primitiveFieldByName,
        "this->");
    builder.append("  return out;\n").append("}\n\n");
  }

  /**
   * Appends encode statements for a member list.
   *
   * @param builder destination source builder
   * @param members members to encode in declaration order
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendEncodeMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    for (ResolvedMessageMember member : members) {
      appendEncodeMember(
          builder, member, messageType, generationContext, primitiveFieldByName, ownerPrefix);
    }
  }

  /**
   * Appends encode statements for one member.
   *
   * @param builder destination source builder
   * @param member member being encoded
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendEncodeMember(
      StringBuilder builder,
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (member instanceof ResolvedField resolvedField) {
      appendEncodeField(
          builder,
          resolvedField.typeRef(),
          "this->" + resolvedField.name(),
          resolvedField.name(),
          resolvedField.endian(),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      appendEncodePrimitive(
          builder,
          "this->" + resolvedBitField.name(),
          bitFieldStoragePrimitive(resolvedBitField.size()),
          resolvedBitField.endian(),
          resolvedBitField.name());
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      appendEncodeFloat(
          builder, "this->" + resolvedFloat.name(), resolvedFloat, resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      appendEncodeScaledInt(
          builder,
          "this->" + resolvedScaledInt.name(),
          resolvedScaledInt,
          resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      appendEncodeArray(
          builder,
          "this->" + resolvedArray.name(),
          resolvedArray,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      appendEncodeVector(
          builder,
          "this->" + resolvedVector.name(),
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          resolvedVector.name());
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      appendEncodeBlobArray(builder, "this->" + resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      appendEncodeBlobVector(
          builder,
          "this->" + resolvedBlobVector.name(),
          resolvedBlobVector,
          primitiveFieldByName,
          ownerPrefix,
          resolvedBlobVector.name());
    }
  }

  /**
   * Appends encode statements for one field (including reusable type references).
   *
   * @param builder destination source builder
   * @param typeRef field type reference
   * @param valueExpression expression that resolves to the field value
   * @param fieldName field name used in helper labels
   * @param endian optional endian override
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendEncodeField(
      StringBuilder builder,
      ResolvedTypeRef typeRef,
      String valueExpression,
      String fieldName,
      Endian endian,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendEncodePrimitive(
          builder, valueExpression, primitiveTypeRef.primitiveType(), endian, fieldName);
      return;
    }
    if (typeRef instanceof MessageTypeRef) {
      builder
          .append("  {\n")
          .append("    std::vector<std::uint8_t> nested = ")
          .append(valueExpression)
          .append(".encode();\n")
          .append("    out.insert(out.end(), nested.begin(), nested.end());\n")
          .append("  }\n");
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      appendEncodeFloat(builder, valueExpression, resolvedFloat, fieldName);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      appendEncodeScaledInt(builder, valueExpression, resolvedScaledInt, fieldName);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      appendEncodeArray(
          builder,
          valueExpression,
          resolvedArray,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      appendEncodeVector(
          builder,
          valueExpression,
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef) {
      appendEncodeBlobArray(builder, valueExpression);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      appendEncodeBlobVector(
          builder,
          valueExpression,
          resolvedBlobVector,
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    throw new IllegalStateException(
        "Unsupported field type reference in C++ encode: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Appends primitive encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the primitive value
   * @param primitiveType primitive wire type
   * @param endian optional endian override
   * @param label label used in runtime exception text
   */
  private static void appendEncodePrimitive(
      StringBuilder builder,
      String valueExpression,
      PrimitiveType primitiveType,
      Endian endian,
      String label) {
    boolean littleEndian = endian == Endian.LITTLE;
    String littleEndianLiteral = littleEndian ? "true" : "false";
    builder
        .append("  writeIntegral<")
        .append(primitiveType.cppTypeName())
        .append(">(out, ")
        .append(valueExpression)
        .append(", ")
        .append(littleEndianLiteral)
        .append(");\n");
    if (primitiveType == PrimitiveType.UINT8 || primitiveType == PrimitiveType.INT8) {
      return;
    }
    builder.append("  (void)\"").append(label).append("\";\n");
  }

  /**
   * Appends float encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the logical float value
   * @param resolvedFloat float definition
   * @param fieldName field/member name used in exception text
   */
  private static void appendEncodeFloat(
      StringBuilder builder,
      String valueExpression,
      ResolvedFloat resolvedFloat,
      String fieldName) {
    String littleEndianLiteral = resolvedFloat.endian() == Endian.LITTLE ? "true" : "false";
    String scaleLiteral = decimalLiteral(resolvedFloat.scale());

    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      if (resolvedFloat.size() == FloatSize.F16) {
        builder
            .append("  writeFloat16(out, static_cast<float>(")
            .append(valueExpression)
            .append("), ")
            .append(littleEndianLiteral)
            .append(");\n");
        return;
      }
      if (resolvedFloat.size() == FloatSize.F32) {
        builder
            .append("  writeFloat32(out, static_cast<float>(")
            .append(valueExpression)
            .append("), ")
            .append(littleEndianLiteral)
            .append(");\n");
        return;
      }
      builder
          .append("  writeFloat64(out, ")
          .append(valueExpression)
          .append(", ")
          .append(littleEndianLiteral)
          .append(");\n");
      return;
    }

    if (resolvedFloat.size() == FloatSize.F16) {
      builder
          .append("  writeIntegral<std::int16_t>(out, scaleToSignedRaw<std::int16_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      return;
    }
    if (resolvedFloat.size() == FloatSize.F32) {
      builder
          .append("  writeIntegral<std::int32_t>(out, scaleToSignedRaw<std::int32_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      return;
    }
    builder
        .append("  writeIntegral<std::int64_t>(out, scaleToSignedRaw<std::int64_t>(")
        .append(valueExpression)
        .append(", ")
        .append(scaleLiteral)
        .append(", \"")
        .append(fieldName)
        .append("\"), ")
        .append(littleEndianLiteral)
        .append(");\n");
  }

  /**
   * Appends scaled-int encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the logical scaled value
   * @param resolvedScaledInt scaled-int definition
   * @param fieldName field/member name used in exception text
   */
  private static void appendEncodeScaledInt(
      StringBuilder builder,
      String valueExpression,
      ResolvedScaledInt resolvedScaledInt,
      String fieldName) {
    String littleEndianLiteral = resolvedScaledInt.endian() == Endian.LITTLE ? "true" : "false";
    String scaleLiteral = decimalLiteral(resolvedScaledInt.scale());
    switch (resolvedScaledInt.baseType()) {
      case INT8 -> builder
          .append("  writeIntegral<std::int8_t>(out, scaleToSignedRaw<std::int8_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), false);\n");
      case UINT8 -> builder
          .append("  writeIntegral<std::uint8_t>(out, scaleToUnsignedRaw<std::uint8_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), false);\n");
      case INT16 -> builder
          .append("  writeIntegral<std::int16_t>(out, scaleToSignedRaw<std::int16_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case UINT16 -> builder
          .append("  writeIntegral<std::uint16_t>(out, scaleToUnsignedRaw<std::uint16_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case INT32 -> builder
          .append("  writeIntegral<std::int32_t>(out, scaleToSignedRaw<std::int32_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case UINT32 -> builder
          .append("  writeIntegral<std::uint32_t>(out, scaleToUnsignedRaw<std::uint32_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case INT64 -> builder
          .append("  writeIntegral<std::int64_t>(out, scaleToSignedRaw<std::int64_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case UINT64 -> builder
          .append("  writeIntegral<std::uint64_t>(out, scaleToUnsignedRaw<std::uint64_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
    }
  }

  /**
   * Appends fixed-array encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the array value
   * @param resolvedArray array definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendEncodeArray(
      StringBuilder builder,
      String valueExpression,
      ResolvedArray resolvedArray,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String loopIndex = toLoopIndexName(resolvedArray.name());
    String itemName = toLoopItemName(resolvedArray.name());
    builder
        .append("  for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndex)
        .append("++) {\n")
        .append("    auto ")
        .append(itemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndex)
        .append("];\n");
    appendEncodeCollectionElement(
        builder,
        itemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix,
        resolvedArray.name());
    builder.append("  }\n");
  }

  /**
   * Appends vector encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the vector value
   * @param resolvedVector vector definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendEncodeVector(
      StringBuilder builder,
      String valueExpression,
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    appendCountValidation(
        builder,
        resolvedVector.lengthMode(),
        valueExpression,
        primitiveFieldByName,
        ownerPrefix,
        ownerName);
    String loopIndex = toLoopIndexName(ownerName);
    String itemName = toLoopItemName(ownerName);
    builder
        .append("  for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < ")
        .append(valueExpression)
        .append(".size(); ")
        .append(loopIndex)
        .append("++) {\n")
        .append("    auto ")
        .append(itemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndex)
        .append("];\n");
    appendEncodeCollectionElement(
        builder,
        itemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix,
        ownerName);
    builder.append("  }\n");

    String terminatorLiteral = terminatorLiteral(resolvedVector.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      PrimitiveType primitiveType =
          ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
      appendEncodePrimitive(
          builder,
          primitiveLiteralExpression(primitiveType, numericLiteral),
          primitiveType,
          resolvedVector.endian(),
          ownerName + "_terminator");
    }
  }

  /**
   * Appends blob-array encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob-array value
   */
  private static void appendEncodeBlobArray(StringBuilder builder, String valueExpression) {
    builder
        .append("  out.insert(out.end(), ")
        .append(valueExpression)
        .append(".begin(), ")
        .append(valueExpression)
        .append(".end());\n");
  }

  /**
   * Appends blob-vector encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob-vector value
   * @param resolvedBlobVector blob-vector definition
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendEncodeBlobVector(
      StringBuilder builder,
      String valueExpression,
      ResolvedBlobVector resolvedBlobVector,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    appendCountValidation(
        builder,
        resolvedBlobVector.lengthMode(),
        valueExpression,
        primitiveFieldByName,
        ownerPrefix,
        ownerName);
    builder
        .append("  out.insert(out.end(), ")
        .append(valueExpression)
        .append(".begin(), ")
        .append(valueExpression)
        .append(".end());\n");
    String terminatorLiteral = terminatorLiteral(resolvedBlobVector.lengthMode());
    if (terminatorLiteral != null) {
      appendEncodePrimitive(
          builder,
          primitiveLiteralExpression(PrimitiveType.UINT8, parseNumericLiteral(terminatorLiteral)),
          PrimitiveType.UINT8,
          Endian.BIG,
          ownerName + "_terminator");
    }
  }

  /**
   * Appends encode statements for one collection element.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to one element value
   * @param elementTypeRef collection element type
   * @param endian optional endian override
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendEncodeCollectionElement(
      StringBuilder builder,
      String valueExpression,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendEncodePrimitive(
          builder, valueExpression, primitiveTypeRef.primitiveType(), endian, ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof MessageTypeRef) {
      builder
          .append("    {\n")
          .append("      std::vector<std::uint8_t> nested = ")
          .append(valueExpression)
          .append(".encode();\n")
          .append("      out.insert(out.end(), nested.begin(), nested.end());\n")
          .append("    }\n");
      return;
    }
    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      appendEncodeFloat(
          builder,
          valueExpression,
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName()),
          ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      appendEncodeScaledInt(
          builder,
          valueExpression,
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName()),
          ownerName + "_item");
      return;
    }
    throw new IllegalStateException(
        "Unsupported collection element type in C++ encode: "
            + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Appends count-field validation for vector/blob-vector encode paths.
   *
   * @param builder destination source builder
   * @param lengthMode vector/blob length mode
   * @param valueExpression expression that resolves to collection value
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendCountValidation(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      String valueExpression,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }
    PrimitiveType countType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    if (countType == null) {
      return;
    }
    String countExpression = ownerPrefix + resolvedCountFieldLength.ref();
    builder
        .append("  std::size_t expected")
        .append(toPascalCase(ownerName))
        .append("Count = requireCount(")
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("  if (")
        .append(valueExpression)
        .append(".size() != expected")
        .append(toPascalCase(ownerName))
        .append("Count) {\n")
        .append("    throw std::invalid_argument(\"")
        .append(ownerName)
        .append(" length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("  }\n");
  }

  /**
   * Appends the public `decode(span)` method.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   */
  private static void appendDecodeMethod(StringBuilder builder, ResolvedMessageType messageType) {
    builder
        .append(messageType.name())
        .append(' ')
        .append(messageType.name())
        .append("::decode(std::span<const std::uint8_t> data) {\n")
        .append("  std::size_t cursor = 0;\n")
        .append("  ")
        .append(messageType.name())
        .append(" value = ")
        .append(messageType.name())
        .append("::decodeFrom(data, cursor);\n")
        .append("  if (cursor != data.size()) {\n")
        .append("    throw std::invalid_argument(\"Extra bytes remain after decoding ")
        .append(messageType.name())
        .append(".\");\n")
        .append("  }\n")
        .append("  return value;\n")
        .append("}\n\n");
  }

  /**
   * Appends the cursor-based decode method used by nested message decoding.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendDecodeFromMethod(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    builder
        .append(messageType.name())
        .append(' ')
        .append(messageType.name())
        .append("::decodeFrom(std::span<const std::uint8_t> data, std::size_t& cursor) {\n")
        .append("  ")
        .append(messageType.name())
        .append(" value{};\n");
    appendDecodeMembers(
        builder,
        messageType.members(),
        messageType,
        generationContext,
        primitiveFieldByName,
        "value.");
    builder.append("  return value;\n").append("}\n\n");
  }

  /**
   * Appends decode statements for a member list.
   *
   * @param builder destination source builder
   * @param members members to decode in declaration order
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendDecodeMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    for (ResolvedMessageMember member : members) {
      appendDecodeMember(
          builder, member, messageType, generationContext, primitiveFieldByName, ownerPrefix);
    }
  }

  /**
   * Appends decode statements for one member.
   *
   * @param builder destination source builder
   * @param member member being decoded
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendDecodeMember(
      StringBuilder builder,
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (member instanceof ResolvedField resolvedField) {
      appendDecodeField(
          builder,
          resolvedField.typeRef(),
          ownerPrefix + resolvedField.name(),
          resolvedField.name(),
          resolvedField.endian(),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      appendDecodePrimitive(
          builder,
          ownerPrefix + resolvedBitField.name(),
          bitFieldStoragePrimitive(resolvedBitField.size()),
          resolvedBitField.endian(),
          resolvedBitField.name());
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      appendDecodeFloat(
          builder, ownerPrefix + resolvedFloat.name(), resolvedFloat, resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      appendDecodeScaledInt(
          builder,
          ownerPrefix + resolvedScaledInt.name(),
          resolvedScaledInt,
          resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      appendDecodeArray(
          builder,
          ownerPrefix + resolvedArray.name(),
          resolvedArray,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      appendDecodeVector(
          builder,
          ownerPrefix + resolvedVector.name(),
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          resolvedVector.name());
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      appendDecodeBlobArray(
          builder, ownerPrefix + resolvedBlobArray.name(), resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      appendDecodeBlobVector(
          builder,
          ownerPrefix + resolvedBlobVector.name(),
          resolvedBlobVector,
          primitiveFieldByName,
          ownerPrefix,
          resolvedBlobVector.name());
    }
  }

  /**
   * Appends decode statements for one field (including reusable type references).
   *
   * @param builder destination source builder
   * @param typeRef field type reference
   * @param targetExpression assignment target expression
   * @param fieldName field name used in helper labels
   * @param endian optional endian override
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendDecodeField(
      StringBuilder builder,
      ResolvedTypeRef typeRef,
      String targetExpression,
      String fieldName,
      Endian endian,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendDecodePrimitive(
          builder, targetExpression, primitiveTypeRef.primitiveType(), endian, fieldName);
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      builder
          .append("  ")
          .append(targetExpression)
          .append(" = ")
          .append(
              cppMessageTypeName(
                  messageTypeRef.messageTypeName(),
                  messageType.effectiveNamespace(),
                  generationContext))
          .append("::decodeFrom(data, cursor);\n");
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      appendDecodeFloat(
          builder,
          targetExpression,
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName()),
          fieldName);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      appendDecodeScaledInt(
          builder,
          targetExpression,
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName()),
          fieldName);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      appendDecodeArray(
          builder,
          targetExpression,
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName()),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      appendDecodeVector(
          builder,
          targetExpression,
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName()),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef) {
      appendDecodeBlobArray(builder, targetExpression, fieldName);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      appendDecodeBlobVector(
          builder,
          targetExpression,
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName()),
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    throw new IllegalStateException(
        "Unsupported field type reference in C++ decode: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Appends primitive decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param primitiveType primitive wire type
   * @param endian optional endian override
   * @param label label used in runtime exception text
   */
  private static void appendDecodePrimitive(
      StringBuilder builder,
      String targetExpression,
      PrimitiveType primitiveType,
      Endian endian,
      String label) {
    boolean littleEndian = endian == Endian.LITTLE;
    builder
        .append("  ")
        .append(targetExpression)
        .append(" = readIntegral<")
        .append(primitiveType.cppTypeName())
        .append(">(data, cursor, ")
        .append(littleEndian ? "true" : "false")
        .append(", \"")
        .append(label)
        .append("\");\n");
  }

  /**
   * Appends float decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedFloat float definition
   * @param fieldName field/member name used in exception text
   */
  private static void appendDecodeFloat(
      StringBuilder builder,
      String targetExpression,
      ResolvedFloat resolvedFloat,
      String fieldName) {
    String littleEndianLiteral = resolvedFloat.endian() == Endian.LITTLE ? "true" : "false";
    String scaleLiteral = decimalLiteral(resolvedFloat.scale());
    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      if (resolvedFloat.size() == FloatSize.F16) {
        builder
            .append("  ")
            .append(targetExpression)
            .append(" = static_cast<double>(readFloat16(data, cursor, ")
            .append(littleEndianLiteral)
            .append(", \"")
            .append(fieldName)
            .append("\"));\n");
        return;
      }
      if (resolvedFloat.size() == FloatSize.F32) {
        builder
            .append("  ")
            .append(targetExpression)
            .append(" = static_cast<double>(readFloat32(data, cursor, ")
            .append(littleEndianLiteral)
            .append(", \"")
            .append(fieldName)
            .append("\"));\n");
        return;
      }
      builder
          .append("  ")
          .append(targetExpression)
          .append(" = readFloat64(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\");\n");
      return;
    }

    if (resolvedFloat.size() == FloatSize.F16) {
      builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int16_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      return;
    }
    if (resolvedFloat.size() == FloatSize.F32) {
      builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int32_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      return;
    }
    builder
        .append("  ")
        .append(targetExpression)
        .append(" = static_cast<double>(readIntegral<std::int64_t>(data, cursor, ")
        .append(littleEndianLiteral)
        .append(", \"")
        .append(fieldName)
        .append("\")) * ")
        .append(scaleLiteral)
        .append(";\n");
  }

  /**
   * Appends scaled-int decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedScaledInt scaled-int definition
   * @param fieldName field/member name used in exception text
   */
  private static void appendDecodeScaledInt(
      StringBuilder builder,
      String targetExpression,
      ResolvedScaledInt resolvedScaledInt,
      String fieldName) {
    String littleEndianLiteral = resolvedScaledInt.endian() == Endian.LITTLE ? "true" : "false";
    String scaleLiteral = decimalLiteral(resolvedScaledInt.scale());
    switch (resolvedScaledInt.baseType()) {
      case INT8 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int8_t>(data, cursor, false, \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT8 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::uint8_t>(data, cursor, false, \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT16 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int16_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT16 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::uint16_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT32 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int32_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT32 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::uint32_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT64 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int64_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT64 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::uint64_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
    }
  }

  /**
   * Appends fixed-array decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedArray array definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendDecodeArray(
      StringBuilder builder,
      String targetExpression,
      ResolvedArray resolvedArray,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String loopIndex = toLoopIndexName(resolvedArray.name());
    String itemName = toLoopItemName(resolvedArray.name());
    builder
        .append("  for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndex)
        .append("++) {\n");
    appendDecodeCollectionElement(
        builder,
        itemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix,
        resolvedArray.name());
    builder
        .append("    ")
        .append(targetExpression)
        .append('[')
        .append(loopIndex)
        .append("] = ")
        .append(itemName)
        .append(";\n")
        .append("  }\n");
  }

  /**
   * Appends vector decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (resolvedVector.lengthMode() instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      appendDecodeCountedVector(
          builder,
          targetExpression,
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          ownerName,
          resolvedCountFieldLength);
      return;
    }
    appendDecodeTerminatedVector(
        builder, targetExpression, resolvedVector, messageType, generationContext, ownerName);
  }

  /**
   * Appends decode statements for count-based vectors.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   * @param resolvedCountFieldLength count-field length mode
   */
  private static void appendDecodeCountedVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName,
      ResolvedCountFieldLength resolvedCountFieldLength) {
    PrimitiveType countType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    if (countType == null) {
      return;
    }
    String itemName = toLoopItemName(ownerName);
    String loopIndex = toLoopIndexName(ownerName);
    builder
        .append("  {\n")
        .append("    std::size_t expected")
        .append(toPascalCase(ownerName))
        .append("Count = requireCount(")
        .append(ownerPrefix)
        .append(resolvedCountFieldLength.ref())
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("    ")
        .append(targetExpression)
        .append(".reserve(expected")
        .append(toPascalCase(ownerName))
        .append("Count);\n")
        .append("    for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < expected")
        .append(toPascalCase(ownerName))
        .append("Count; ")
        .append(loopIndex)
        .append("++) {\n");
    appendDecodeCollectionElement(
        builder,
        itemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix,
        ownerName);
    builder
        .append("      ")
        .append(targetExpression)
        .append(".push_back(")
        .append(itemName)
        .append(");\n")
        .append("    }\n")
        .append("  }\n");
  }

  /**
   * Appends decode statements for terminator-based vectors.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeTerminatedVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      String ownerName) {
    String literal = terminatorLiteral(resolvedVector.lengthMode());
    BigInteger numericLiteral = parseNumericLiteral(literal);
    PrimitiveType primitiveType =
        ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
    String itemName = toLoopItemName(ownerName);

    builder
        .append("  ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("  while (true) {\n");
    appendDecodeCollectionElement(
        builder,
        itemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        messageType,
        generationContext,
        Map.of(),
        "value.",
        ownerName);
    builder
        .append("    if (")
        .append(itemName)
        .append(" == ")
        .append(primitiveLiteralExpression(primitiveType, numericLiteral))
        .append(") {\n")
        .append("      break;\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(".push_back(")
        .append(itemName)
        .append(");\n")
        .append("  }\n");
  }

  /**
   * Appends decode statements for one collection element.
   *
   * @param builder destination source builder
   * @param localName local variable that receives decoded value
   * @param elementTypeRef collection element type
   * @param endian optional endian override
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeCollectionElement(
      StringBuilder builder,
      String localName,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      builder
          .append("    ")
          .append(primitiveTypeRef.primitiveType().cppTypeName())
          .append(' ')
          .append(localName)
          .append(" = readIntegral<")
          .append(primitiveTypeRef.primitiveType().cppTypeName())
          .append(">(data, cursor, ")
          .append(endian == Endian.LITTLE ? "true" : "false")
          .append(", \"")
          .append(ownerName)
          .append("_item\");\n");
      return;
    }
    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String cppType =
          cppMessageTypeName(
              messageTypeRef.messageTypeName(),
              messageType.effectiveNamespace(),
              generationContext);
      builder
          .append("    ")
          .append(cppType)
          .append(' ')
          .append(localName)
          .append(" = ")
          .append(cppType)
          .append("::decodeFrom(data, cursor);\n");
      return;
    }
    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      builder.append("    double ").append(localName).append("{};\n");
      appendDecodeFloat(builder, localName, resolvedFloat, ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      builder.append("    double ").append(localName).append("{};\n");
      appendDecodeScaledInt(builder, localName, resolvedScaledInt, ownerName + "_item");
      return;
    }
    throw new IllegalStateException(
        "Unsupported collection element type in C++ decode: "
            + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Appends blob-array decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param fieldName field name used in helper labels
   */
  private static void appendDecodeBlobArray(
      StringBuilder builder, String targetExpression, String fieldName) {
    builder
        .append("  requireReadable(data, cursor, ")
        .append(targetExpression)
        .append(".size(), \"")
        .append(fieldName)
        .append("\");\n")
        .append("  std::copy_n(data.begin() + static_cast<std::ptrdiff_t>(cursor), ")
        .append(targetExpression)
        .append(".size(), ")
        .append(targetExpression)
        .append(".begin());\n")
        .append("  cursor += ")
        .append(targetExpression)
        .append(".size();\n");
  }

  /**
   * Appends blob-vector decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedBlobVector blob-vector definition
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeBlobVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedBlobVector resolvedBlobVector,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (resolvedBlobVector.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      PrimitiveType countType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
      if (countType == null) {
        return;
      }
      builder
          .append("  {\n")
          .append("    std::size_t expected")
          .append(toPascalCase(ownerName))
          .append("Count = requireCount(")
          .append(ownerPrefix)
          .append(resolvedCountFieldLength.ref())
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    requireReadable(data, cursor, expected")
          .append(toPascalCase(ownerName))
          .append("Count, \"")
          .append(ownerName)
          .append("\");\n")
          .append("    ")
          .append(targetExpression)
          .append(".assign(\n")
          .append("        data.begin() + static_cast<std::ptrdiff_t>(cursor),\n")
          .append("        data.begin() + static_cast<std::ptrdiff_t>(cursor + expected")
          .append(toPascalCase(ownerName))
          .append("Count));\n")
          .append("    cursor += expected")
          .append(toPascalCase(ownerName))
          .append("Count;\n")
          .append("  }\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedBlobVector.lengthMode()));
    builder
        .append("  ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("  while (true) {\n")
        .append("    std::uint8_t nextByte = readIntegral<std::uint8_t>(data, cursor, false, \"")
        .append(ownerName)
        .append("_item\");\n")
        .append("    if (nextByte == ")
        .append(primitiveLiteralExpression(PrimitiveType.UINT8, numericLiteral))
        .append(") {\n")
        .append("      break;\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(".push_back(nextByte);\n")
        .append("  }\n");
  }

  /**
   * Returns the exact declaration name for one member.
   *
   * @param member member whose name is needed
   * @return member name in source order
   */
  private static String memberName(ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      return resolvedField.name();
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      return resolvedBitField.name();
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      return resolvedFloat.name();
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      return resolvedScaledInt.name();
    }
    if (member instanceof ResolvedArray resolvedArray) {
      return resolvedArray.name();
    }
    if (member instanceof ResolvedVector resolvedVector) {
      return resolvedVector.name();
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      return resolvedBlobArray.name();
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      return resolvedBlobVector.name();
    }
    throw new IllegalStateException("Unsupported member has no declaration name: " + member);
  }

  /**
   * Resolves one bitfield size to the matching primitive storage type.
   *
   * @param bitFieldSize bitfield storage size
   * @return primitive storage type
   */
  private static PrimitiveType bitFieldStoragePrimitive(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> PrimitiveType.UINT8;
      case U16 -> PrimitiveType.UINT16;
      case U32 -> PrimitiveType.UINT32;
      case U64 -> PrimitiveType.UINT64;
    };
  }

  /**
   * Converts one decimal value to deterministic C++ literal text.
   *
   * @param value decimal value from resolved model
   * @return C++ decimal literal text
   */
  private static String decimalLiteral(BigDecimal value) {
    return value == null ? "1.0" : value.toPlainString();
  }

  /**
   * Resolves optional terminator literal from a length mode.
   *
   * @param lengthMode resolved length mode
   * @return terminator literal when present, otherwise {@code null}
   */
  private static String terminatorLiteral(ResolvedLengthMode lengthMode) {
    if (lengthMode instanceof ResolvedTerminatorValueLength resolvedTerminatorValueLength) {
      return resolvedTerminatorValueLength.value();
    }
    if (lengthMode instanceof ResolvedTerminatorField resolvedTerminatorField) {
      return terminatorLiteral(resolvedTerminatorField.next());
    }
    return null;
  }

  /**
   * Resolves optional terminator literal from one terminator node.
   *
   * @param terminatorNode resolved terminator node
   * @return terminator literal when present, otherwise {@code null}
   */
  private static String terminatorLiteral(ResolvedTerminatorNode terminatorNode) {
    if (terminatorNode == null) {
      return null;
    }
    if (terminatorNode instanceof ResolvedTerminatorMatch resolvedTerminatorMatch) {
      return resolvedTerminatorMatch.value();
    }
    return terminatorLiteral(((ResolvedTerminatorField) terminatorNode).next());
  }

  /**
   * Parses one numeric literal used by vector/blob terminator modes.
   *
   * @param literal raw numeric literal text from XML
   * @return parsed integer value
   */
  private static BigInteger parseNumericLiteral(String literal) {
    String trimmed = literal.trim();
    if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      return new BigInteger(trimmed.substring(2), 16);
    }
    if (trimmed.startsWith("-0x") || trimmed.startsWith("-0X")) {
      return new BigInteger(trimmed.substring(3), 16).negate();
    }
    if (trimmed.matches("-?[0-9]+")) {
      return new BigInteger(trimmed, 10);
    }
    return new BigInteger(trimmed, 16);
  }

  /**
   * Returns whether a numeric literal fits one primitive integer type.
   *
   * @param value parsed numeric literal
   * @param primitiveType primitive target type
   * @return {@code true} when representable
   */
  private static boolean fitsPrimitiveRange(BigInteger value, PrimitiveType primitiveType) {
    return switch (primitiveType) {
      case UINT8 -> inRange(value, BigInteger.ZERO, BigInteger.valueOf(255));
      case UINT16 -> inRange(value, BigInteger.ZERO, BigInteger.valueOf(65_535));
      case UINT32 -> inRange(value, BigInteger.ZERO, BigInteger.valueOf(4_294_967_295L));
      case UINT64 -> inRange(value, BigInteger.ZERO, new BigInteger("18446744073709551615"));
      case INT8 -> inRange(value, BigInteger.valueOf(-128), BigInteger.valueOf(127));
      case INT16 -> inRange(value, BigInteger.valueOf(-32_768), BigInteger.valueOf(32_767));
      case INT32 -> inRange(
          value, BigInteger.valueOf(Integer.MIN_VALUE), BigInteger.valueOf(Integer.MAX_VALUE));
      case INT64 -> inRange(
          value, BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE));
    };
  }

  /**
   * Returns whether a value is between two inclusive bounds.
   *
   * @param value value to check
   * @param lowerInclusive inclusive lower bound
   * @param upperInclusive inclusive upper bound
   * @return {@code true} when in range
   */
  private static boolean inRange(
      BigInteger value, BigInteger lowerInclusive, BigInteger upperInclusive) {
    return value.compareTo(lowerInclusive) >= 0 && value.compareTo(upperInclusive) <= 0;
  }

  /**
   * Builds a C++ expression that casts one integer literal to a primitive type.
   *
   * @param primitiveType primitive target type
   * @param numericLiteral parsed numeric literal
   * @return C++ literal expression
   */
  private static String primitiveLiteralExpression(
      PrimitiveType primitiveType, BigInteger numericLiteral) {
    return "static_cast<"
        + primitiveType.cppTypeName()
        + ">("
        + numericLiteralToken(primitiveType, numericLiteral)
        + ")";
  }

  /**
   * Builds a C++ unsigned literal token for a 64-bit constant.
   *
   * @param numericLiteral literal value
   * @return C++ token with `ULL` suffix
   */
  private static String cppUnsignedLiteral(BigInteger numericLiteral) {
    return numericLiteral.toString() + "ULL";
  }

  /**
   * Builds the numeric token text used inside generated C++ casts.
   *
   * @param primitiveType primitive target type
   * @param numericLiteral parsed numeric literal
   * @return C++ numeric token
   */
  private static String numericLiteralToken(
      PrimitiveType primitiveType, BigInteger numericLiteral) {
    if (primitiveType == PrimitiveType.UINT8
        || primitiveType == PrimitiveType.UINT16
        || primitiveType == PrimitiveType.UINT32
        || primitiveType == PrimitiveType.UINT64) {
      return numericLiteral.toString() + "ULL";
    }
    return numericLiteral.toString() + "LL";
  }

  /**
   * Builds a segment mask literal for one bit width.
   *
   * @param width segment width in bits
   * @return C++ `ULL` literal mask
   */
  private static String segmentMaskLiteral(int width) {
    if (width >= 64) {
      return "0xFFFFFFFFFFFFFFFFULL";
    }
    return BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE).toString() + "ULL";
  }

  /**
   * Builds a shifted segment mask literal for one segment position.
   *
   * @param width segment width in bits
   * @param fromBit segment start bit
   * @return shifted C++ `ULL` literal mask
   */
  private static String shiftedSegmentMaskLiteral(int width, int fromBit) {
    if (width >= 64 && fromBit == 0) {
      return "0xFFFFFFFFFFFFFFFFULL";
    }
    return BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE).shiftLeft(fromBit).toString()
        + "ULL";
  }

  /**
   * Builds a loop index variable name from one owner name.
   *
   * @param ownerName owner/member name
   * @return deterministic loop index variable name
   */
  private static String toLoopIndexName(String ownerName) {
    return "index" + toPascalCase(ownerName);
  }

  /**
   * Builds a loop item variable name from one owner name.
   *
   * @param ownerName owner/member name
   * @return deterministic loop item variable name
   */
  private static String toLoopItemName(String ownerName) {
    String pascal = toPascalCase(ownerName);
    if (pascal.isEmpty()) {
      return "item";
    }
    return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1) + "Item";
  }

  /**
   * Converts an identifier to pascal case for generated helper method names.
   *
   * @param value input identifier
   * @return pascal-case output
   */
  private static String toPascalCase(String value) {
    String[] parts = value.split("[^A-Za-z0-9]+");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      builder.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        builder.append(part.substring(1));
      }
    }
    return builder.toString();
  }

  /**
   * Converts a value to upper snake case for constant names.
   *
   * @param value input text
   * @return upper-snake output
   */
  private static String toUpperSnakeCase(String value) {
    String normalized = value.replaceAll("[^A-Za-z0-9]+", "_");
    return normalized.toUpperCase(Locale.ROOT);
  }

  /**
   * Creates one unsupported-member diagnostic.
   *
   * @param messageName parent message name
   * @param memberLabel unsupported member label
   * @param outputPath output path shown in diagnostics
   * @return unsupported-member diagnostic
   */
  private static Diagnostic unsupportedMemberDiagnostic(
      String messageName, String memberLabel, String outputPath) {
    return new Diagnostic(
        DiagnosticSeverity.ERROR,
        "GENERATOR_CPP_UNSUPPORTED_MEMBER",
        "C++ generator does not support message member "
            + memberLabel
            + " in message "
            + messageName
            + " yet.",
        outputPath,
        -1,
        -1);
  }

  /**
   * Creates one unsupported-type-reference diagnostic.
   *
   * @param messageName parent message name
   * @param fieldName field name that uses the unsupported type reference
   * @param typeRefLabel unsupported type-reference label
   * @param outputPath output path shown in diagnostics
   * @param details extra detail appended to the diagnostic message
   * @return unsupported-type-reference diagnostic
   */
  private static Diagnostic unsupportedTypeRefDiagnostic(
      String messageName,
      String fieldName,
      String typeRefLabel,
      String outputPath,
      String details) {
    return new Diagnostic(
        DiagnosticSeverity.ERROR,
        "GENERATOR_CPP_UNSUPPORTED_TYPE_REF",
        "C++ generator does not support field type reference "
            + typeRefLabel
            + " for field "
            + fieldName
            + " in message "
            + messageName
            + " yet. "
            + details,
        outputPath,
        -1,
        -1);
  }

  /**
   * Builds the relative include path for one message header.
   *
   * @param messageType message type that owns the header
   * @return include path using slash-separated namespace segments
   */
  private static String headerIncludePath(ResolvedMessageType messageType) {
    return messageType.effectiveNamespace().replace('.', '/') + "/" + messageType.name() + ".hpp";
  }

  /**
   * Appends nested namespace open lines to a source builder.
   *
   * @param builder destination source builder
   * @param namespaceValue dot-delimited namespace
   */
  private static void appendNamespaceOpen(StringBuilder builder, String namespaceValue) {
    for (String segment : splitNamespace(namespaceValue)) {
      builder.append("namespace ").append(segment).append(" {\n");
    }
    builder.append('\n');
  }

  /**
   * Appends nested namespace close lines to a source builder.
   *
   * @param builder destination source builder
   * @param namespaceValue dot-delimited namespace
   */
  private static void appendNamespaceClose(StringBuilder builder, String namespaceValue) {
    List<String> segments = splitNamespace(namespaceValue);
    for (int index = segments.size() - 1; index >= 0; index--) {
      builder.append("}  // namespace ").append(segments.get(index)).append("\n");
    }
  }

  /**
   * Splits a dot-delimited namespace into non-blank segments.
   *
   * @param namespaceValue dot-delimited namespace
   * @return namespace segments in order
   */
  private static List<String> splitNamespace(String namespaceValue) {
    String[] rawSegments = namespaceValue.split("\\.");
    List<String> segments = new ArrayList<>();
    for (String segment : rawSegments) {
      if (!segment.isBlank()) {
        segments.add(segment);
      }
    }
    return segments;
  }

  /**
   * Converts a dot-delimited namespace to a C++ {@code ::}-delimited namespace.
   *
   * @param namespaceValue dot-delimited namespace
   * @return C++ namespace string
   */
  private static String toCppNamespace(String namespaceValue) {
    return String.join("::", splitNamespace(namespaceValue));
  }

  /**
   * Immutable lookup container used by C++ generation helpers.
   *
   * @param messageTypeByName message type lookup map
   * @param reusableFloatByName reusable float lookup map
   * @param reusableScaledIntByName reusable scaled-int lookup map
   * @param reusableArrayByName reusable array lookup map
   * @param reusableVectorByName reusable vector lookup map
   * @param reusableBlobArrayByName reusable blob-array lookup map
   * @param reusableBlobVectorByName reusable blob-vector lookup map
   */
  private record GenerationContext(
      Map<String, ResolvedMessageType> messageTypeByName,
      Map<String, ResolvedFloat> reusableFloatByName,
      Map<String, ResolvedScaledInt> reusableScaledIntByName,
      Map<String, ResolvedArray> reusableArrayByName,
      Map<String, ResolvedVector> reusableVectorByName,
      Map<String, ResolvedBlobArray> reusableBlobArrayByName,
      Map<String, ResolvedBlobVector> reusableBlobVectorByName) {
    /** Creates an immutable generation context. */
    private GenerationContext {
      messageTypeByName = Map.copyOf(messageTypeByName);
      reusableFloatByName = Map.copyOf(reusableFloatByName);
      reusableScaledIntByName = Map.copyOf(reusableScaledIntByName);
      reusableArrayByName = Map.copyOf(reusableArrayByName);
      reusableVectorByName = Map.copyOf(reusableVectorByName);
      reusableBlobArrayByName = Map.copyOf(reusableBlobArrayByName);
      reusableBlobVectorByName = Map.copyOf(reusableBlobVectorByName);
    }
  }
}
