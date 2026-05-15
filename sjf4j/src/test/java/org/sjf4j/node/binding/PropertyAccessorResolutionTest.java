package org.sjf4j.node.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.PropertyStrategy;
import org.sjf4j.node.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyAccessorResolutionTest {

    static class BooleanAccessorPojo {
        public boolean getActive() { return false; }
        public boolean isActive() { return true; }
    }

    static class ParentGetterPojo {
        public String getName() { return "parent"; }
    }

    static class ChildGetterPojo extends ParentGetterPojo {
        @Override
        public String getName() { return "child"; }
    }

    static class SetterAnchorPojo {
        private String value;
        public String getValue() { return value; }
        public void setValue(Object value) { this.value = String.valueOf(value); }
        public void setValue(String value) { this.value = value; }
    }

    static class AmbiguousSetterPojo {
        public void setValue(Integer value) {}
        public void setValue(Long value) {}
    }

    static class ParentIgnoredGetterPojo {
        @NodeIgnore
        public String getName() { return "parent"; }
    }

    static class ChildVisibleGetterPojo extends ParentIgnoredGetterPojo {
        @Override
        public String getName() { return "child"; }
    }

    static class ParentSetterOverloadPojo {
        public void setValue(Object value) {}
    }

    static class ChildSetterOverloadPojo extends ParentSetterOverloadPojo {
        public void setValue(String value) {}
    }

    @NodeBinding(propertyStrategy = PropertyStrategy.BEAN_FIELD)
    static class BeanFieldTypePriorityPojo {
        private Object value;
        public String getValue() { return (String) value; }
        public void setValue(String value) { this.value = value; }
    }

    @NodeBinding(propertyStrategy = PropertyStrategy.FIELD_BEAN)
    static class FieldBeanTypePriorityPojo {
        private Object value;
        public String getValue() { return (String) value; }
        public void setValue(String value) { this.value = value; }
    }

    static class IncompatibleAccessorPojo {
        public String getValue() { return "x"; }
        public void setValue(Integer value) {}
    }

    @Test
    void booleanIsGetterBeatsGetGetter() {
        NodeRegistry.PropertyInfo pi = NodeRegistry.registerPojoOrElseThrow(BooleanAccessorPojo.class).properties.get("active");
        assertTrue((Boolean) pi.invokeGetter(new BooleanAccessorPojo()));
    }

    @Test
    void subclassGetterBeatsParentGetter() {
        NodeRegistry.PropertyInfo pi = NodeRegistry.registerPojoOrElseThrow(ChildGetterPojo.class).properties.get("name");
        assertEquals("child", pi.invokeGetter(new ChildGetterPojo()));
    }

    @Test
    void overloadedSetterFailsFastEvenWithGetterAnchor() {
        JsonException ex = assertThrows(JsonException.class,
                () -> NodeRegistry.registerPojoOrElseThrow(SetterAnchorPojo.class));
        assertTrue(ex.getMessage().contains("ambiguous setter"));
    }

    @Test
    void ambiguousSetterFailsFast() {
        JsonException ex = assertThrows(JsonException.class,
                () -> NodeRegistry.registerPojoOrElseThrow(AmbiguousSetterPojo.class));
        assertTrue(ex.getMessage().contains("ambiguous setter"));
    }

    @Test
    void parentIgnoreDoesNotHideChildOverrideGetter() {
        NodeRegistry.PropertyInfo pi = NodeRegistry.registerPojoOrElseThrow(ChildVisibleGetterPojo.class).properties.get("name");
        assertEquals("child", pi.invokeGetter(new ChildVisibleGetterPojo()));
    }

    @Test
    void childSetterOverloadDoesNotSilentlyOverrideParentSetter() {
        JsonException ex = assertThrows(JsonException.class,
                () -> NodeRegistry.registerPojoOrElseThrow(ChildSetterOverloadPojo.class));
        assertTrue(ex.getMessage().contains("ambiguous setter"));
    }

    @Test
    void mergedPropertyTypeFollowsStrategyPriority() {
        assertEquals(String.class,
                Types.rawClazz(NodeRegistry.registerPojoOrElseThrow(BeanFieldTypePriorityPojo.class)
                        .properties.get("value").type));
        assertEquals(Object.class,
                Types.rawClazz(NodeRegistry.registerPojoOrElseThrow(FieldBeanTypePriorityPojo.class)
                        .properties.get("value").type));
    }

    @Test
    void incompatibleAccessorTypesFailFast() {
        JsonException ex = assertThrows(JsonException.class,
                () -> NodeRegistry.registerPojoOrElseThrow(IncompatibleAccessorPojo.class));
        assertTrue(ex.getMessage().contains("incompatible getter/setter types"));
    }
}
