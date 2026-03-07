package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import io.github.sportne.bms.util.Diagnostics;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts parsed XML objects into resolved objects used by generators.
 *
 * <p>This stage performs checks that XSD cannot fully enforce, such as:
 *
 * <ul>
 *   <li>namespace format rules
 *   <li>duplicate message or field names
 *   <li>unknown type references
 * </ul>
 */
public final class SemanticResolver {
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern NAMESPACE_PATTERN =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

  /**
   * Builds the resolved schema.
   *
   * <p>If any semantic error exists, this method throws a {@link BmsException} with diagnostics.
   */
  public ResolvedSchema resolve(ParsedSchema parsedSchema, String sourcePath) throws BmsException {
    List<Diagnostic> diagnostics = new ArrayList<>();

    validateNamespace(parsedSchema.namespace(), "schema@namespace", sourcePath, diagnostics);

    Map<String, ParsedMessageType> messageTypeByName = new LinkedHashMap<>();
    for (ParsedMessageType messageType : parsedSchema.messageTypes()) {
      if (!IDENTIFIER_PATTERN.matcher(messageType.name()).matches()) {
        diagnostics.add(
            error(
                "SEMANTIC_INVALID_MESSAGE_NAME",
                "Message type name must be a valid identifier: " + messageType.name(),
                sourcePath));
      }
      if (messageTypeByName.putIfAbsent(messageType.name(), messageType) != null) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_MESSAGE_TYPE",
                "Duplicate message type name: " + messageType.name(),
                sourcePath));
      }
      if (messageType.namespaceOverride() != null) {
        validateNamespace(
            messageType.namespaceOverride(), "messageType@namespace", sourcePath, diagnostics);
      }
    }

    List<ResolvedMessageType> resolvedMessageTypes = new ArrayList<>();
    for (ParsedMessageType parsedMessageType : parsedSchema.messageTypes()) {
      String effectiveNamespace =
          parsedMessageType.namespaceOverride() == null
              ? parsedSchema.namespace()
              : parsedMessageType.namespaceOverride();

      Set<String> fieldNames = new HashSet<>();
      List<ResolvedField> resolvedFields = new ArrayList<>();
      for (ParsedField parsedField : parsedMessageType.fields()) {
        if (!IDENTIFIER_PATTERN.matcher(parsedField.name()).matches()) {
          diagnostics.add(
              error(
                  "SEMANTIC_INVALID_FIELD_NAME",
                  "Field name must be a valid identifier in message "
                      + parsedMessageType.name()
                      + ": "
                      + parsedField.name(),
                  sourcePath));
        }

        if (!fieldNames.add(parsedField.name())) {
          diagnostics.add(
              error(
                  "SEMANTIC_DUPLICATE_FIELD_NAME",
                  "Duplicate field name in message "
                      + parsedMessageType.name()
                      + ": "
                      + parsedField.name(),
                  sourcePath));
        }

        PrimitiveType primitiveType = PrimitiveType.fromSchemaName(parsedField.typeName());
        ResolvedTypeRef typeRef;
        if (primitiveType != null) {
          typeRef = new PrimitiveTypeRef(primitiveType);
        } else if (messageTypeByName.containsKey(parsedField.typeName())) {
          typeRef = new MessageTypeRef(parsedField.typeName());
        } else {
          diagnostics.add(
              error(
                  "SEMANTIC_UNKNOWN_TYPE",
                  "Unknown field type in message "
                      + parsedMessageType.name()
                      + ": "
                      + parsedField.typeName(),
                  sourcePath));
          continue;
        }

        resolvedFields.add(
            new ResolvedField(
                parsedField.name(),
                typeRef,
                parsedField.length(),
                parsedField.endian(),
                parsedField.fixed(),
                parsedField.comment()));
      }

      resolvedMessageTypes.add(
          new ResolvedMessageType(
              parsedMessageType.name(),
              parsedMessageType.comment(),
              effectiveNamespace,
              resolvedFields));
    }

    if (Diagnostics.hasErrors(diagnostics)) {
      throw new BmsException("Semantic validation failed.", diagnostics);
    }

    return new ResolvedSchema(parsedSchema.namespace(), resolvedMessageTypes);
  }

  private static void validateNamespace(
      String namespace, String attributeName, String sourcePath, List<Diagnostic> diagnostics) {
    if (namespace == null || namespace.isBlank()) {
      diagnostics.add(
          error("SEMANTIC_INVALID_NAMESPACE", attributeName + " must not be blank.", sourcePath));
      return;
    }
    if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_NAMESPACE",
              attributeName + " must be dot-delimited identifiers. Received: " + namespace,
              sourcePath));
    }
  }

  private static Diagnostic error(String code, String message, String sourcePath) {
    return new Diagnostic(DiagnosticSeverity.ERROR, code, message, sourcePath, -1, -1);
  }
}
