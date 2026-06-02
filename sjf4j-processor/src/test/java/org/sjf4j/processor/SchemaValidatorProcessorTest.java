package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.SchemaException;
import org.sjf4j.schema.ValidationResult;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SchemaValidatorProcessorTest {
    @Test
    public void generateSchemaValidatorMethods() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("User.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"name\\\"],\\\"properties\\\":{\\\"name\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}}}\")\n" +
                        "public class User { public String name; public User() {} public User(String name) { this.name = name; } }\n");
        write(src.resolve("UserValidator.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.*;\n" +
                        "import org.sjf4j.schema.ValidationResult;\n" +
                        "@CompiledSchemaValidator public interface UserValidator {\n" +
                        "  boolean ok(User u);\n" +
                        "  void require(User u);\n" +
                        "  ValidationResult result(User u);\n" +
                        "  @ValidatorOptions(fallback=false) boolean fast(User u);\n" +
                        "}\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("User.java"), src.resolve("UserValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> validatorClass = Class.forName("testcase.UserValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        Object good = userClass.getConstructor(String.class).newInstance("Ada");
        Object bad = userClass.getConstructor(String.class).newInstance("A");

        assertTrue((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, good));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, bad));
        assertTrue((Boolean) validatorClass.getMethod("fast", userClass).invoke(validator, good));
        assertFalse((Boolean) validatorClass.getMethod("fast", userClass).invoke(validator, bad));

        validatorClass.getMethod("require", userClass).invoke(validator, good);
        try {
            validatorClass.getMethod("require", userClass).invoke(validator, bad);
            fail("expected SchemaException");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof SchemaException);
            assertTrue(e.getCause().getMessage().contains("schema validation failed"));
        }

        ValidationResult result = (ValidationResult) validatorClass.getMethod("result", userClass).invoke(validator, bad);
        assertFalse(result.isValid());
        assertFalse(result.getMessages().isEmpty());
    }

    @Test
    public void rejectMissingSchemaAnnotation() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-missing-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Bad.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; class User {} @CompiledSchemaValidator interface Bad { boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertFalse(compile(out, diagnostics, src.resolve("Bad.java")));
        assertTrue(diagnosticsToString(diagnostics).contains("must declare @ValidJsonSchema"));
    }

    @Test
    public void rejectFallbackFalseUnsupportedSchema() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-fallback-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Bad.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"unevaluatedProperties\\\":false}\") class User {} @CompiledSchemaValidator interface Bad { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertFalse(compile(out, diagnostics, src.resolve("Bad.java")));
        assertTrue(diagnosticsToString(diagnostics).contains("requires runtime fallback"));
    }

    @Test
    public void fastItemsSkipNullListAndValidateElements() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-items-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Group.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"tags\\\":{\\\"items\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}}}}\")\n" +
                        "public class Group { public List<String> tags; public Group() {} public Group(List<String> tags) { this.tags = tags; } }\n");
        write(src.resolve("GroupValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface GroupValidator { @ValidatorOptions(fallback=false) boolean ok(Group g); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("Group.java"), src.resolve("GroupValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> groupClass = Class.forName("testcase.Group", true, loader);
        Class<?> validatorClass = Class.forName("testcase.GroupValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        assertTrue((Boolean) validatorClass.getMethod("ok", groupClass).invoke(validator, groupClass.getConstructor(java.util.List.class).newInstance(new Object[]{null})));
        assertTrue((Boolean) validatorClass.getMethod("ok", groupClass).invoke(validator, groupClass.getConstructor(java.util.List.class).newInstance(java.util.Collections.emptyList())));
        assertTrue((Boolean) validatorClass.getMethod("ok", groupClass).invoke(validator, groupClass.getConstructor(java.util.List.class).newInstance(java.util.Arrays.asList("ok"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", groupClass).invoke(validator, groupClass.getConstructor(java.util.List.class).newInstance(java.util.Arrays.asList("x"))));
    }

    @Test
    public void fallbackTrueAllowsUnsupportedSchemaAndUsesRuntimeValidation() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-fallback-runtime-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("User.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"tags\\\":{\\\"contains\\\":{\\\"const\\\":\\\"required\\\"},\\\"minContains\\\":1}}}\")\n" +
                        "public class User { public List<String> tags; public User() {} public User(List<String> tags) { this.tags = tags; } }\n");
        write(src.resolve("UserValidator.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.*;\n" +
                        "import org.sjf4j.schema.ValidationResult;\n" +
                        "@CompiledSchemaValidator interface UserValidator { boolean ok(User u); void require(User u); ValidationResult result(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("User.java"), src.resolve("UserValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> validatorClass = Class.forName("testcase.UserValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        Object good = userClass.getConstructor(java.util.List.class).newInstance(java.util.Arrays.asList("required"));
        Object bad = userClass.getConstructor(java.util.List.class).newInstance(java.util.Arrays.asList("other"));

        assertTrue((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, good));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, bad));
        try {
            validatorClass.getMethod("require", userClass).invoke(validator, bad);
            fail("expected fallback SchemaException");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof SchemaException);
        }
        ValidationResult result = (ValidationResult) validatorClass.getMethod("result", userClass).invoke(validator, bad);
        assertFalse(result.isValid());
        assertFalse(result.getMessages().isEmpty());
    }

    @Test
    public void inheritSchemaAnnotationFromSuperclass() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-parent-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("User.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"name\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}}}\") class Base { public String name; }\n" +
                        "public class User extends Base { public User() {} public User(String name) { this.name = name; } }\n");
        write(src.resolve("UserValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface UserValidator { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("User.java"), src.resolve("UserValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> validatorClass = Class.forName("testcase.UserValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        assertTrue((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, userClass.getConstructor(String.class).newInstance("Ada")));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, userClass.getConstructor(String.class).newInstance("A")));
    }

    @Test
    public void rejectDependentRequiredWithFallbackFalse() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-dependent-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Bad.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"dependentRequired\\\":{\\\"card\\\":[\\\"billing\\\"]}}\") class User { public String card; public String billing; } @CompiledSchemaValidator interface Bad { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertFalse(compile(out, diagnostics, src.resolve("Bad.java")));
        assertTrue(diagnosticsToString(diagnostics).contains("dependentRequired"));
    }

    @Test
    public void rejectValidationResultWithFallbackFalse() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-result-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Bad.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; import org.sjf4j.schema.ValidationResult; @ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\"}\") class User {} @CompiledSchemaValidator interface Bad { @ValidatorOptions(fallback=false) ValidationResult validate(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertFalse(compile(out, diagnostics, src.resolve("Bad.java")));
        assertTrue(diagnosticsToString(diagnostics).contains("ValidationResult methods require runtime fallback diagnostics"));
    }

    @Test
    public void rejectInvalidMethodShapesAndReturnTypes() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-shape-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Bad.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\"}\") class User {}\n" +
                        "@CompiledSchemaValidator interface NoParams { boolean ok(); }\n" +
                        "@CompiledSchemaValidator interface MultiParams { boolean ok(User u, User v); }\n" +
                        "@CompiledSchemaValidator interface PrimitiveParam { boolean ok(int u); }\n" +
                        "@CompiledSchemaValidator interface BadReturn { String ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertFalse(compile(out, diagnostics, src.resolve("Bad.java")));
        String text = diagnosticsToString(diagnostics);
        assertTrue(text.contains("must have exactly one parameter"), text);
        assertTrue(text.contains("parameter must be a declared POJO type"), text);
        assertTrue(text.contains("must return boolean, void, or org.sjf4j.schema.ValidationResult"), text);
    }

    @Test
    public void recordReadsUseComponentsOnlyForAdditionalProperties() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-record-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("User.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"name\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}},\\\"additionalProperties\\\":false}\")\n" +
                        "public record User(String name) {}\n");
        write(src.resolve("UserValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface UserValidator { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("User.java"), src.resolve("UserValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> validatorClass = Class.forName("testcase.UserValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        assertTrue((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, userClass.getConstructor(String.class).newInstance("Ada")));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, userClass.getConstructor(String.class).newInstance("A")));
    }

    private static boolean compile(Path out, DiagnosticCollector<JavaFileObject> diagnostics, Path... sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(toFiles(sources)))).call();
        return Boolean.TRUE.equals(ok);
    }

    private static File[] toFiles(Path[] paths) {
        File[] files = new File[paths.length];
        for (int i = 0; i < paths.length; i++) files[i] = paths[i].toFile();
        return files;
    }

    private static void write(Path path, String text) throws Exception {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write(text);
        }
    }

    private static String diagnosticsToString(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            sb.append(diagnostic.getMessage(null)).append('\n');
        }
        return sb.toString();
    }
}
