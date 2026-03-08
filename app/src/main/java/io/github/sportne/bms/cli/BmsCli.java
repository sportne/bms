package io.github.sportne.bms.cli;

import io.github.sportne.bms.BmsCompiler;
import io.github.sportne.bms.codegen.cpp.CppCodeGenerator;
import io.github.sportne.bms.codegen.java.JavaCodeGenerator;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.Diagnostics;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Command-line entry point for BMS.
 *
 * <p>This class reads command arguments, runs the compiler pipeline, and prints friendly messages.
 * It supports:
 *
 * <ul>
 *   <li>{@code validate}: check that a spec is valid
 *   <li>{@code generate}: validate + generate Java/C++ files
 * </ul>
 */
public final class BmsCli {
  private static final int EXIT_SUCCESS = 0;
  private static final int EXIT_SPEC_ERROR = 1;
  private static final int EXIT_USAGE_ERROR = 2;
  private static final int EXIT_INTERNAL_ERROR = 3;

  /**
   * Process entry point for command-line execution.
   *
   * @param args command-line arguments passed by the OS shell
   */
  public static void main(String[] args) {
    int exitCode = new BmsCli().run(args, System.out, System.err);
    if (exitCode != EXIT_SUCCESS) {
      System.exit(exitCode);
    }
  }

  /**
   * Runs the CLI without calling {@link System#exit(int)}.
   *
   * <p>This makes it easy to test CLI behavior in unit tests.
   *
   * @param args command-line arguments (for example {@code validate spec.xml})
   * @param out destination stream for normal output
   * @param err destination stream for error output
   * @return exit code that the process would use
   */
  public int run(String[] args, PrintStream out, PrintStream err) {
    Objects.requireNonNull(args, "args");
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(err, "err");

    if (args.length == 0) {
      printUsage(err);
      return EXIT_USAGE_ERROR;
    }

    try {
      return switch (args[0]) {
        case "validate" -> runValidate(args, out, err);
        case "generate" -> runGenerate(args, out, err);
        default -> {
          err.println("Unknown command: " + args[0]);
          printUsage(err);
          yield EXIT_USAGE_ERROR;
        }
      };
    } catch (BmsException exception) {
      printDiagnostics(err, exception);
      return EXIT_SPEC_ERROR;
    } catch (RuntimeException exception) {
      err.println("Internal error: " + exception.getMessage());
      return EXIT_INTERNAL_ERROR;
    }
  }

  /**
   * Handles the {@code validate} sub-command.
   *
   * @param args full CLI argument array
   * @param out destination stream for normal output
   * @param err destination stream for error output
   * @return process-style exit code
   * @throws BmsException if spec validation fails
   */
  private static int runValidate(String[] args, PrintStream out, PrintStream err)
      throws BmsException {
    if (args.length < 2) {
      err.println("Usage error: validate expects at least one spec path.");
      printUsage(err);
      return EXIT_USAGE_ERROR;
    }

    List<Path> specPaths = new ArrayList<>();
    for (int index = 1; index < args.length; index++) {
      String token = args[index];
      if (token.startsWith("--")) {
        err.println("Usage error: validate does not support options.");
        printUsage(err);
        return EXIT_USAGE_ERROR;
      }
      specPaths.add(Path.of(token));
    }

    BmsCompiler compiler = new BmsCompiler(BmsCompiler.defaultXsdPath());
    compiler.compile(specPaths);
    out.println("Validation succeeded: " + specPaths.get(0));
    return EXIT_SUCCESS;
  }

  /**
   * Handles the {@code generate} sub-command.
   *
   * @param args full CLI argument array
   * @param out destination stream for normal output
   * @param err destination stream for error output
   * @return process-style exit code
   * @throws BmsException if validation or generation fails
   */
  private static int runGenerate(String[] args, PrintStream out, PrintStream err)
      throws BmsException {
    if (args.length < 4) {
      err.println("Usage error: generate requires a spec path and at least one target output.");
      printUsage(err);
      return EXIT_USAGE_ERROR;
    }

    List<Path> specPaths = new ArrayList<>();
    int index = 1;
    while (index < args.length && !args[index].startsWith("--")) {
      specPaths.add(Path.of(args[index]));
      index++;
    }
    if (specPaths.isEmpty()) {
      err.println("Usage error: generate expects at least one spec path before options.");
      printUsage(err);
      return EXIT_USAGE_ERROR;
    }

    Path javaOutput = null;
    Path cppOutput = null;

    for (; index < args.length; index++) {
      String token = args[index];
      if ("--java".equals(token)) {
        if (index + 1 >= args.length) {
          err.println("Usage error: missing path after --java.");
          return EXIT_USAGE_ERROR;
        }
        javaOutput = Path.of(args[++index]);
      } else if ("--cpp".equals(token)) {
        if (index + 1 >= args.length) {
          err.println("Usage error: missing path after --cpp.");
          return EXIT_USAGE_ERROR;
        }
        cppOutput = Path.of(args[++index]);
      } else {
        err.println("Usage error: unknown option " + token);
        printUsage(err);
        return EXIT_USAGE_ERROR;
      }
    }

    if (javaOutput == null && cppOutput == null) {
      err.println("Usage error: generate needs --java and/or --cpp.");
      printUsage(err);
      return EXIT_USAGE_ERROR;
    }

    BmsCompiler compiler = new BmsCompiler(BmsCompiler.defaultXsdPath());
    ResolvedSchema resolvedSchema = compiler.compile(specPaths);

    if (javaOutput != null) {
      new JavaCodeGenerator().generate(resolvedSchema, javaOutput);
      out.println("Generated Java output: " + javaOutput);
    }
    if (cppOutput != null) {
      new CppCodeGenerator().generate(resolvedSchema, cppOutput);
      out.println("Generated C++ output: " + cppOutput);
    }
    return EXIT_SUCCESS;
  }

  /**
   * Prints an exception summary followed by each diagnostic line.
   *
   * @param err destination stream for error output
   * @param exception compiler exception that contains diagnostics
   */
  private static void printDiagnostics(PrintStream err, BmsException exception) {
    err.println(exception.getMessage());
    for (Diagnostic diagnostic : exception.diagnostics()) {
      err.println(Diagnostics.format(diagnostic));
    }
  }

  /**
   * Prints short command usage text.
   *
   * @param out destination stream for usage output
   */
  private static void printUsage(PrintStream out) {
    out.println("Usage:");
    out.println("  bms validate <spec.xml> [more-specs.xml ...]");
    out.println("  bms generate <spec.xml> [more-specs.xml ...] --java <outDir> [--cpp <outDir>]");
  }
}
