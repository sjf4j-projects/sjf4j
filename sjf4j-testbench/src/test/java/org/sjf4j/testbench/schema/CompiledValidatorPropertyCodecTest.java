package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generated validators must honor the same property codec selections as runtime SchemaPlan. */
public class CompiledValidatorPropertyCodecTest {
    public static final class Times {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant millis = Instant.ofEpochMilli(2_000);
        @NodeProperty(valuePattern = "yyyy/MM/dd")
        public LocalDate day = LocalDate.of(2026, 11, 2);
        public String standard = "ok";
    }

    @NodeValue
    public static final class BooleanValue {
        final boolean state;
        public BooleanValue(boolean state) { this.state = state; }
        @ValueToRaw public Boolean toRaw() { return state; }
        @RawToValue public static BooleanValue fromRaw(Boolean raw) {
            return new BooleanValue(Boolean.TRUE.equals(raw));
        }
    }

    public static final class FlagHolder {
        @NodeProperty(valueFormat = "")
        public BooleanValue flag = new BooleanValue(true);
    }

    public static final class GetterWins {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant millis = Instant.ofEpochMilli(2_000);
        public Instant getMillis() { return millis; }
    }

    @CompiledValidator(fallback = false)
    public interface Compiled {
        @ValidJsonSchema("{\"type\":\"object\",\"properties\":{"
                + "\"millis\":{\"type\":\"number\",\"minimum\":2000},"
                + "\"day\":{\"type\":\"string\",\"pattern\":\"^2026/11/02$\"},"
                + "\"standard\":{\"type\":\"string\"}}}")
        boolean valid(Times value);

        @ValidJsonSchema("{\"properties\":{\"millis\":{\"type\":\"number\",\"const\":2000}}}")
        boolean getter(GetterWins value);

        @ValidJsonSchema("{\"properties\":{\"flag\":{\"type\":\"boolean\"}}}")
        boolean booleanCodec(FlagHolder value);
    }

    @CompiledValidator(fallback = false)
    public interface RegisteredCodecRoot {
        @ValidJsonSchema("{\"type\":\"object\"}")
        boolean object(Instant value);

        @ValidJsonSchema("{\"type\":\"object\"}")
        boolean object(LocalDate value);
    }

    @CompiledValidator(fallback = true)
    public interface Fallback {
        @ValidJsonSchema("{\"properties\":{\"millis\":{\"type\":\"object\"}}}")
        boolean structural(Times value);

        @ValidJsonSchema("{\"properties\":{\"millis\":{\"anyOf\":["
                + "{\"type\":\"number\",\"minimum\":1000},{\"type\":\"null\"}]}}}")
        boolean complexScalar(Times value);
    }

    @Test
    void registeredCodecsDoNotBecomePojoObjectNodes() {
        RegisteredCodecRoot compiled = CompiledInstances.create(RegisteredCodecRoot.class);
        assertFalse(compiled.object(Instant.EPOCH));
        assertFalse(compiled.object(LocalDate.of(2026, 11, 2)));
    }

    @Test
    void directlyChecksNamedAndPatternedPropertyCodecs() {
        Compiled compiled = CompiledInstances.create(Compiled.class);
        Times times = new Times();
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"type\":\"object\",\"properties\":{"
                        + "\"millis\":{\"type\":\"number\",\"minimum\":2000},"
                        + "\"day\":{\"type\":\"string\",\"pattern\":\"^2026/11/02$\"},"
                        + "\"standard\":{\"type\":\"string\"}}}").createPlan();
        assertTrue(compiled.valid(times));
        assertEquals(plan.isValid(times), compiled.valid(times));
        times.millis = Instant.ofEpochMilli(1999);
        assertFalse(compiled.valid(times));
        assertEquals(plan.isValid(times), compiled.valid(times));
        times.millis = null;
        assertEquals(plan.isValid(times), compiled.valid(times));
    }

    @Test
    void staticallyCompilesBooleanRawValueCodec() {
        Compiled validator = CompiledInstances.create(Compiled.class);
        FlagHolder holder = new FlagHolder();
        SchemaPlan reference = JsonSchema.fromJson(
                "{\"properties\":{\"flag\":{\"type\":\"boolean\"}}}").createPlan();
        assertTrue(reference.isValid(holder));
        assertTrue(validator.booleanCodec(holder));
        holder.flag = null;
        assertEquals(reference.isValid(holder), validator.booleanCodec(holder));
    }

    @Test
    void detectsCodecAnnotationOnFieldWhenGetterWins() {
        Compiled compiled = CompiledInstances.create(Compiled.class);
        GetterWins value = new GetterWins();
        assertTrue(compiled.getter(value));
        value.millis = Instant.ofEpochMilli(42);
        assertFalse(compiled.getter(value));
    }

    @Test
    void structuralKeywordKeepsRuntimePropertyContext() {
        Times value = new Times();
        Fallback compiled = CompiledInstances.create(Fallback.class);
        SchemaPlan reference = JsonSchema.fromJson(
                "{\"properties\":{\"millis\":{\"type\":\"object\"}}}").createPlan();
        assertFalse(reference.isValid(value));
        assertEquals(reference.isValid(value), compiled.structural(value));

        // This is not directly supported by the explicit-codec scalar compiler.
        // Falling back on the child Instant would select the ISO default and fail:
        // fallback must evaluate the parent POJO to retain epochMillis.
        SchemaPlan complex = JsonSchema.fromJson(
                "{\"properties\":{\"millis\":{\"anyOf\":["
                        + "{\"type\":\"number\",\"minimum\":1000},{\"type\":\"null\"}]}}}").createPlan();
        assertTrue(complex.isValid(value));
        assertEquals(complex.isValid(value), compiled.complexScalar(value));
        value.millis = Instant.ofEpochMilli(100);
        assertEquals(complex.isValid(value), compiled.complexScalar(value));
    }
}
