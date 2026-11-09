package org.sjf4j.meta;

import org.junit.jupiter.api.Test;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.node.NodeWalker;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Object classification must not depend on the availability of a constructor.
 * Writing and inspecting an existing object differs from creating a new one.
 */
class NullablePojoCreatorTest {

    // Two parameterized constructors, with no explicit creator and no no-args
    // constructor. This remains non-constructible with or without -parameters.
    static final class ReadOnly {
        private final String value;

        ReadOnly(String value) {
            this.value = value;
        }

        ReadOnly(String value, int ignored) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    static final class EmptyWithoutCreator {
        EmptyWithoutCreator(String unused) {}
        EmptyWithoutCreator(int unused) {}
    }

    static final class SetterOnly {
        private int value;

        public void setValue(int value) {
            this.value = value;
        }

        int currentValue() {
            return value;
        }
    }

    static final class InvalidExplicitCreator {
        final String value;

        @NodeCreator
        InvalidExplicitCreator(String value) {
            this.value = value;
        }

        @NodeCreator
        InvalidExplicitCreator(String value, int ignored) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Test
    void nonConstructiblePojoHasMetadataAndSupportsWrites() {
        PojoInfo metadata = TypeRegistry.requireRegisteredPojoInfo(ReadOnly.class);
        assertNull(metadata.creatorInfo);
        assertEquals(1, metadata.gettableProperties.length);

        ReadOnly source = new ReadOnly("test");
        assertEquals("{\"value\":\"test\"}", new SimpleJsonBinder().writeNodeAsString(source));
        assertEquals("test", Nodes.toMap(source).get("value"));

        Map<String, Object> raw = Collections.<String, Object>singletonMap("value", "test");
        assertEquals("test", ((Map<?, ?>) Sjf4j.global().convertToRaw(source)).get("value"));
        assertThrows(BindingException.class, () -> Sjf4j.global().convert(raw, ReadOnly.class));
        assertThrows(BindingException.class, () -> Sjf4j.global().deepCopy(source));
        assertThrows(BindingException.class, () -> Nodes.copy(source));
        assertThrows(NodeException.class, () -> NodeWalker.createObjectNode(ReadOnly.class));
    }

    @Test
    void bindingReadChecksCreatorOnlyForNonNullObjects() {
        SimpleJsonBinder binder = new SimpleJsonBinder();

        ReadOnly instance = new ReadOnly("ok");
        assertEquals("{\"value\":\"ok\"}", binder.writeNodeAsString(instance));
        assertNull(binder.readNode("null", ReadOnly.class));

        BindingException error = assertThrows(BindingException.class,
                () -> binder.readNode("{\"value\":\"ok\"}", ReadOnly.class));
        assertTrue(error.getMessage().contains("no usable creator"), error::getMessage);
        assertEquals("{\"value\":\"ok\"}", binder.writeNodeAsString(instance));
    }

    @Test
    void bindingReadFailureFirstDoesNotPoisonWriterMetadata() {
        SimpleJsonBinder binder = new SimpleJsonBinder();
        assertThrows(BindingException.class, () -> binder.readNode("{}", EmptyWithoutCreator.class));
        assertEquals("{}", binder.writeNodeAsString(new EmptyWithoutCreator(1)));
        assertNotNull(TypeRegistry.requireRegisteredPojoInfo(EmptyWithoutCreator.class));
        assertNull(TypeRegistry.requireRegisteredPojoInfo(EmptyWithoutCreator.class).creatorInfo);
    }

    @Test
    void settableOnlyObjectCanBeReadAndWritesEmptyObject() {
        SimpleJsonBinder binder = new SimpleJsonBinder();
        SetterOnly target = binder.readNode("{\"value\":7}", SetterOnly.class);
        assertEquals(7, target.currentValue());
        assertEquals("{}", binder.writeNodeAsString(target));
    }

    @Test
    void invalidExplicitCreatorIsStillRejected() {
        BindingException error = assertThrows(BindingException.class,
                () -> TypeRegistry.registerTypeInfo(InvalidExplicitCreator.class));
        assertTrue(error.getMessage().contains("multiple creator"), error::getMessage);
    }

    @Test
    void thirdPartyTypesAreNoLongerExcludedSolelyByPackage() {
        assertTrue(ReflectUtil.isPojoCandidate(
                com.fasterxml.jackson.annotation.JsonIgnoreProperties.Value.class));
        assertFalse(ReflectUtil.isPojoCandidate(Thread.class));
    }
}
