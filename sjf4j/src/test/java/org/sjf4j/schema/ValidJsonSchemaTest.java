package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.exception.SchemaException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @ValidJsonSchema(ref = "user.json")
    public static class ResourceUser {
        public int id;
        public String name;
    }

    @ValidJsonSchema
    public static class ConventionUser {
        public String code;
    }

    @ValidJsonSchema(ref = "domain.json#User")
    public static class AnchoredUser {
        public String name;
        public String email;
        public String tag;
    }

    @ValidJsonSchema(ref = "classpath:/json-schemas/user.json")
    public static class ClasspathRefUser {
        public int id;
        public String name;
    }

    @ValidJsonSchema(ref = "user.json")
    public static class ExternalRefUser {
        public int id;
        public String name;
    }

    @ValidJsonSchema(ref = "missing.json")
    public static class MissingRefUser {
        public String name;
    }

    @ValidJsonSchema(ref = "root.json")
    public static class NoIdRelativeRefUser extends JsonObject {
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
        SchemaValidator validator = new SchemaValidator(null,
                new ValidationOptions.Builder().strictFormats(true).build(),
                null);
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

    @ValidJsonSchema(ref = "classpath:/json-schemas/domain.json#/$defs/User")
    public static class PointerRefUser {
        public String name;
        public String email;
        public String tag;
    }

    @Test
    public void testPointerRefInSchema() {
        SchemaValidator validator = new SchemaValidator(null,
                new ValidationOptions.Builder().strictFormats(true).build(),
                null);
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
    public static class MetaplusDoc {
        public String name;
    }

    @ValidJsonSchema
    public static class FullNameOnlyUser {
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
        Path schemaPath = tempDir.resolve("ExternalBaseUser.json");
        Files.write(schemaPath, schema.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        ExternalBaseUser ok = new ExternalBaseUser();
        ok.name = "han";
        assertTrue(validator.validate(ok).isValid());
    }

    @Test
    public void testExternalBaseDirSnakeName(@TempDir Path tempDir) throws Exception {
        String schema = "{" +
                "\"type\":\"object\"," +
                "\"required\":[\"name\"]," +
                "\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":1}}," +
                "\"additionalProperties\":false" +
                "}";
        Path schemaPath = tempDir.resolve("metaplus_doc.json");
        Files.write(schemaPath, schema.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        MetaplusDoc ok = new MetaplusDoc();
        ok.name = "han";
        assertTrue(validator.validate(ok).isValid());
    }

    @Test
    public void testConventionDoesNotUseFullClassName(@TempDir Path tempDir) throws Exception {
        String schema = "{" +
                "\"type\":\"object\"," +
                "\"required\":[\"name\"]," +
                "\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":1}}," +
                "\"additionalProperties\":false" +
                "}";
        Path schemaPath = tempDir.resolve(FullNameOnlyUser.class.getName() + ".json");
        Files.write(schemaPath, schema.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        FullNameOnlyUser ok = new FullNameOnlyUser();
        ok.name = "han";

        assertThrows(SchemaException.class, () -> validator.validate(ok));
    }


    @Test
    public void testPreloadRefs(@TempDir Path tempDir) throws Exception {
        Path schemaPath = tempDir.resolve("user.json");
        Files.write(schemaPath, SIMPLE_USER_SCHEMA.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        validator.preload("user.json");

        ExternalRefUser ok = new ExternalRefUser();
        ok.id = 1;
        ok.name = "han";
        assertTrue(validator.validate(ok).isValid());
    }

    @Test
    public void testPreloadMissingRefThrows(@TempDir Path tempDir) {
        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        assertThrows(SchemaException.class, () -> validator.preload("missing.json"));
    }

    @Test
    public void testExplicitMissingRefThrows(@TempDir Path tempDir) {
        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        MissingRefUser user = new MissingRefUser();
        user.name = "han";
        assertThrows(SchemaException.class, () -> validator.validate(user));
    }

    @Test
    public void testPreloadDirectory(@TempDir Path tempDir) throws Exception {
        Path schemaPath = tempDir.resolve("PreloadUser.json");
        Files.write(schemaPath, SIMPLE_USER_SCHEMA.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        validator.preloadDirectory(baseDir);

        PreloadUser ok = new PreloadUser();
        ok.id = 1;
        ok.name = "han";
        assertTrue(validator.validate(ok).isValid());
    }

    @Test
    public void testRelativeRefUsesRetrievalUriWithoutId(@TempDir Path tempDir) throws Exception {
        String rootSchema = "{" +
                "\"type\":\"object\"," +
                "\"required\":[\"name\"]," +
                "\"properties\":{\"name\":{\"$ref\":\"defs/non-empty-string.json\"}}," +
                "\"additionalProperties\":false" +
                "}";
        String leafSchema = "{" +
                "\"type\":\"string\"," +
                "\"minLength\":1" +
                "}";

        Path defsDir = tempDir.resolve("defs");
        Files.createDirectories(defsDir);
        Files.write(tempDir.resolve("root.json"), rootSchema.getBytes(StandardCharsets.UTF_8));
        Files.write(defsDir.resolve("non-empty-string.json"), leafSchema.getBytes(StandardCharsets.UTF_8));

        String baseDir = "file:" + tempDir.toString();
        SchemaValidator validator = new SchemaValidator(baseDir, null, null);
        validator.preloadDirectory(baseDir);

        NoIdRelativeRefUser ok = new NoIdRelativeRefUser();
        ok.put("name", "han");
        assertTrue(validator.validate(ok).isValid());

        NoIdRelativeRefUser bad = new NoIdRelativeRefUser();
        bad.put("name", "");
        assertFalse(validator.validate(bad).isValid());
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
            value = "{\"type\":\"object\",\"properties\":{\"email\":{\"type\":\"string\",\"format\":\"email\"}}}"
    )
    public static class FormatUser {
        public String email;
    }

    @ValidJsonSchema("{\"type\":\"object\",\"required\":[\"a\",\"b\"],\"properties\":{\"a\":{\"type\":\"integer\"},\"b\":{\"type\":\"string\"}}}")
    public static class ParentAnnotated {
        public Integer a;
        public String b;
    }

    public static class ChildInheritOnly extends ParentAnnotated {
        public Boolean c;
    }

    @ValidJsonSchema("{\"type\":\"object\",\"required\":[\"c\"],\"properties\":{\"c\":{\"type\":\"boolean\"}}}")
    public static class ChildAnnotated extends ParentAnnotated {
        public Boolean c;
    }

    @Test
    public void testDefaultStrictFormatDisabled() {
        SchemaValidator validator = new SchemaValidator(null, ValidationOptions.DEFAULT, null);
        FormatUser user = new FormatUser();
        user.email = "not-email";
        assertTrue(validator.validate(user).isValid());
    }

    @Test
    public void testInheritedSchemaFromParentClass() {
        SchemaValidator validator = new SchemaValidator();

        ChildInheritOnly ok = new ChildInheritOnly();
        ok.a = 1;
        ok.b = "x";
        ok.c = true;
        assertTrue(validator.validate(ok).isValid());

        ChildInheritOnly bad = new ChildInheritOnly();
        bad.b = "x";
        bad.c = true;
        assertFalse(validator.validate(bad).isValid());
    }

    @Test
    public void testParentAndChildBothAnnotated() {
        SchemaValidator validator = new SchemaValidator();

        ChildAnnotated ok = new ChildAnnotated();
        ok.a = 1;
        ok.b = "x";
        ok.c = true;
        assertTrue(validator.validate(ok).isValid());

        ChildAnnotated badParent = new ChildAnnotated();
        badParent.b = "x";
        badParent.c = true;
        assertFalse(validator.validate(badParent).isValid());

        ChildAnnotated badChild = new ChildAnnotated();
        badChild.a = 1;
        badChild.b = "x";
        assertFalse(validator.validate(badChild).isValid());
    }

}
