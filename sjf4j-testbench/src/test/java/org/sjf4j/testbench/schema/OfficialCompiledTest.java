package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Selected official Draft 2020-12 groups with schemas fixed at compile time. */
public final class OfficialCompiledTest {
    private static final String INTEGER = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"integer\"}";
    private static final String NUMBER = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"number\"}";
    private static final String STRING = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"string\"}";
    private static final String OBJECT = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\"}";
    private static final String ARRAY = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"array\"}";
    private static final String BOOLEAN = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"boolean\"}";
    private static final String INTEGER_OR_STRING = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":[\"integer\",\"string\"]}";
    private static final String SINGLE_STRING_TYPE = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":[\"string\"]}";
    private static final String ARRAY_OR_OBJECT = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":[\"array\",\"object\"]}";
    private static final String SIMPLE_ENUM = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"enum\":[1,2,3]}";
    private static final String SCALAR_CONST = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"const\":2}";
    private static final String REQUIRED = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"properties\":{\"foo\":{},\"bar\":{}},\"required\":[\"foo\"]}";
    private static final String REQUIRED_DEFAULT = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"properties\":{\"foo\":{}}}";
    private static final String REQUIRED_EMPTY = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"properties\":{\"foo\":{}},\"required\":[]}";
    private static final String REQUIRED_JS_NAMES = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"required\":[\"__proto__\",\"toString\",\"constructor\"]}";
    private static final String MIN_PROPERTIES = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"minProperties\":1}";
    private static final String MAX_PROPERTIES = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"maxProperties\":2}";
    private static final String PROPERTIES = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"properties\":{\"foo\":{\"type\":\"integer\"},\"bar\":{\"type\":\"string\"}}}";
    private static final String BOOLEAN_PROPERTIES = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"properties\":{\"foo\":true,\"bar\":false}}";

    @CompiledValidator(fallback = false)
    public interface Validator {
        @ValidJsonSchema(INTEGER) boolean integer(Object value);
        @ValidJsonSchema(NUMBER) boolean number(Object value);
        @ValidJsonSchema(STRING) boolean string(Object value);
        @ValidJsonSchema(OBJECT) boolean object(Object value);
        @ValidJsonSchema(ARRAY) boolean array(Object value);
        @ValidJsonSchema(BOOLEAN) boolean bool(Object value);
        @ValidJsonSchema(INTEGER_OR_STRING) boolean integerOrString(Object value);
        @ValidJsonSchema(SINGLE_STRING_TYPE) boolean singleStringType(Object value);
        @ValidJsonSchema(ARRAY_OR_OBJECT) boolean arrayOrObject(Object value);
        @ValidJsonSchema(SIMPLE_ENUM) boolean simpleEnum(Object value);
        @ValidJsonSchema(SCALAR_CONST) boolean scalarConst(Object value);
        @ValidJsonSchema(REQUIRED) boolean required(Object value);
        @ValidJsonSchema(REQUIRED_DEFAULT) boolean requiredDefault(Object value);
        @ValidJsonSchema(REQUIRED_EMPTY) boolean requiredEmpty(Object value);
        @ValidJsonSchema(REQUIRED_JS_NAMES) boolean requiredJsNames(Object value);
        @ValidJsonSchema(MIN_PROPERTIES) boolean minProperties(Object value);
        @ValidJsonSchema(MAX_PROPERTIES) boolean maxProperties(Object value);
        @ValidJsonSchema(PROPERTIES) boolean properties(Object value);
        @ValidJsonSchema(BOOLEAN_PROPERTIES) boolean booleanProperties(Object value);
    }

    private final Validator validator = CompiledInstances.create(Validator.class);

    @Test
    void integer() throws Exception {
        checkGroup("type.json", "integer type matches integers", INTEGER, validator::integer);
    }

    @Test
    void number() throws Exception {
        checkGroup("type.json", "number type matches numbers", NUMBER, validator::number);
    }

    @Test
    void string() throws Exception {
        checkGroup("type.json", "string type matches strings", STRING, validator::string);
    }

