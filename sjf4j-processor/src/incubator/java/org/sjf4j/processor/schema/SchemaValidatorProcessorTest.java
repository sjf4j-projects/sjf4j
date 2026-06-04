package org.sjf4j.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.Sjf4jProcessor;
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
                "package testcase; import org.sjf4j.annotation.schema.*; @ValidJsonSchema(value=\"{\\\"$dynamicRef\\\":\\\"#x\\\"}\") class User {} @CompiledSchemaValidator interface Bad { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertFalse(compile(out, diagnostics, src.resolve("Bad.java")));
        assertTrue(diagnosticsToString(diagnostics).contains("requires runtime fallback"));
    }

    @Test
    public void missingRequiredPropertiesCompileToSingleFalseResult() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-missing-required-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Empty.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"a\\\",\\\"b\\\"]}\")\n" +
                        "public class Empty {}\n");
        write(src.resolve("EmptyValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface EmptyValidator { @ValidatorOptions(fallback=false) boolean ok(Empty e); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("Empty.java"), src.resolve("EmptyValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> emptyClass = Class.forName("testcase.Empty", true, loader);
        Class<?> validatorClass = Class.forName("testcase.EmptyValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        assertFalse((Boolean) validatorClass.getMethod("ok", emptyClass).invoke(validator, emptyClass.getConstructor().newInstance()));
    }

    @Test
    public void runtimeTypedRequiredPropertiesUseNodesFastPath() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-runtime-required-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Envelope.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.node.NodeProperty;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"items\\\":{\\\"type\\\":\\\"array\\\",\\\"items\\\":{\\\"oneOf\\\":[{\\\"$ref\\\":\\\"#/definitions/a\\\"},{\\\"$ref\\\":\\\"#/definitions/b\\\"}]}}},\\\"definitions\\\":{\\\"a\\\":{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"@type\\\",\\\"id\\\"],\\\"properties\\\":{\\\"@type\\\":{\\\"const\\\":\\\"poly-a\\\"},\\\"id\\\":{\\\"type\\\":\\\"string\\\"}}},\\\"b\\\":{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"@type\\\",\\\"num\\\"],\\\"properties\\\":{\\\"@type\\\":{\\\"const\\\":\\\"poly-b\\\"},\\\"num\\\":{\\\"type\\\":\\\"number\\\"}}}}}\")\n" +
                        "public class Envelope { public java.util.List<Base> items; public Envelope(java.util.List<Base> items) { this.items = items; } }\n");
        write(src.resolve("Base.java"),
                "package testcase; public interface Base {}\n");
        write(src.resolve("A.java"),
                "package testcase; import org.sjf4j.annotation.node.*; public final class A implements Base { private final String type = \"poly-a\"; public String id; @NodeCreator public A(@NodeProperty(\"id\") String id) { this.id = id; } @NodeProperty(\"@type\") public String getType() { return type; } }\n");
        write(src.resolve("B.java"),
                "package testcase; import org.sjf4j.annotation.node.*; public final class B implements Base { private final String type = \"poly-b\"; public double num; @NodeCreator public B(@NodeProperty(\"num\") double num) { this.num = num; } @NodeProperty(\"@type\") public String getType() { return type; } }\n");
        write(src.resolve("C.java"),
                "package testcase; import org.sjf4j.annotation.node.*; public final class C implements Base { private final String type = \"poly-a\"; @NodeCreator public C() {} @NodeProperty(\"@type\") public String getType() { return type; } }\n");
        write(src.resolve("EnvelopeValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface EnvelopeValidator { @ValidatorOptions(fallback=false) boolean ok(Envelope e); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("Envelope.java"), src.resolve("Base.java"),
                src.resolve("A.java"), src.resolve("B.java"), src.resolve("C.java"), src.resolve("EnvelopeValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> envelopeClass = Class.forName("testcase.Envelope", true, loader);
        Class<?> aClass = Class.forName("testcase.A", true, loader);
        Class<?> bClass = Class.forName("testcase.B", true, loader);
        Class<?> cClass = Class.forName("testcase.C", true, loader);
        Class<?> validatorClass = Class.forName("testcase.EnvelopeValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        java.lang.reflect.Constructor<?> envelopeCtor = envelopeClass.getConstructor(java.util.List.class);
        java.lang.reflect.Constructor<?> aCtor = aClass.getDeclaredConstructor(String.class);
        java.lang.reflect.Constructor<?> bCtor = bClass.getDeclaredConstructor(double.class);
        java.lang.reflect.Constructor<?> cCtor = cClass.getDeclaredConstructor();
        aCtor.setAccessible(true);
        bCtor.setAccessible(true);
        cCtor.setAccessible(true);

        assertTrue((Boolean) validatorClass.getMethod("ok", envelopeClass).invoke(validator,
                envelopeCtor.newInstance(java.util.Arrays.asList(aCtor.newInstance("id"), bCtor.newInstance(1.0d)))));
        assertFalse((Boolean) validatorClass.getMethod("ok", envelopeClass).invoke(validator,
                envelopeCtor.newInstance(java.util.Arrays.asList(cCtor.newInstance()))));
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
    public void fastPathCompilesDependentRequiredObjectAndArrayKeywords() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-more-keywords-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("User.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"minProperties\\\":3,\\\"maxProperties\\\":3,\\\"dependentRequired\\\":{\\\"card\\\":[\\\"billing\\\"]},\\\"properties\\\":{\\\"tags\\\":{\\\"type\\\":\\\"array\\\",\\\"uniqueItems\\\":true,\\\"contains\\\":{\\\"const\\\":\\\"required\\\"},\\\"minContains\\\":1,\\\"maxContains\\\":1}}}\")\n" +
                        "public class User { public String card; public String billing; public java.util.List<String> tags; public User(String card, String billing, java.util.List<String> tags) { this.card = card; this.billing = billing; this.tags = tags; } }\n");
        write(src.resolve("UserValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface UserValidator { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("User.java"), src.resolve("UserValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> validatorClass = Class.forName("testcase.UserValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        java.lang.reflect.Constructor<?> ctor = userClass.getConstructor(String.class, String.class, java.util.List.class);

        assertTrue((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator,
                ctor.newInstance("c", "b", java.util.Arrays.asList("required", "other"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator,
                ctor.newInstance("c", "b", java.util.Arrays.asList("other"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator,
                ctor.newInstance("c", "b", java.util.Arrays.asList("required", "required"))));
    }

    @Test
    public void localFallbackCompilesPrefixPropertyNamesPatternAdditionalAndRuntimeItems() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-local-fallback-keywords-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Box.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{"
                        + "\\\"values\\\":{\\\"type\\\":\\\"array\\\",\\\"prefixItems\\\":[{\\\"type\\\":\\\"integer\\\"},{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}],\\\"items\\\":{\\\"type\\\":\\\"number\\\"}},"
                        + "\\\"names\\\":{\\\"type\\\":\\\"object\\\",\\\"propertyNames\\\":{\\\"pattern\\\":\\\"^[A-Z]\\\"}},"
                        + "\\\"data\\\":{\\\"type\\\":\\\"object\\\",\\\"patternProperties\\\":{\\\"^s_\\\":{\\\"type\\\":\\\"string\\\"}},\\\"additionalProperties\\\":{\\\"type\\\":\\\"integer\\\"}},"
                        + "\\\"runtime\\\":{\\\"type\\\":\\\"array\\\",\\\"items\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2},\\\"contains\\\":{\\\"const\\\":\\\"target\\\"}}"
                        + "}}\")\n" +
                        "public class Box { public java.util.List<Object> values; public java.util.Map<String,Object> names; public java.util.Map<String,Object> data; public Object runtime; public Box(java.util.List<Object> values, java.util.Map<String,Object> names, java.util.Map<String,Object> data, Object runtime) { this.values = values; this.names = names; this.data = data; this.runtime = runtime; } }\n");
        write(src.resolve("BoxValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface BoxValidator { @ValidatorOptions(fallback=false) boolean ok(Box b); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("Box.java"), src.resolve("BoxValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> boxClass = Class.forName("testcase.Box", true, loader);
        Class<?> validatorClass = Class.forName("testcase.BoxValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        java.lang.reflect.Constructor<?> ctor = boxClass.getConstructor(java.util.List.class, java.util.Map.class, java.util.Map.class, Object.class);

        java.util.Map<String, Object> names = new java.util.LinkedHashMap<String, Object>();
        names.put("Name", Integer.valueOf(1));
        java.util.Map<String, Object> badNames = new java.util.LinkedHashMap<String, Object>();
        badNames.put("name", Integer.valueOf(1));
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<String, Object>();
        data.put("s_name", "ok");
        data.put("count", Integer.valueOf(1));
        java.util.Map<String, Object> badData = new java.util.LinkedHashMap<String, Object>();
        badData.put("s_name", Integer.valueOf(1));

        assertTrue((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(java.util.Arrays.asList(Integer.valueOf(1), "ok", Double.valueOf(2.5d)), names, data, java.util.Arrays.asList("target", "ok"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(java.util.Arrays.asList("bad", "ok", Double.valueOf(2.5d)), names, data, java.util.Arrays.asList("target", "ok"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(java.util.Arrays.asList(Integer.valueOf(1), "ok", Double.valueOf(2.5d)), badNames, data, java.util.Arrays.asList("target", "ok"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(java.util.Arrays.asList(Integer.valueOf(1), "ok", Double.valueOf(2.5d)), names, badData, java.util.Arrays.asList("target", "ok"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(java.util.Arrays.asList(Integer.valueOf(1), "ok", Double.valueOf(2.5d)), names, data, java.util.Arrays.asList("target", "x"))));
    }

    @Test
    public void localFallbackCompilesRemainingRuntimeEvaluatorKeywords() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-local-fallback-more-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Box.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{"
                        + "\\\"ratio\\\":{\\\"type\\\":\\\"number\\\",\\\"multipleOf\\\":0.5},"
                        + "\\\"payload\\\":{\\\"type\\\":\\\"string\\\"},"
                        + "\\\"complex\\\":{\\\"enum\\\":[{\\\"a\\\":1}]},"
                        + "\\\"deps\\\":{\\\"type\\\":\\\"object\\\",\\\"dependencies\\\":{\\\"card\\\":[\\\"billing\\\"]}},"
                        + "\\\"ds\\\":{\\\"type\\\":\\\"object\\\",\\\"dependentSchemas\\\":{\\\"flag\\\":{\\\"required\\\":[\\\"extra\\\"]}}},"
                        + "\\\"strict\\\":{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"known\\\":{\\\"type\\\":\\\"string\\\"}},\\\"unevaluatedProperties\\\":false}"
                        + "}}\")\n" +
                        "public class Box { public Number ratio; public String payload; public Object complex; public java.util.Map<String,Object> deps; public java.util.Map<String,Object> ds; public java.util.Map<String,Object> strict; public Box(Number ratio, String payload, Object complex, java.util.Map<String,Object> deps, java.util.Map<String,Object> ds, java.util.Map<String,Object> strict) { this.ratio = ratio; this.payload = payload; this.complex = complex; this.deps = deps; this.ds = ds; this.strict = strict; } }\n");
        write(src.resolve("BoxValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface BoxValidator { @ValidatorOptions(fallback=false) boolean ok(Box b); }\n");
        write(src.resolve("ContentBox.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"$schema\\\":\\\"http://json-schema.org/draft-07/schema#\\\",\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"payload\\\":{\\\"type\\\":\\\"string\\\",\\\"contentEncoding\\\":\\\"base64\\\",\\\"contentMediaType\\\":\\\"application/json\\\"}}}\")\n" +
                        "public class ContentBox { public String payload; public ContentBox(String payload) { this.payload = payload; } }\n");
        write(src.resolve("ContentBoxValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface ContentBoxValidator { @ValidatorOptions(fallback=false) boolean ok(ContentBox b); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("Box.java"), src.resolve("BoxValidator.java"),
                src.resolve("ContentBox.java"), src.resolve("ContentBoxValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> boxClass = Class.forName("testcase.Box", true, loader);
        Class<?> validatorClass = Class.forName("testcase.BoxValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        java.lang.reflect.Constructor<?> ctor = boxClass.getConstructor(Number.class, String.class, Object.class, java.util.Map.class, java.util.Map.class, java.util.Map.class);
        Class<?> contentBoxClass = Class.forName("testcase.ContentBox", true, loader);
        Class<?> contentValidatorClass = Class.forName("testcase.ContentBoxValidator_Impl", true, loader);
        Object contentValidator = contentValidatorClass.getField("INSTANCE").get(null);
        java.lang.reflect.Constructor<?> contentCtor = contentBoxClass.getConstructor(String.class);

        java.util.Map<String, Object> complex = new java.util.LinkedHashMap<String, Object>();
        complex.put("a", Integer.valueOf(1));
        java.util.Map<String, Object> deps = new java.util.LinkedHashMap<String, Object>();
        deps.put("card", "c");
        deps.put("billing", "b");
        java.util.Map<String, Object> badDeps = new java.util.LinkedHashMap<String, Object>();
        badDeps.put("card", "c");
        java.util.Map<String, Object> ds = new java.util.LinkedHashMap<String, Object>();
        ds.put("flag", Boolean.TRUE);
        ds.put("extra", "x");
        java.util.Map<String, Object> badDs = new java.util.LinkedHashMap<String, Object>();
        badDs.put("flag", Boolean.TRUE);
        java.util.Map<String, Object> strict = new java.util.LinkedHashMap<String, Object>();
        strict.put("known", "ok");
        java.util.Map<String, Object> badStrict = new java.util.LinkedHashMap<String, Object>();
        badStrict.put("known", "ok");
        badStrict.put("extra", "nope");

        assertTrue((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(Double.valueOf(1.5d), "eyJhIjoxfQ==", complex, deps, ds, strict)));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(Double.valueOf(1.2d), "eyJhIjoxfQ==", complex, deps, ds, strict)));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(Double.valueOf(1.5d), "eyJhIjoxfQ==", java.util.Collections.singletonMap("a", Integer.valueOf(2)), deps, ds, strict)));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(Double.valueOf(1.5d), "eyJhIjoxfQ==", complex, badDeps, ds, strict)));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(Double.valueOf(1.5d), "eyJhIjoxfQ==", complex, deps, badDs, strict)));
        assertFalse((Boolean) validatorClass.getMethod("ok", boxClass).invoke(validator,
                ctor.newInstance(Double.valueOf(1.5d), "eyJhIjoxfQ==", complex, deps, ds, badStrict)));
        assertTrue((Boolean) contentValidatorClass.getMethod("ok", contentBoxClass).invoke(contentValidator,
                contentCtor.newInstance("eyJhIjoxfQ==")));
        assertFalse((Boolean) contentValidatorClass.getMethod("ok", contentBoxClass).invoke(contentValidator,
                contentCtor.newInstance("$$$")));
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
    public void fastPathCompilesDependentRequiredWithFallbackFalse() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-dependent-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("User.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"dependentRequired\\\":{\\\"card\\\":[\\\"billing\\\"]}}\") public class User { public String card; public User(String card) { this.card = card; } }\n");
        write(src.resolve("UserValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface UserValidator { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("User.java"), src.resolve("UserValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> validatorClass = Class.forName("testcase.UserValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator, userClass.getConstructor(String.class).newInstance("c")));
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

    @Test
    public void fastPathValidatesArraysEnumsCharsAndNestedObjects() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-composite-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Composite.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.*;\n" +
                        "enum Status { ACTIVE, PAUSED, DISABLED }\n" +
                        "class Address { public String city; Address(String city) { this.city = city; } }\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"status\\\",\\\"initial\\\",\\\"tags\\\",\\\"address\\\"],\\\"properties\\\":{\\\"status\\\":{\\\"enum\\\":[\\\"ACTIVE\\\",\\\"PAUSED\\\"]},\\\"initial\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":1,\\\"maxLength\\\":1},\\\"tags\\\":{\\\"type\\\":\\\"array\\\",\\\"minItems\\\":1,\\\"items\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}},\\\"address\\\":{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"city\\\"],\\\"properties\\\":{\\\"city\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}},\\\"additionalProperties\\\":false}},\\\"additionalProperties\\\":false}\")\n" +
                        "class User { public Status status; public char initial; public String[] tags; public Address address; User(Status status, char initial, String[] tags, Address address) { this.status = status; this.initial = initial; this.tags = tags; this.address = address; } }\n" +
                        "@CompiledSchemaValidator interface UserValidator { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("Composite.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> statusClass = Class.forName("testcase.Status", true, loader);
        Class<?> addressClass = Class.forName("testcase.Address", true, loader);
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> validatorClass = Class.forName("testcase.UserValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        java.lang.reflect.Constructor<?> addressCtor = addressClass.getDeclaredConstructor(String.class);
        java.lang.reflect.Constructor<?> userCtor = userClass.getDeclaredConstructor(statusClass, char.class, String[].class, addressClass);
        addressCtor.setAccessible(true);
        userCtor.setAccessible(true);
        java.lang.reflect.Field activeField = statusClass.getField("ACTIVE");
        java.lang.reflect.Field disabledField = statusClass.getField("DISABLED");
        activeField.setAccessible(true);
        disabledField.setAccessible(true);
        Object active = activeField.get(null);
        Object disabled = disabledField.get(null);

        assertTrue((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator,
                userCtor.newInstance(active, 'A', new String[]{"ok"}, addressCtor.newInstance("Paris"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator,
                userCtor.newInstance(disabled, 'A', new String[]{"ok"}, addressCtor.newInstance("Paris"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator,
                userCtor.newInstance(active, 'A', new String[]{"x"}, addressCtor.newInstance("Paris"))));
        assertFalse((Boolean) validatorClass.getMethod("ok", userClass).invoke(validator,
                userCtor.newInstance(active, 'A', new String[]{"ok"}, addressCtor.newInstance("X"))));
    }

    @Test
    public void fastPathCompilesLocalRefsInOneOfArrayItems() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-ref-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Envelope.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"items\\\":{\\\"type\\\":\\\"array\\\",\\\"items\\\":{\\\"oneOf\\\":[{\\\"$ref\\\":\\\"#/definitions/a\\\"},{\\\"$ref\\\":\\\"#/definitions/b\\\"}]}}},\\\"definitions\\\":{\\\"a\\\":{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"kind\\\",\\\"value\\\"],\\\"properties\\\":{\\\"kind\\\":{\\\"const\\\":\\\"A\\\"},\\\"value\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}}},\\\"b\\\":{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"kind\\\",\\\"value\\\"],\\\"properties\\\":{\\\"kind\\\":{\\\"const\\\":\\\"B\\\"},\\\"value\\\":{\\\"type\\\":\\\"number\\\",\\\"minimum\\\":1}}}}}\")\n" +
                        "public class Envelope { public java.util.List<Entry> items; public Envelope(java.util.List<Entry> items) { this.items = items; } }\n" +
                        "class Entry { public String kind; public Object value; Entry(String kind, Object value) { this.kind = kind; this.value = value; } }\n");
        write(src.resolve("EnvelopeValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface EnvelopeValidator { @ValidatorOptions(fallback=false) boolean ok(Envelope e); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("Envelope.java"), src.resolve("EnvelopeValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> envelopeClass = Class.forName("testcase.Envelope", true, loader);
        Class<?> entryClass = Class.forName("testcase.Entry", true, loader);
        Class<?> validatorClass = Class.forName("testcase.EnvelopeValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        java.lang.reflect.Constructor<?> envelopeCtor = envelopeClass.getConstructor(java.util.List.class);
        java.lang.reflect.Constructor<?> entryCtor = entryClass.getDeclaredConstructor(String.class, Object.class);
        entryCtor.setAccessible(true);

        assertTrue((Boolean) validatorClass.getMethod("ok", envelopeClass).invoke(validator,
                envelopeCtor.newInstance(java.util.Arrays.asList(entryCtor.newInstance("A", "ok"), entryCtor.newInstance("B", Integer.valueOf(2))))));
        assertFalse((Boolean) validatorClass.getMethod("ok", envelopeClass).invoke(validator,
                envelopeCtor.newInstance(java.util.Arrays.asList(entryCtor.newInstance("C", "ok")))));
        assertFalse((Boolean) validatorClass.getMethod("ok", envelopeClass).invoke(validator,
                envelopeCtor.newInstance(java.util.Arrays.asList(entryCtor.newInstance("A", "x")))));
    }

    @Test
    public void rejectRecursiveRefWithFallbackFalse() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-recursive-ref-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Bad.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @ValidJsonSchema(value=\"{\\\"$ref\\\":\\\"#\\\"}\") class User {} @CompiledSchemaValidator interface Bad { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertFalse(compile(out, diagnostics, src.resolve("Bad.java")));
        String text = diagnosticsToString(diagnostics);
        assertTrue(text.contains("recursive/cyclic $ref") || text.contains("requires runtime fallback"), text);
    }

    @Test
    public void fastPathCompilesFormatWithStrictOption() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-format-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("User.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"id\\\":{\\\"type\\\":\\\"string\\\",\\\"format\\\":\\\"uuid\\\"}}}\")\n" +
                        "public class User { public String id; public User(String id) { this.id = id; } }\n");
        write(src.resolve("UserValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface UserValidator { @ValidatorOptions(fallback=false) boolean strict(User u); @ValidatorOptions(fallback=false, strictFormat=false) boolean lenient(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics, src.resolve("User.java"), src.resolve("UserValidator.java")), diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> validatorClass = Class.forName("testcase.UserValidator_Impl", true, loader);
        Object validator = validatorClass.getField("INSTANCE").get(null);
        Object good = userClass.getConstructor(String.class).newInstance("123e4567-e89b-12d3-a456-426614174000");
        Object bad = userClass.getConstructor(String.class).newInstance("not-a-uuid");

        assertTrue((Boolean) validatorClass.getMethod("strict", userClass).invoke(validator, good));
        assertFalse((Boolean) validatorClass.getMethod("strict", userClass).invoke(validator, bad));
        assertTrue((Boolean) validatorClass.getMethod("lenient", userClass).invoke(validator, bad));
    }

    @Test
    public void thirdPartyPropertyNamesDrivePojoSchemaValidation() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-schema-third-party-name-test");
        Path src = dir.resolve("src");
        Path out = dir.resolve("classes");
        Files.createDirectories(src.resolve("testcase"));
        Files.createDirectories(src.resolve("com/fasterxml/jackson/annotation"));
        Files.createDirectories(out);
        write(src.resolve("com/fasterxml/jackson/annotation/JsonProperty.java"),
                "package com.fasterxml.jackson.annotation; public @interface JsonProperty { String value() default \"\"; String[] alias() default {}; }\n");
        write(src.resolve("testcase/User.java"),
                "package testcase;\n" +
                        "import com.fasterxml.jackson.annotation.JsonProperty;\n" +
                        "import org.sjf4j.annotation.schema.ValidJsonSchema;\n" +
                        "@ValidJsonSchema(value=\"{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"first_name\\\"],\\\"properties\\\":{\\\"first_name\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}}}\")\n" +
                        "public class User { @JsonProperty(\"first_name\") public String firstName; public User(String firstName) { this.firstName = firstName; } }\n");
        write(src.resolve("testcase/UserValidator.java"),
                "package testcase; import org.sjf4j.annotation.schema.*; @CompiledSchemaValidator interface UserValidator { @ValidatorOptions(fallback=false) boolean ok(User u); }\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        assertTrue(compile(out, diagnostics,
                src.resolve("com/fasterxml/jackson/annotation/JsonProperty.java"),
                src.resolve("testcase/User.java"),
                src.resolve("testcase/UserValidator.java")), diagnosticsToString(diagnostics));

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
