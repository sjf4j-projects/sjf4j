package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Scalar literal and format assertions must agree with full JSON Schema semantics. */
class CompiledValidatorScalarLiteralTest {
    public static final class Value {
        private final String text;
        private final BigDecimal decimal;
        private final Object raw;

        public Value() { this("aa", BigDecimal.ONE, null); }
        public Value(String text, BigDecimal decimal, Object raw) {
            this.text = text;
            this.decimal = decimal;
            this.raw = raw;
        }
        public String getText() { return text; }
        public BigDecimal getDecimal() { return decimal; }
        public Object getRaw() { return raw; }
    }

    private static final String STRING_CONST =
            "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\",\"const\":\"aa\"}}}";
    private static final String NUMBER_ENUM =
            "{\"type\":\"object\",\"properties\":{\"decimal\":{\"type\":\"number\",\"enum\":[1,2.5]}}}";
    private static final String INTEGER_CONST =
            "{\"type\":\"object\",\"properties\":{\"decimal\":{\"type\":\"integer\",\"const\":1}}}";
    private static final String BARE_ENUM =
            "{\"type\":\"object\",\"properties\":{\"text\":{\"enum\":[\"aa\",null,2]}}}";
    private static final String BARE_CONST =
            "{\"type\":\"object\",\"properties\":{\"raw\":{\"const\":null}}}";
    private static final String BOTH =
            "{\"type\":\"object\",\"properties\":{\"decimal\":{\"type\":\"number\",\"const\":1,\"enum\":[2,3]}}}";
    private static final String EMAIL =
            "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\",\"format\":\"email\"}}}";
    private static final String UUID =
            "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\",\"format\":\"uuid\"}}}";

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(STRING_CONST) boolean stringConst(Value v);
        @ValidJsonSchema(NUMBER_ENUM) boolean numberEnum(Value v);
        @ValidJsonSchema(INTEGER_CONST) boolean integerConst(Value v);
        @ValidJsonSchema(BARE_ENUM) boolean bareEnum(Value v);
        @ValidJsonSchema(BARE_CONST) boolean bareConst(Value v);
        @ValidJsonSchema(BOTH) boolean impossible(Value v);
        @ValidJsonSchema(EMAIL) boolean email(Value v, boolean strictFormat);
        @ValidJsonSchema(UUID) boolean uuid(Value v, boolean strictFormat);
    }

    private static Value value(String text, String decimal, Object raw) {
        return new Value(text, decimal == null ? null : new BigDecimal(decimal), raw);
    }

    @Test
    void stringAndNumberConstantsAndEnumsUseJsonEquality() {
        Direct compiled = CompiledInstances.create(Direct.class);
        assertTrue(compiled.stringConst(value("aa", "1", null)));
        assertFalse(compiled.stringConst(value("ab", "1", null)));
        assertFalse(compiled.stringConst(value(null, "1", null)));
        assertTrue(compiled.numberEnum(value("aa", "1.0", null)));
        assertTrue(compiled.numberEnum(value("aa", "2.50", null)));
        assertFalse(compiled.numberEnum(value("aa", "3", null)));
        assertTrue(compiled.integerConst(value("aa", "1.00", null)));
        assertFalse(compiled.integerConst(value("aa", "1.5", null)));
        assertTrue(compiled.bareEnum(value(null, "1", null)));
        assertTrue(compiled.bareEnum(value("aa", "1", null)));
        assertFalse(compiled.bareEnum(value("bb", "1", null)));
        assertTrue(compiled.bareConst(value("aa", "1", null)));
        assertFalse(compiled.bareConst(value("aa", "1", "non-null")));
        assertFalse(compiled.impossible(value("aa", "1", null)));
    }

    @Test
    void formatIsAnAssertionOnlyWhenStrictIsEnabledForDefaultVocabulary() {
        Direct compiled = CompiledInstances.create(Direct.class);
        for (String text : new String[]{"user@example.com", "invalid", "", null}) {
            Value v = value(text, "1", null);
            SchemaPlan email = JsonSchema.fromJson(EMAIL).createPlan();
            assertEquals(email.isValid(v, false), compiled.email(v, false));
            assertEquals(email.isValid(v, true), compiled.email(v, true));
        }
        for (String text : new String[]{"01234567-89ab-4def-8abc-0123456789ab", "not-uuid", null}) {
            Value v = value(text, "1", null);
            SchemaPlan uuid = JsonSchema.fromJson(UUID).createPlan();
            assertEquals(uuid.isValid(v, false), compiled.uuid(v, false));
            assertEquals(uuid.isValid(v, true), compiled.uuid(v, true));
        }
        assertTrue(compiled.email(value("invalid", "1", null), false));
        assertFalse(compiled.email(value("invalid", "1", null), true));
    }
}
