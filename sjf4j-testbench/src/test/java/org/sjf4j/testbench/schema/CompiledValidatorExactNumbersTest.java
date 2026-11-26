package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledValidatorExactNumbersTest {

    private static final String DECIMAL = "0.123456789123456789";
    private static final BigDecimal EXACT = new BigDecimal(DECIMAL);
    private static final BigDecimal LOWER = new BigDecimal("0.123456789123456788");
    private static final BigDecimal UPPER = new BigDecimal("0.123456789123456790");

    private static final String MINIMUM = "{\"minimum\":" + DECIMAL + "}";
    private static final String MAXIMUM = "{\"maximum\":" + DECIMAL + "}";
    private static final String EXCLUSIVE_MINIMUM = "{\"exclusiveMinimum\":" + DECIMAL + "}";
    private static final String EXCLUSIVE_MAXIMUM = "{\"exclusiveMaximum\":" + DECIMAL + "}";
    private static final String MULTIPLE = "{\"multipleOf\":" + DECIMAL + "}";
    private static final String CONSTANT = "{\"const\":" + DECIMAL + "}";
    private static final String ENUM = "{\"enum\":[" + DECIMAL + "]}";
    private static final String COMPOUND_CONST = "{\"const\":{\"n\":[" + DECIMAL + "]}}";
    private static final String COMPOUND_ENUM = "{\"enum\":[{\"n\":[" + DECIMAL + "]}]}";
    private static final String UNIQUE = "{\"uniqueItems\":true}";
    private static final String URI = "https://example.test/exact";
    private static final String EXTERNAL = "{\"" + URI + "\":" + MINIMUM + "}";

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(MINIMUM) boolean minimum(BigDecimal value);
        @ValidJsonSchema(MAXIMUM) boolean maximum(BigDecimal value);
        @ValidJsonSchema(EXCLUSIVE_MINIMUM) boolean exclusiveMinimum(BigDecimal value);
        @ValidJsonSchema(EXCLUSIVE_MAXIMUM) boolean exclusiveMaximum(BigDecimal value);
        @ValidJsonSchema(MULTIPLE) boolean multiple(BigDecimal value);
        @ValidJsonSchema(CONSTANT) boolean constant(BigDecimal value);
        @ValidJsonSchema(ENUM) boolean enumeration(BigDecimal value);
        @ValidJsonSchema(COMPOUND_CONST) boolean compoundConst(Object value);
        @ValidJsonSchema(COMPOUND_ENUM) boolean compoundEnum(Object value);
        @ValidJsonSchema(UNIQUE) boolean unique(List<BigDecimal> value);
        @ValidJsonSchema(ref = URI, externalSchemas = EXTERNAL) boolean external(BigDecimal value);
    }

    @Test
    void numericPredicatesPreserveSchemaPrecision() {
        Direct direct = CompiledInstances.create(Direct.class);
        assertTrue(direct.minimum(EXACT));
        assertFalse(direct.minimum(LOWER));
        assertTrue(direct.maximum(EXACT));
        assertFalse(direct.maximum(UPPER));
        assertFalse(direct.exclusiveMinimum(EXACT));
        assertTrue(direct.exclusiveMinimum(UPPER));
        assertFalse(direct.exclusiveMaximum(EXACT));
        assertTrue(direct.exclusiveMaximum(LOWER));
        assertTrue(direct.multiple(EXACT.multiply(BigDecimal.valueOf(3))));
        assertFalse(direct.multiple(UPPER));
        assertTrue(direct.constant(EXACT));
        assertFalse(direct.constant(LOWER));
        assertTrue(direct.enumeration(EXACT));
        assertFalse(direct.enumeration(UPPER));
        assertTrue(direct.external(EXACT));
        assertFalse(direct.external(LOWER));
    }

    @Test
    void generatedCompoundConstantsAreDecodedExactly() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan constant = JsonSchema.fromJson(COMPOUND_CONST).createPlan();
        SchemaPlan enumeration = JsonSchema.fromJson(COMPOUND_ENUM).createPlan();
        for (BigDecimal value : Arrays.asList(EXACT, LOWER, UPPER)) {
            Map<String, List<BigDecimal>> input = Collections.singletonMap("n", Collections.singletonList(value));
            boolean expected = value.compareTo(EXACT) == 0;
            assertEquals(expected, constant.isValid(input));
            assertEquals(expected, enumeration.isValid(input));
            assertEquals(expected, direct.compoundConst(input));
            assertEquals(expected, direct.compoundEnum(input));
        }
        assertTrue(direct.unique(Arrays.asList(EXACT, LOWER)));
        assertFalse(direct.unique(Arrays.asList(EXACT, EXACT.setScale(EXACT.scale() + 1))));
    }
}
