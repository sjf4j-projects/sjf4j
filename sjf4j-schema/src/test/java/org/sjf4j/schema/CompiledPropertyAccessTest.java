package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class CompiledPropertyAccessTest {

    @Test
    void bypassesObjectEntryViewsWhilePreservingValidation() {
        SchemaRegistry registry = new SchemaRegistry();
        SchemaPlan runtime = registry.register(URI.create("sjf4j:/tests/pojo"),
                JsonSchema.fromJson("{\"type\":\"object\",\"required\":[\"value\"],"
                        + "\"properties\":{\"value\":{\"type\":\"string\",\"minLength\":3}},"
                        + "\"patternProperties\":{\"^val\":{\"maxLength\":6}}}"));
        CompiledPropertyAccess accessor = new CompiledPropertyAccess() {
            @Override public Class<?> type() { return Bean.class; }
            @Override public String[] names() { return new String[]{"value"}; }
            @Override public String[] getterNames() { return new String[]{"getValue"}; }
            @Override public Object read(Object value, int index) {
                if (index != 0) throw new IllegalArgumentException();
                return ((Bean) value).getValue();
            }
        };
        SchemaPlan compiled = CompiledSchemaSupport.specialize(runtime, accessor);
        assertNotSame(runtime, compiled, "getter metadata should enable specialization");
        assertTrue(runtime.isValid(new Bean("valid"), true));
        for (Bean value : new Bean[]{new Bean("valid"), new Bean("x"), new Bean("excessively-long"), new Bean(null)}) {
            assertEquals(runtime.isValid(value, true), compiled.isValid(value, true));
            assertEquals(runtime.validate(value).isValid(), compiled.validate(value).isValid());
        }

        CompiledPropertyAccess wrongGetter = new CompiledPropertyAccess() {
            @Override public Class<?> type() { return Bean.class; }
            @Override public String[] names() { return new String[]{"value"}; }
            @Override public String[] getterNames() { return new String[]{"wrong"}; }
            @Override public Object read(Object value, int index) { throw new AssertionError("must fall back"); }
        };
        assertSame(runtime, CompiledSchemaSupport.specialize(runtime, wrongGetter));
    }

    public static final class Bean {
        private final String value;
        public Bean() { this(null); }
        public Bean(String value) { this.value = value; }
        public String getValue() { return value; }
    }
}