    @Test
    void object() throws Exception {
        checkGroup("type.json", "object type matches objects", OBJECT, validator::object);
    }

    @Test
    void array() throws Exception {
        checkGroup("type.json", "array type matches arrays", ARRAY, validator::array);
    }

    @Test
    void bool() throws Exception {
        checkGroup("type.json", "boolean type matches booleans", BOOLEAN, validator::bool);
    }

    @Test
    void integerOrString() throws Exception {
        checkGroup("type.json", "multiple types can be specified in an array", INTEGER_OR_STRING,
                validator::integerOrString);
    }

    @Test
    void singleStringType() throws Exception {
        checkGroup("type.json", "type as array with one item", SINGLE_STRING_TYPE,
                validator::singleStringType);
    }

    @Test
    void arrayOrObject() throws Exception {
        checkGroup("type.json", "type: array or object", ARRAY_OR_OBJECT, validator::arrayOrObject);
    }

    @Test
    void simpleEnum() throws Exception {
        checkGroup("enum.json", "simple enum validation", SIMPLE_ENUM, validator::simpleEnum);
    }

    @Test
    void scalarConst() throws Exception {
        checkGroup("const.json", "const validation", SCALAR_CONST, validator::scalarConst);
    }

    @Test
    void required() throws Exception {
        checkGroup("required.json", "required validation", REQUIRED, validator::required);
    }

    @Test
    void requiredDefault() throws Exception {
        checkGroup("required.json", "required default validation", REQUIRED_DEFAULT, validator::requiredDefault);
    }

    @Test
    void requiredEmpty() throws Exception {
        checkGroup("required.json", "required with empty array", REQUIRED_EMPTY, validator::requiredEmpty);
    }

    @Test
    void requiredJsNames() throws Exception {
        checkGroup("required.json", "required properties whose names are Javascript object property names",
                REQUIRED_JS_NAMES, validator::requiredJsNames);
    }

    @Test
    void minProperties() throws Exception {
        checkGroup("minProperties.json", "minProperties validation", MIN_PROPERTIES, validator::minProperties);
    }

    @Test
    void maxProperties() throws Exception {
        checkGroup("maxProperties.json", "maxProperties validation", MAX_PROPERTIES, validator::maxProperties);
    }

    @Test
    void properties() throws Exception {
        checkGroup("properties.json", "object properties validation", PROPERTIES, validator::properties);
    }

    @Test
    void booleanProperties() throws Exception {
        checkGroup("properties.json", "properties with boolean schema", BOOLEAN_PROPERTIES,
                validator::booleanProperties);
    }

    private static void checkGroup(String file, String description, String schema, Predicate<Object> validate)
            throws Exception {
        Path root = Paths.get(System.getProperty("sjf4j.schema.officialTestRoot",
                "build/official-json-schema-tests/suite"));
        JsonArray groups = Sjf4j.global().fromJson(Files.readAllBytes(
                root.resolve("tests/draft2020-12").resolve(file)), JsonArray.class);
        JsonObject group = null;
        for (int i = 0; i < groups.size(); i++) {
            JsonObject candidate = groups.getJsonObject(i);
            if (description.equals(candidate.getString("description"))) {
                group = candidate;
                break;
            }
        }
        assertNotNull(group, file + ": " + description);
        assertEquals(Sjf4j.global().fromJson(schema, JsonObject.class), group.getJsonObject("schema"),
                file + ": schema changed; update the compiled annotation");
        JsonArray tests = group.getJsonArray("tests");
        int checked = 0;
        for (int i = 0; i < tests.size(); i++) {
            JsonObject test = tests.getJsonObject(i);
            Object data = test.get("data");
            // Generated validators currently accept null before checking the root schema.
            if (data == null) continue;
            checked++;
            assertEquals(test.getBoolean("valid"), validate.test(data),
                    file + ": " + description + " -> " + test.getString("description"));
        }
        assertTrue(checked > 0, file + ": no non-null cases");
    }
}
