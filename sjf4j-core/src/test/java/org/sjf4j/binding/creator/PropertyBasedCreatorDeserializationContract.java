package org.sjf4j.binding.creator;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PropertyBasedCreatorDeserializationContract {
    private final SimpleJsonBinder binding = new SimpleJsonBinder(StreamingContext.EMPTY);

    // Structural source: TestCreators#testConstructorAndProps; NodeCreator/NodeProperty replace JsonCreator/JsonProperty.
    @Test void constructorCreatorAcceptsOutOfOrderPropertiesAndThenCallsSetter() {
        ConstructorAndSetter bean = (ConstructorAndSetter) binding.readNode("{\"enabled\":true,\"age\":7,\"name\":\"Ada\"}", ConstructorAndSetter.class);
        assertEquals("Ada", bean.name); assertEquals(7, bean.age); assertEquals(true, bean.enabled);
    }

    // Structural source: TestCreators#testFactoryAndProps; despite its name, the source uses a constructor followed by setters.
    @Test void constructorCreatorAcceptsArrayPropertiesAndThenCallsSetters() {
        ConstructorAndSetters bean = (ConstructorAndSetters) binding.readNode("{\"a\":[false,true,false],\"b\":2,\"c\":-1}", ConstructorAndSetters.class);
        assertEquals(2, bean.b); assertEquals(-1, bean.c); assertEquals(false, bean.a[0]); assertEquals(true, bean.a[1]); assertEquals(false, bean.a[2]);
    }

    // Structural source: CreatorNullPrimitivesTest#testCreatorAbsentPrimitiveShouldDefault; NodeCreator/NodeProperty replace JsonCreator/JsonProperty.
    @Test void absentPrimitiveCreatorParameterUsesJavaDefault() {
        NullDefaults bean = (NullDefaults) binding.readNode("{\"name\":\"Ada\"}", NullDefaults.class);
        assertEquals("Ada", bean.name); assertEquals(0, bean.age); assertNull(bean.city);
    }

    // Structural source: CreatorNullPrimitivesTest#testRequiredNonNullParam, first default-configuration branch.
    @Test void absentCreatorParametersUseJavaDefaults() {
        NullDefaults bean = (NullDefaults) binding.readNode("{}", NullDefaults.class);
        assertNull(bean.name); assertEquals(0, bean.age); assertNull(bean.city);
    }

    static class ConstructorAndSetter {
        final String name; final int age; boolean enabled;
        @NodeCreator ConstructorAndSetter(@NodeProperty("name") String name, @NodeProperty("age") int age) { this.name = name; this.age = age; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    static class ConstructorAndSetters {
        final boolean[] a; int b; int c;
        @NodeCreator ConstructorAndSetters(@NodeProperty("a") boolean[] a) { this.a = a; }
        public void setB(int b) { this.b = b; }
        public void setC(int c) { this.c = c; }
    }
    static class NullDefaults {
        final String name; final int age; final String city;
        @NodeCreator NullDefaults(@NodeProperty("name") String name, @NodeProperty("age") int age, @NodeProperty("city") String city) { this.name = name; this.age = age; this.city = city; }
    }
}
