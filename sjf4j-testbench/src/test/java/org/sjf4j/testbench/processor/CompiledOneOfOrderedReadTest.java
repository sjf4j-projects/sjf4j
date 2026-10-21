package org.sjf4j.testbench.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The compiled CURRENT/key OneOf reader must not depend on JSON member order. */
public class CompiledOneOfOrderedReadTest {

    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final Reader READER = CompiledInstances.global(Reader.class);

    @OneOf(key = "@type", mappings = {
            @OneOfMapping(type = Alpha.class, when = "alpha"),
            @OneOfMapping(type = Beta.class, when = "beta"),
            @OneOfMapping(type = Gamma.class, when = "gamma")
    })
    public interface Variant {}

    public static final class Alpha implements Variant {
        private final UUID id;
        private final int count;
        private String label;
        private int setterCalls;

        @NodeCreator
        public Alpha(@NodeProperty(value = "id", aliases = "legacy_id") UUID id,
                     @NodeProperty("count") int count) {
            this.id = id;
            this.count = count;
        }

        public UUID getId() { return id; }
        public int getCount() { return count; }
        public String getLabel() { return label; }
        public int getSetterCalls() { return setterCalls; }
        public void setLabel(String label) { this.label = label; setterCalls++; }
    }

    public static final class Beta implements Variant {
        private final double number;

        @NodeCreator
        public Beta(@NodeProperty("number") double number) {
            this.number = number;
        }

        public double getNumber() { return number; }
    }

    public static final class Gamma implements Variant {
        private String name;
        private int value;

        public Gamma() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Reader {
        @ReadFrom Variant read(String json) throws IOException;
    }

    @Test
    void generatedSubtypeReadersAreUsed() {
        boolean alpha = false;
        boolean beta = false;
        boolean gamma = false;
        for (java.lang.reflect.Method method : READER.getClass().getDeclaredMethods()) {
            alpha |= method.getReturnType() == Alpha.class && method.getName().endsWith("_content");
            beta |= method.getReturnType() == Beta.class && method.getName().endsWith("_content");
            gamma |= method.getReturnType() == Gamma.class && method.getName().endsWith("_content");
        }
        assertTrue(alpha, "Alpha must use a generated OneOf subtype reader");
        assertTrue(beta, "Beta must use a generated OneOf subtype reader");
        assertTrue(gamma, "Gamma must use a generated OneOf subtype reader");
    }

    @Test
    void discriminatorFirstMiddleAndLastUseCompiledCreatorAndCodec() throws Exception {
        String first = "{\"@type\":\"alpha\",\"id\":\"" + ID
                + "\",\"count\":7,\"label\":\"done\"}";
        String middle = "{\"legacy_id\":\"" + ID
                + "\",\"@type\":\"alpha\",\"count\":7,\"label\":\"done\"}";
        String last = "{\"label\":\"done\",\"count\":7,\"id\":\"" + ID
                + "\",\"@type\":\"alpha\"}";
        for (String json : new String[]{first, middle, last}) {
            Alpha value = assertInstanceOf(Alpha.class, READER.read(json));
            assertEquals(UUID.fromString(ID), value.getId());
            assertEquals(7, value.getCount());
            assertEquals("done", value.getLabel());
            assertEquals(1, value.getSetterCalls());
        }
    }

    @Test
    void unknownPrefixSubtreeAndNumericSubtype() throws Exception {
        Beta beta = assertInstanceOf(Beta.class, READER.read(
                "{\"unknown\":{\"list\":[1,{\"nested\":true}]},"
                        + "\"number\":12.5,\"@type\":\"beta\"}"));
        assertEquals(12.5, beta.getNumber());
        Beta first = assertInstanceOf(Beta.class,
                READER.read("{\"@type\":\"beta\",\"number\":12.5}"));
        assertEquals(12.5, first.getNumber());
    }

    @Test
    void noArgsSubtypeRestoresPrefixProperty() throws Exception {
        Gamma first = assertInstanceOf(Gamma.class,
                READER.read("{\"@type\":\"gamma\",\"value\":19,\"name\":\"Ada\"}"));
        Gamma last = assertInstanceOf(Gamma.class,
                READER.read("{\"value\":19,\"name\":\"Ada\",\"@type\":\"gamma\"}"));
        assertEquals(19, first.getValue());
        assertEquals("Ada", first.getName());
        assertEquals(19, last.getValue());
        assertEquals("Ada", last.getName());
    }

    @Test
    void detectsDuplicateCreatorArgumentAcrossPrefixAndSuffix() {
        String duplicate = "{\"id\":\"" + ID + "\",\"@type\":\"alpha\","
                + "\"legacy_id\":\"" + ID + "\"}";
        assertThrows(BindingException.class, () -> READER.read(duplicate));
        String duplicatePrefix = "{\"id\":\"" + ID + "\","
                + "\"legacy_id\":\"" + ID + "\",\"@type\":\"alpha\"}";
        assertThrows(BindingException.class, () -> READER.read(duplicatePrefix));
    }

    @Test
    void detectsDuplicateDiscriminatorAndMissingDiscriminator() {
        assertThrows(BindingException.class, () ->
                READER.read("{\"@type\":\"beta\",\"number\":2,\"@type\":\"beta\"}"));
        assertThrows(BindingException.class, () ->
                READER.read("{\"number\":2,\"@type\":\"beta\",\"@type\":\"beta\"}"));
        assertThrows(BindingException.class, () ->
                READER.read("{\"number\":2}"));
        assertThrows(BindingException.class, () ->
                READER.read("{\"@type\":\"unknown\"}"));
    }

    @Test
    void nullAndEmptyInput() throws Exception {
        assertNull(READER.read("null"));
        assertThrows(BindingException.class, () -> READER.read("{}"));
    }
}
