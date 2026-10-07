package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.PropertyStrategy;
import org.sjf4j.exception.BindingException;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDiscoveryTest {

    static class DefaultBeanFieldPojo {
        private String name;
        public String getName() {
                return name;
            }
        public void setName(String name) {
                this.name = name;
            }
    }

    static class MethodRenamePojo {
        private String name;
        @NodeProperty("nick")
        public String getName() {
                return name;
            }
        public void setName(String name) {
                this.name = name;
            }
    }

    static class MethodRenameCreatorPojo {
        private final String name;

        MethodRenameCreatorPojo(@NodeProperty("name") String name) {
            this.name = name;
        }

        @NodeProperty("nick")
        public String getName() {
                return name;
            }
    }

    static class MethodRenameAlignedCreatorPojo {
        private final String name;

        MethodRenameAlignedCreatorPojo(@NodeProperty("nick") String name) {
            this.name = name;
        }

        @NodeProperty("nick")
        public String getName() {
                return name;
            }
    }

    @NodeIgnore
    static class IgnoredType {
        public String street;
    }

    static class TypeIgnoreContainer {
        public String name;
        public IgnoredType address;
    }

    static class TypeIgnoreBeanContainer {
        private IgnoredType info;
        public IgnoredType getInfo() {
                return info;
            }
        public void setInfo(IgnoredType info) {
                this.info = info;
            }
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

    static class IgnorePojo {
        @NodeIgnore
        public String ignoredField;
        private String name;
        @NodeIgnore
        public String getName() {
                return name;
            }
        public void setName(String name) {
                this.name = name;
            }
    }

    @NodeObject(propertyStrategy = PropertyStrategy.FIELD_ONLY)
    static class AccessCompatPojo {
        private String name;
    }

    @NodeObject(propertyStrategy = PropertyStrategy.BEAN_ONLY)
    static class BeanOnlyPojo {
        public String fieldOnly;
        private String name;
        public String getName() {
                return name;
            }
        public void setName(String name) {
                this.name = name;
            }
    }

    @NodeObject(propertyStrategy = PropertyStrategy.FIELD_ONLY)
    static class FieldOnlyPojo {
        private String name;
        public String getName() {
                return "getter";
            }
    }

    @NodeObject(propertyStrategy = PropertyStrategy.BEAN_FIELD)
    static class BeanFieldPojo {
        String hidden;
        public String publicField;
        public String getHidden() {
                return hidden;
            }
    }

    @NodeObject(propertyStrategy = PropertyStrategy.FIELD_BEAN)
    static class FieldBeanPojo {
        private String name;
        public String getName() {
                return name;
            }
        public void setName(String name) {
                this.name = name;
            }
    }

    static class PrivateBeanMethodPojo {
        private String hidden = "x";
        private String getHidden() {
                return hidden;
            }
    }

    static class CollidingRenamePojo {
        private String first;
        private String second;

        @NodeProperty("same")
        public String getFirst() {
                return first;
            }

        public void setFirst(String first) {
                this.first = first;
            }

        @NodeProperty("same")
        public String getSecond() {
                return second;
            }

        public void setSecond(String second) {
                this.second = second;
            }
    }

    static class FieldRenameToBeanImplicitPojo {
        @NodeProperty("name")
        private String userName;

        public String getName() {
                return userName;
            }
    }

    @Test
    void defaultIsBeanFieldAndFindsGetterSetter() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(DefaultBeanFieldPojo.class);
        assertNotNull(pi.propertyLookup.get("name"));
        assertTrue(pi.propertyLookup.get("name").readable);
        assertTrue(pi.propertyLookup.get("name").writable);
    }

    @Test
    void methodRenameMergesSinglePropertyFamily() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(MethodRenamePojo.class);
        assertNotNull(pi.propertyLookup.get("nick"));
        assertNull(pi.propertyLookup.get("name"));
        MethodRenamePojo pojo = Sjf4j.global().fromJson("{\"nick\":\"x\"}", MethodRenamePojo.class);
        assertEquals("x", pojo.getName());
    }

    @Test
    void alignedMethodRenameAlsoBindsCreatorArgument() {
        MethodRenameAlignedCreatorPojo pojo = Sjf4j.global().fromJson("{\"nick\":\"x\"}", MethodRenameAlignedCreatorPojo.class);
        assertEquals("x", pojo.getName());
    }

    @Test
    void methodRenameCreatorMustMatchFinalPropertyName() {
        assertThrowsExactly(BindingException.class,
                () -> TypeRegistry.requireRegisteredPojoInfo(MethodRenameCreatorPojo.class));
    }

    @Test
    void nodeIgnoreOnFieldAndMethodWorks() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(IgnorePojo.class);
        assertFalse(pi.propertyLookup.containsKey("ignoredField"));
        assertFalse(pi.propertyLookup.get("name").readable);
    }

    @Test
    void nodeIgnoreTypeOnFieldExcludesProperty() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(TypeIgnoreContainer.class);
        assertEquals(1, pi.readableProperties.length);
        assertTrue(pi.propertyLookup.containsKey("name"));
        assertNull(pi.propertyLookup.get("address"));
    }

    @Test
    void nodeIgnoreTypeOnBeanMethodExcludesGetterSetter() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(TypeIgnoreBeanContainer.class);
        assertNull(pi.propertyLookup.get("info"));
    }

    @Test
    void nodeIgnoreTypeStillAllowsDirectPojoAnalysis() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(IgnoredType.class);
        assertNotNull(pi);
        assertTrue(pi.propertyLookup.containsKey("street"));
    }

    @Test
    void fieldOnlyAnnotationWorks() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(AccessCompatPojo.class);
        assertNotNull(pi.propertyLookup.get("name"));
    }

    @Test
    void strategySemanticsCoverage() {
        assertTrue(TypeRegistry.requireRegisteredPojoInfo(BeanOnlyPojo.class).propertyLookup.containsKey("name"));
        assertFalse(TypeRegistry.requireRegisteredPojoInfo(BeanOnlyPojo.class).propertyLookup.containsKey("fieldOnly"));

        assertTrue(TypeRegistry.requireRegisteredPojoInfo(FieldOnlyPojo.class).propertyLookup.containsKey("name"));

        PojoInfo beanField = TypeRegistry.requireRegisteredPojoInfo(BeanFieldPojo.class);
        assertTrue(beanField.propertyLookup.containsKey("hidden"));
        assertTrue(beanField.propertyLookup.containsKey("publicField"));

        PojoInfo fieldBean = TypeRegistry.requireRegisteredPojoInfo(FieldBeanPojo.class);
        assertTrue(fieldBean.propertyLookup.get("name").readable);
        assertTrue(fieldBean.propertyLookup.get("name").writable);
    }

    @Test
    void defaultBeanFieldIgnoresPrivateImplicitBeanMethods() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(PrivateBeanMethodPojo.class);
        assertFalse(pi.propertyLookup.containsKey("hidden"));
    }

    @Test
    void conflictingFinalNamesFailFast() {
        assertThrowsExactly(BindingException.class, () -> TypeRegistry.requireRegisteredPojoInfo(CollidingRenamePojo.class));
    }

    @Test
    void fieldRenameCanMergeIntoMatchingBeanImplicitFamily() {
        PojoInfo pi = TypeRegistry.requireRegisteredPojoInfo(FieldRenameToBeanImplicitPojo.class);
        assertTrue(pi.propertyLookup.containsKey("name"));
        assertFalse(pi.propertyLookup.containsKey("userName"));
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
}
