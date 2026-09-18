package org.sjf4j.binding.creator;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** NodeCreator constructor and factory cases structurally adapted from Jackson creator tests. */
public abstract class CreatorDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Structural source: CreatorPropertyConstraintsTest#testRequiredAnnotatedParam; SJF4J has no required flag, so a missing primitive uses its Java default. */
    @Test void testRequiredAnnotatedParam() {
        ConstructorBean bean = (ConstructorBean) binding(StreamingContext.EMPTY).readNode("{\"name\":\"han\"}", ConstructorBean.class);
        assertEquals("han", bean.name); assertEquals(0, bean.age);
    }
    /** Structural source: CreatorPropertyConstraintsTest#testRequiredAnnotatedParam; verifies SJF4J's out-of-order NodeCreator arguments. */
    @Test void testCreatorArgumentsInJsonOrder() {
        ConstructorBean bean = (ConstructorBean) binding(StreamingContext.EMPTY).readNode("{\"age\":7,\"name\":\"han\"}", ConstructorBean.class);
        assertEquals("han", bean.name); assertEquals(7, bean.age);
    }
    /** Structural source: PropertyAliasTest#testSimpleAliases; NodeProperty aliases replace JsonAlias on a creator parameter. */
    @Test void testCreatorParameterAlias() {
        ConstructorBean bean = (ConstructorBean) binding(StreamingContext.EMPTY).readNode("{\"n\":\"han\",\"age\":7}", ConstructorBean.class);
        assertEquals("han", bean.name); assertEquals(7, bean.age);
    }
    /** Structural source: PropertyAliasTest#testAliasInFactoryMethod; NodeCreator factory aliases replace JsonAlias. */
    @Test void testAliasInFactoryMethod() {
        FactoryBean bean = (FactoryBean) binding(StreamingContext.EMPTY).readNode("{\"userId\":7,\"label\":\"alpha\"}", FactoryBean.class);
        assertEquals(7, bean.id); assertEquals("alpha", bean.label);
    }
    /** Structural source: CreatorPropertyConstraintsTest#testRequiredGloballyParam; SJF4J has no global missing-creator-property configuration. */
    @Test void testMissingCreatorReferenceArgument() {
        ReferenceBean bean = (ReferenceBean) binding(StreamingContext.EMPTY).readNode("{\"name\":\"han\"}", ReferenceBean.class);
        assertEquals("han", bean.name); assertNull(bean.city);
    }
    static class ConstructorBean { final String name; final int age; @NodeCreator ConstructorBean(@NodeProperty(value = "name", aliases = "n") String name, @NodeProperty("age") int age) { this.name = name; this.age = age; } }
    static class FactoryBean { final int id; final String label; private FactoryBean(int id, String label) { this.id = id; this.label = label; } @NodeCreator static FactoryBean create(@NodeProperty(value = "id", aliases = "userId") int id, @NodeProperty("label") String label) { return new FactoryBean(id, label); } }
    static class ReferenceBean { final String name; final String city; @NodeCreator ReferenceBean(@NodeProperty("name") String name, @NodeProperty("city") String city) { this.name = name; this.city = city; } }
}
