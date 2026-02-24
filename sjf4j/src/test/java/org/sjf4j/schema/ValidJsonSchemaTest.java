package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidJsonSchemaTest {

    private static final String SIMPLE_USER_SCHEMA = "{" +
            "\"type\":\"object\"," +
            "\"required\":[\"id\",\"name\"]," +
            "\"properties\":{\"id\":{\"type\":\"integer\",\"minimum\":1}," +
            "\"name\":{\"type\":\"string\",\"minLength\":1}}," +
            "\"additionalProperties\":false" +
            "}";

    @ValidJsonSchema(value = SIMPLE_USER_SCHEMA)
    public static class InlineUser {
        public int id;
        public String name;
    }

    @ValidJsonSchema(ref = "user.schema.json")
    public static class ResourceUser {
        public int id;
        public String name;
    }

    @ValidJsonSchema
    public static class ConventionUser {
        public String code;
    }

    @ValidJsonSchema(ref = "domain.schema.json#User", strictFormat = true)
    public static class AnchoredUser {
        public String name;
        public String email;
        public String tag;
    }

    @ValidJsonSchema(ref = "classpath:/json-schemas/user.schema.json")
    public static class ClasspathRefUser {
        public int id;
        public String name;
    }

    @ValidJsonSchema(ref = "user.schema.json")
    public static class ExternalRefUser {
        public int id;
        public String name;
    }

    @Test
    public void testInlineSchemaOnPojo() {
        SchemaValidator validator = new SchemaValidator();
        InlineUser ok = new InlineUser();
        ok.id = 1;
        ok.name = "han";
        assertTrue(validator.validate(ok).isValid());

        InlineUser bad = new InlineUser();
        bad.id = 0;
        bad.name = "";
        assertFalse(validator.validate(bad).isValid());
    }

    @Test
    public void testResourceSchemaOnPojo() {
        SchemaValidator validator = new SchemaValidator();
        ResourceUser ok = new ResourceUser();
        ok.id = 2;
        ok.name = "alice";
        assertTrue(validator.validate(ok).isValid());

        ResourceUser bad = new ResourceUser();
        bad.id = 0;
        bad.name = "";
        assertFalse(validator.validate(bad).isValid());
    }

    @Test
    public void testConventionSchemaOnPojo() {
        SchemaValidator validator = new SchemaValidator();
        ConventionUser ok = new ConventionUser();
        ok.code = "AB12";
        assertTrue(validator.validate(ok).isValid());

        ConventionUser bad = new ConventionUser();
        bad.code = "ab12";
        assertFalse(validator.validate(bad).isValid());
    }

    @Test
    public void testAnchoredSchemaOnClass() {
        SchemaValidator validator = new SchemaValidator();
        AnchoredUser ok = new AnchoredUser();
        ok.name = "han";
        ok.email = "han@example.com";
        ok.tag = "alpha";
        ValidationResult result = validator.validate(ok);
        assertTrue(result.isValid());

        AnchoredUser badEmail = new AnchoredUser();
        badEmail.name = "han";
        badEmail.email = "not-email";
        badEmail.tag = "alpha";
        assertFalse(validator.validate(badEmail).isValid());

        AnchoredUser badTag = new AnchoredUser();
        badTag.name = "han";
        badTag.email = "han@example.com";
        badTag.tag = "a1";
        assertFalse(validator.validate(badTag).isValid());
    }


//    @ValidJsonSchema(ref = "#User")
//    public static class FragmentRefUser {
//        public String name;
//    }
//
//    @Test
//    public void testFragmentOnlyRefUsesConvention() {
//        SchemaValidator validator = new SchemaValidator();
//        FragmentRefUser ok = new FragmentRefUser();
//        ok.name = "han";
//        assertTrue(validator.validate(ok).isValid());
//
//        FragmentRefUser bad = new FragmentRefUser();
//        bad.name = "";
//        assertFalse(validator.validate(bad).isValid());
//    }

    @Test
    public void testExplicitClasspathRef() {
        SchemaValidator validator = new SchemaValidator();
        ClasspathRefUser ok = new ClasspathRefUser();
        ok.id = 1;
        ok.name = "tom";
        assertTrue(validator.validate(ok).isValid());
    }

    @ValidJsonSchema(ref = "classpath:/json-schemas/domain.schema.json#/$defs/User")
    public static class PointerRefUser {
        public String name;
        public String email;
        public String tag;
    }

    @Test
    public void testPointerRefInSchema() {
        SchemaValidator validator = new SchemaValidator();
        PointerRefUser ok = new PointerRefUser();
        ok.name = "han";
        ok.email = "han@example.com";
        ok.tag = "alpha";
        assertTrue(validator.validate(ok).isValid());

        PointerRefUser bad = new PointerRefUser();
        bad.name = "han";
        bad.email = "not-email";
        bad.tag = "alpha";
        assertFalse(validator.validate(bad).isValid());
    }


    @ValidJsonSchema
    public static class ExternalBaseUser {
        public String name;
    }

    @ValidJsonSchema
    public static class PreloadUser {
        public int id;
        public String name;
    }

    @Test
    public void testExternalBaseDir(@TempDir Path tempDir) throws Exception {
        String schema = "{" +
                "\"type\":\"object\"," +
                "\"required\":[\"name\"]," +
                "\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":1}}," +
                "\"additionalProperties\":false" +
                "}";
        Path schemaPath = tempDir.resolve("ExternalBaseUser.schema.json");
        Files.write(schemaPath, schema.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        ExternalBaseUser ok = new ExternalBaseUser();
        ok.name = "han";
        assertTrue(validator.validate(ok).isValid());
    }


    @Test
    public void testPreloadRefs(@TempDir Path tempDir) throws Exception {
        Path schemaPath = tempDir.resolve("user.schema.json");
        Files.write(schemaPath, SIMPLE_USER_SCHEMA.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        validator.preload("user.schema.json");

        ExternalRefUser ok = new ExternalRefUser();
        ok.id = 1;
        ok.name = "han";
        assertTrue(validator.validate(ok).isValid());
    }

    @Test
    public void testPreloadDirectory(@TempDir Path tempDir) throws Exception {
        Path schemaPath = tempDir.resolve("PreloadUser.schema.json");
        Files.write(schemaPath, SIMPLE_USER_SCHEMA.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        validator.preloadDirectory(baseDir);

        PreloadUser ok = new PreloadUser();
        ok.id = 1;
        ok.name = "han";
        assertTrue(validator.validate(ok).isValid());
    }


    @ValidJsonSchema("{\"type\":\"object\",\"required\":[\"id\",\"name\"],\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"}},\"additionalProperties\":false}")
    public static class JojoUser extends JsonObject {
    }

    @Test
    public void testJojoSchema() {
        SchemaValidator validator = new SchemaValidator();
        JojoUser ok = new JojoUser();
        ok.put("id", 1);
        ok.put("name", "tom");
        assertTrue(validator.validate(ok).isValid());

        JojoUser bad = new JojoUser();
        bad.put("id", 0);
        assertFalse(validator.validate(bad).isValid());
    }


    @ValidJsonSchema(
            value = "{\"type\":\"object\",\"properties\":{\"email\":{\"type\":\"string\",\"format\":\"email\"}}}",
            strictFormat = false
    )
    public static class FormatUser {
        public String email;
    }

    @Test
    public void testDefaultStrictFormatDisabled() {
        SchemaValidator validator = new SchemaValidator();
        FormatUser user = new FormatUser();
        user.email = "not-email";
        assertTrue(validator.validate(user).isValid());
    }

}
