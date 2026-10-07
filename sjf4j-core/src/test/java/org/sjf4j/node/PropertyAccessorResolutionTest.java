package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.exception.NodeException;
import org.sjf4j.annotation.node.PropertyStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyAccessorResolutionTest {

    static class BooleanAccessorPojo {
        public boolean getActive() {
                return false;
            }
        public boolean isActive() {
                return true;
            }
    }

    static class ParentGetterPojo {
        public String getName() {
                return "parent";
            }
    }

    static class ChildGetterPojo extends ParentGetterPojo {
        @Override
        public String getName() {
                return "child";
            }
    }

    static class SetterAnchorPojo {
        private String value;
        public String getValue() {
                return value;
            }
        public void setValue(Object value) {
                this.value = String.valueOf(value);
            }
        public void setValue(String value) {
                this.value = value;
            }
    }

    static class AmbiguousSetterPojo {
        public void setValue(Integer value) {}
        public void setValue(Long value) {}
    }

    static class ParentIgnoredGetterPojo {
        @NodeIgnore
        public String getName() {
                return "parent";
            }
    }

    static class ChildVisibleGetterPojo extends ParentIgnoredGetterPojo {
        @Override
        public String getName() {
                return "child";
            }
    }

    static class ParentSetterOverloadPojo {
        public void setValue(Object value) {}
    }

    static class ChildSetterOverloadPojo extends ParentSetterOverloadPojo {
        public void setValue(String value) {}
    }

    @NodeObject(propertyStrategy = PropertyStrategy.BEAN_FIELD)
    static class BeanFieldTypePriorityPojo {
        private Object value;
        public String getValue() {
                return (String) value;
            }
        public void setValue(String value) {
                this.value = value;
            }
    }

    @NodeObject(propertyStrategy = PropertyStrategy.FIELD_BEAN)
    static class FieldBeanTypePriorityPojo {
        private Object value;
        public String getValue() {
                return (String) value;
            }
        public void setValue(String value) {
                this.value = value;
            }
    }

    static class IncompatibleAccessorPojo {
        public String getValue() {
                return "x";
            }
        public void setValue(Integer value) {}
    }

    @Test
    void booleanIsGetterBeatsGetGetter() {
        PropertyInfo pi = TypeRegistry.requireRegisteredPojoInfo(BooleanAccessorPojo.class).propertyLookup.get("active");
        assertTrue((Boolean) pi.invokeGetter(new BooleanAccessorPojo()));
    }

    @Test
    void subclassGetterBeatsParentGetter() {
        PropertyInfo pi = TypeRegistry.requireRegisteredPojoInfo(ChildGetterPojo.class).propertyLookup.get("name");
        assertEquals("child", pi.invokeGetter(new ChildGetterPojo()));
    }

    @Test
    void overloadedSetterFailsFastEvenWithGetterAnchor() {
        NodeException ex = assertThrows(NodeException.class,
                () -> TypeRegistry.requireRegisteredPojoInfo(SetterAnchorPojo.class));
        assertTrue(ex.getMessage().contains("ambiguous setter"));
    }

    @Test
    void ambiguousSetterFailsFast() {
        NodeException ex = assertThrows(NodeException.class,
                () -> TypeRegistry.requireRegisteredPojoInfo(AmbiguousSetterPojo.class));
        assertTrue(ex.getMessage().contains("ambiguous setter"));
    }

    @Test
    void parentIgnoreDoesNotHideChildOverrideGetter() {
        PropertyInfo pi = TypeRegistry.requireRegisteredPojoInfo(ChildVisibleGetterPojo.class).propertyLookup.get("name");
        assertEquals("child", pi.invokeGetter(new ChildVisibleGetterPojo()));
    }

    @Test
    void childSetterOverloadDoesNotSilentlyOverrideParentSetter() {
        NodeException ex = assertThrows(NodeException.class,
                () -> TypeRegistry.requireRegisteredPojoInfo(ChildSetterOverloadPojo.class));
        assertTrue(ex.getMessage().contains("ambiguous setter"));
    }

    @Test
    void mergedPropertyTypeFollowsStrategyPriority() {
        assertEquals(String.class,
                Types.rawClazz(TypeRegistry.requireRegisteredPojoInfo(BeanFieldTypePriorityPojo.class)
                        .propertyLookup.get("value").type));
        assertEquals(Object.class,
                Types.rawClazz(TypeRegistry.requireRegisteredPojoInfo(FieldBeanTypePriorityPojo.class)
                        .propertyLookup.get("value").type));
    }

    @Test
    void incompatibleAccessorTypesFailFast() {
        NodeException ex = assertThrows(NodeException.class,
                () -> TypeRegistry.requireRegisteredPojoInfo(IncompatibleAccessorPojo.class));
        assertTrue(ex.getMessage().contains("incompatible getter/setter types"));
    }
}
