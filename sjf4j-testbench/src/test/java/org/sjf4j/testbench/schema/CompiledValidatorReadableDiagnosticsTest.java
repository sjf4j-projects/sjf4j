package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.ValidationException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Ensure diagnostic source refactoring retains keyword and instance paths. */
class CompiledValidatorReadableDiagnosticsTest {
    public static final class Model {
        private final BigDecimal decimal;
        private final String name;
        private final List<String> list;

        Model(BigDecimal decimal, String name, List<String> list) {
            this.decimal = decimal;
            this.name = name;
            this.list = list;
        }

        public BigDecimal getDecimal() { return decimal; }
        public String getName() { return name; }
        public List<String> getList() { return list; }
    }

    @CompiledValidator(fallback = false)
    interface Validator {
        @ValidJsonSchema("{\"type\":\"object\",\"properties\":{"
                + "\"decimal\":{\"type\":\"number\",\"exclusiveMinimum\":0},"
                + "\"name\":{\"type\":\"string\",\"minLength\":0},"
                + "\"list\":{\"type\":\"array\",\"minItems\":0,\"items\":{\"type\":\"string\"}}}}")
        void requireValid(Model value);
    }

    private static void assertFailure(Validator validator, Model value,
                                      String keyword, String pointer, String instance) {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.requireValid(value));
        assertEquals(keyword, ex.getResult().getLastError().getKeyword());
        assertEquals(pointer, ex.getResult().getLastError().getKeywordPs().rootedPointerExpr());
        assertEquals(instance, ex.getResult().getLastError().getInstancePs().rootedPointerExpr());
    }

    @Test
    void knownJavaShapesAndZeroBoundsPreserveFailFastPaths() {
        Validator validator = CompiledInstances.create(Validator.class);
        assertFailure(validator, null, "type", "/type", "");
        assertFailure(validator, new Model(null, "", List.of()), "type",
                "/properties/decimal/type", "/decimal");
        assertFailure(validator, new Model(BigDecimal.ZERO, "", List.of()),
                "exclusiveMinimum", "/properties/decimal/exclusiveMinimum", "/decimal");
        assertFailure(validator, new Model(BigDecimal.ONE, null, List.of()), "type",
                "/properties/name/type", "/name");
        assertFailure(validator, new Model(BigDecimal.ONE, "", null), "type",
                "/properties/list/type", "/list");
        assertFailure(validator, new Model(BigDecimal.ONE, "", Arrays.asList("ok", null)),
                "type", "/properties/list/items/type", "/list/1");
        assertDoesNotThrow(() -> validator.requireValid(
                new Model(BigDecimal.ONE, "", List.of())));
        assertDoesNotThrow(() -> validator.requireValid(
                new Model(new BigDecimal("0.01"), "valid", List.of("x"))));
    }
}
