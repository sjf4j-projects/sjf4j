package org.sjf4j.binding.beans;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Ordinary and root POJO contracts sourced from Jackson's default bean reads. */
public abstract class BeanDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);

    /** Direct source: bean/BeanDeserializerVanillaTest#allKnownProperties. */
    @Test void readsAllOrdinaryPojoPropertiesAtTheRoot() {
        Wide bean = (Wide) binding(StreamingContext.EMPTY).readNode(
                "{\"text\":\"hello\",\"count\":42,\"enabled\":true,\"point\":{\"x\":1,\"y\":2},"
                        + "\"points\":[{\"x\":3,\"y\":4}],\"numbers\":[7,8],\"attributes\":{\"key\":\"value\"}}", Wide.class);
        assertEquals("hello", bean.getText());
        assertEquals(42, bean.count);
        assertEquals(true, bean.enabled);
        assertEquals(1, bean.point.x);
        assertEquals(4, bean.points.get(0).y);
        assertEquals(8, bean.numbers[1]);
        assertEquals("value", bean.attributes.get("key"));
    }

    /** Direct source: bean/BeanDeserializerVanillaTest#emptyObject. */
    @Test void preservesJavaDefaultsForAnEmptyRootObject() {
        Wide bean = (Wide) binding(StreamingContext.EMPTY).readNode("{}", Wide.class);
        assertNull(bean.getText());
        assertEquals(-1, bean.count);
        assertNull(bean.point);
    }

    /** Direct source: BeanPropertyDeserTest#testSimpleAutoDetect; focused public-field behavior. */
    @Test void bindsPublicFields() {
        AccessorBean bean = (AccessorBean) binding(StreamingContext.EMPTY).readNode(
                "{\"id\":7}", AccessorBean.class);
        assertEquals(7, bean.id);
    }

    /** Retained SJF4J coverage for ordinary JavaBean setter binding. */
    @Test void bindsJavaBeanSetters() {
        AccessorBean bean = (AccessorBean) binding(StreamingContext.EMPTY).readNode(
                "{\"name\":\"Ada\"}", AccessorBean.class);
        assertEquals("Ada", bean.getName());
    }

    /** Retained SJF4J coverage for primitive JavaBean setter binding formerly covered by FieldBinderTest. */
    @Test void bindsPrimitiveJavaBeanSetters() {
        AgeBean bean = (AgeBean) binding(StreamingContext.EMPTY).readNode("{\"age\":42}", AgeBean.class);
        assertEquals(42, bean.getAge());
    }

    /** Direct source: bean/BeanDeserializerVanillaTest#unknownPropertiesInterleaved. */
    @Test void skipsUnknownStructuredPropertiesAndContinuesBinding() {
        Wide bean = (Wide) binding(StreamingContext.EMPTY).readNode(
                "{\"unknown\":{\"count\":\"not-a-number\"},\"count\":3,\"alsoUnknown\":[1],\"text\":\"kept\"}", Wide.class);
        assertEquals(3, bean.count);
        assertEquals("kept", bean.getText());
    }

    /** Structural source: NullHandlingDeserTest#testNull; retained SJF4J root-null POJO behavior. */
    @Test void readsNullAtTheRootForAPojoTarget() {
        assertNull(binding(StreamingContext.EMPTY).readNode("null", Wide.class));
    }

    /** Retained source-derived failure: BeanDeserializerTest#testAbstractFailure rejects the target, but SJF4J leaks InstantiationError outside ordinary binding exceptions. */
    @Test void rejectsAnAbstractRootPojoTarget() {
        assertThrows(RuntimeException.class, () -> binding(StreamingContext.EMPTY).readNode("{\"x\":3}", AbstractBean.class));
    }

    static class Point { public int x; public int y; }
    static class Wide {
        private String text;
        public int count = -1;
        public boolean enabled;
        public Point point;
        public List<Point> points;
        public int[] numbers;
        public Map<String, String> attributes;
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
    static class AccessorBean {
        public int id;
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
    static class AgeBean {
        private int age;
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }
    abstract static class AbstractBean { public int x; }
}
