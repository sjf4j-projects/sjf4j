package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidJsonSchemaTest {

    @ValidJsonSchema(value = "{" +
            "\"type\":\"object\"," +
            "\"required\":[\"id\",\"name\"]," +
            "\"properties\":{" +
            "   \"id\":{\"type\":\"integer\",\"minimum\":1}," +
            "   \"name\":{\"type\":\"string\",\"minLength\":1}}," +
            "   \"additionalProperties\":false" +
            "}"
    )
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

    @ValidJsonSchema(value = "{\"type\":\"object\",\"required\":[\"id\",\"name\"],\"properties\":{\"id\":{\"type\":\"integer\"},\"name\":{\"type\":\"string\"}},\"additionalProperties\":false}")
    public static class JojoUser extends JsonObject {
    }

    @ValidJsonSchema(value = "{\"type\":\"object\",\"properties\":{\"email\":{\"type\":\"string\",\"format\":\"email\"}}}")
    public static class FormatUser {
        public String email;
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

    @Test
    public void testDefaultStrictFormatDisabled() {
        SchemaValidator validator = new SchemaValidator();
        FormatUser user = new FormatUser();
        user.email = "not-email";
        assertTrue(validator.validate(user).isValid());
    }

}
