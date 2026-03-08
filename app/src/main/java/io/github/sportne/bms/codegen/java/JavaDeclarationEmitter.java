package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.toPascalCase;

import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import java.util.ArrayList;
import java.util.List;

/**
 * Emits Java field declarations for resolved message members.
 *
 * <p>This helper owns declaration flattening rules for nested and conditional blocks.
 */
final class JavaDeclarationEmitter {
  /** Prevents instantiation of this static utility class. */
  private JavaDeclarationEmitter() {}

  /**
   * Appends field declarations for message members.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  static void appendMemberDeclarations(
      StringBuilder builder,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext) {
    List<DeclarationInfo> declarations = new ArrayList<>();
    collectMemberDeclarationsForMembers(declarations, messageType.members(), generationContext);
    appendMemberFieldDeclarations(builder, declarations);
    appendMemberAccessors(builder, declarations);
    builder.append("\n");
  }

  /**
   * Collects field declarations for one member list recursively.
   *
   * @param declarations destination declaration list
   * @param members members to render
   * @param generationContext reusable lookup maps
   */
  private static void collectMemberDeclarationsForMembers(
      List<DeclarationInfo> declarations,
      List<ResolvedMessageMember> members,
      JavaCodeGenerator.GenerationContext generationContext) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        collectMemberDeclarationsForMembers(
            declarations, resolvedIfBlock.members(), generationContext);
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        collectMemberDeclarationsForMembers(
            declarations, resolvedNestedType.members(), generationContext);
        continue;
      }
      if (!isDeclarableMember(member)) {
        continue;
      }
      declarations.add(
          new DeclarationInfo(
              JavaTypeRenderer.javaTypeForMember(member, generationContext), memberName(member)));
    }
  }

  /**
   * Appends private field declarations with deterministic default initializers.
   *
   * @param builder destination source builder
   * @param declarations declarations to render
   */
  private static void appendMemberFieldDeclarations(
      StringBuilder builder, List<DeclarationInfo> declarations) {
    for (DeclarationInfo declaration : declarations) {
      builder
          .append("  private ")
          .append(declaration.javaType())
          .append(' ')
          .append(declaration.memberName());
      String initializer = fieldInitializer(declaration.javaType());
      if (!initializer.isEmpty()) {
        builder.append(" = ").append(initializer);
      }
      builder.append(";\n");
    }
    builder.append("\n");
  }

  /**
   * Appends getter and setter methods for each declared field.
   *
   * @param builder destination source builder
   * @param declarations declarations to render
   */
  private static void appendMemberAccessors(
      StringBuilder builder, List<DeclarationInfo> declarations) {
    for (DeclarationInfo declaration : declarations) {
      String javaType = declaration.javaType();
      String memberName = declaration.memberName();
      String pascalName = toPascalCase(memberName);

      builder
          .append("  public ")
          .append(javaType)
          .append(" get")
          .append(pascalName)
          .append("() {\n");
      if (isPrimitiveArrayType(javaType)) {
        builder.append("    return this.").append(memberName).append(".clone();\n");
      } else if (isObjectArrayType(javaType)) {
        String componentType = arrayComponentType(javaType);
        builder
            .append("    ")
            .append(componentType)
            .append("[] copy = new ")
            .append(componentType)
            .append("[this.")
            .append(memberName)
            .append(".length];\n")
            .append("    for (int index = 0; index < this.")
            .append(memberName)
            .append(".length; index++) {\n")
            .append("      copy[index] = ")
            .append(componentType)
            .append(".decode(this.")
            .append(memberName)
            .append("[index].encode());\n")
            .append("    }\n")
            .append("    return copy;\n");
      } else if (isMessageObjectType(javaType)) {
        builder
            .append("    return ")
            .append(javaType)
            .append(".decode(this.")
            .append(memberName)
            .append(".encode());\n");
      } else {
        builder.append("    return this.").append(memberName).append(";\n");
      }
      builder.append("  }\n\n");

      builder
          .append("  public void set")
          .append(pascalName)
          .append("(")
          .append(javaType)
          .append(" value) {\n");
      if (isPrimitiveArrayType(javaType)) {
        builder
            .append("    this.")
            .append(memberName)
            .append(" = Objects.requireNonNull(value, \"")
            .append(memberName)
            .append("\").clone();\n");
      } else if (isObjectArrayType(javaType)) {
        String componentType = arrayComponentType(javaType);
        builder
            .append("    ")
            .append(componentType)
            .append("[] source = Objects.requireNonNull(value, \"")
            .append(memberName)
            .append("\");\n")
            .append("    ")
            .append(componentType)
            .append("[] copy = new ")
            .append(componentType)
            .append("[source.length];\n")
            .append("    for (int index = 0; index < source.length; index++) {\n")
            .append("      ")
            .append(componentType)
            .append(" element = Objects.requireNonNull(source[index], \"")
            .append(memberName)
            .append("[\" + index + \"]\");\n")
            .append("      copy[index] = ")
            .append(componentType)
            .append(".decode(element.encode());\n")
            .append("    }\n")
            .append("    this.")
            .append(memberName)
            .append(" = copy;\n");
      } else if ("String".equals(javaType)) {
        builder
            .append("    this.")
            .append(memberName)
            .append(" = Objects.requireNonNull(value, \"")
            .append(memberName)
            .append("\");\n");
      } else if (isMessageObjectType(javaType)) {
        builder
            .append("    ")
            .append(javaType)
            .append(" source = Objects.requireNonNull(value, \"")
            .append(memberName)
            .append("\");\n")
            .append("    this.")
            .append(memberName)
            .append(" = ")
            .append(javaType)
            .append(".decode(source.encode());\n");
      } else {
        builder.append("    this.").append(memberName).append(" = value;\n");
      }
      builder.append("  }\n\n");
    }
  }

  /**
   * Returns the default field initializer for one generated Java type.
   *
   * @param javaType generated Java type
   * @return initializer expression or empty string
   */
  private static String fieldInitializer(String javaType) {
    if ("String".equals(javaType)) {
      return "\"\"";
    }
    if (isArrayType(javaType)) {
      return "new " + arrayComponentType(javaType) + "[0]";
    }
    if (isMessageObjectType(javaType)) {
      return "new " + javaType + "()";
    }
    return "";
  }

  /**
   * Returns whether one generated Java type is an array type.
   *
   * @param javaType generated Java type
   * @return {@code true} when the type ends with {@code []}
   */
  private static boolean isArrayType(String javaType) {
    return javaType.endsWith("[]");
  }

  /**
   * Returns whether one generated Java type is an array of primitive values.
   *
   * @param javaType generated Java type
   * @return {@code true} when the type is one primitive array
   */
  private static boolean isPrimitiveArrayType(String javaType) {
    return isArrayType(javaType) && isPrimitiveType(arrayComponentType(javaType));
  }

  /**
   * Returns whether one generated Java type is an array of message/object values.
   *
   * @param javaType generated Java type
   * @return {@code true} when the type is one object array
   */
  private static boolean isObjectArrayType(String javaType) {
    return isArrayType(javaType) && !isPrimitiveType(arrayComponentType(javaType));
  }

  /**
   * Returns whether one generated Java type maps to one nested generated message object.
   *
   * @param javaType generated Java type
   * @return {@code true} when the type is a non-array, non-string, non-primitive object
   */
  private static boolean isMessageObjectType(String javaType) {
    return !isArrayType(javaType) && !isPrimitiveType(javaType) && !"String".equals(javaType);
  }

  /**
   * Returns whether one generated Java type name is primitive.
   *
   * @param javaType generated Java type
   * @return {@code true} when one primitive declaration type
   */
  private static boolean isPrimitiveType(String javaType) {
    return "byte".equals(javaType)
        || "short".equals(javaType)
        || "int".equals(javaType)
        || "long".equals(javaType)
        || "float".equals(javaType)
        || "double".equals(javaType)
        || "char".equals(javaType)
        || "boolean".equals(javaType);
  }

  /**
   * Returns the component type for one array declaration type.
   *
   * @param javaType generated Java array type
   * @return component type name
   */
  private static String arrayComponentType(String javaType) {
    return javaType.substring(0, javaType.length() - 2);
  }

  /**
   * Returns whether one member kind produces a generated Java field declaration.
   *
   * @param member member to inspect
   * @return {@code true} when the member is emitted as a Java field
   */
  private static boolean isDeclarableMember(ResolvedMessageMember member) {
    return member instanceof ResolvedField
        || member instanceof ResolvedBitField
        || member instanceof ResolvedFloat
        || member instanceof ResolvedScaledInt
        || member instanceof ResolvedArray
        || member instanceof ResolvedVector
        || member instanceof ResolvedBlobArray
        || member instanceof ResolvedBlobVector
        || member instanceof ResolvedVarString;
  }

  /**
   * Resolves one member name.
   *
   * @param member member to inspect
   * @return member name
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
    if (member instanceof ResolvedVarString resolvedVarString) {
      return resolvedVarString.name();
    }
    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }

  /**
   * Field declaration metadata for generated member accessors.
   *
   * @param javaType generated Java type
   * @param memberName member name
   */
  private record DeclarationInfo(String javaType, String memberName) {
    /** Creates one declaration descriptor. */
    private DeclarationInfo {}
  }
}
