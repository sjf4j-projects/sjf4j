package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.NodeException;
import org.sjf4j.annotation.node.PropertyStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyStrategyBindingTest {

    static class DefaultBeanFieldPojo {
        private String name;
        public String getName() {
                return name;
            }
        public void setName(String name) {
                this.name = name;
            }
    }

    @NodeObject(propertyStrategy = PropertyStrategy.BEAN_ONLY)
    static class BeanOnlyPojo {
        public String fieldOnly;
        private String beanName;
        public String getBeanName() {
                return beanName;
            }
        public void setBeanName(String beanName) {
                this.beanName = beanName;
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
    static class GetterOnlyWithFieldFallbackPojo {
        @NodeProperty("name")
        private String name;
        public String getName() {
                return name;
            }
    }

    @NodeObject(propertyStrategy = PropertyStrategy.BEAN_ONLY)
    static class SetterOnlyPojo {
        private String name;
        public void setName(String name) {
                this.name = name;
            }
        String peek() {
                return name;
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

    @Test
    void defaultBeanFieldFindsBeanProperty() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(DefaultBeanFieldPojo.class);
        assertEquals(PropertyStrategy.BEAN_FIELD, pi.propertyStrategy);
        assertTrue(pi.properties.containsKey("name"));
        assertTrue(pi.properties.get("name").hasGetter());
        assertTrue(pi.properties.get("name").hasSetter());
    }

    @Test
    void beanOnlyIgnoresFieldOnlyMembers() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(BeanOnlyPojo.class);
        assertTrue(pi.properties.containsKey("beanName"));
        assertFalse(pi.properties.containsKey("fieldOnly"));
    }

    @Test
    void fieldOnlyIncludesPrivateFieldAndIgnoresGetter() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(FieldOnlyPojo.class);
        assertNotNull(pi.properties.get("name"));
        assertFalse(pi.readableProperties.isEmpty());
    }

    @Test
    void getterOnlyCanUseExplicitFieldAsWriterFallback() {
        GetterOnlyWithFieldFallbackPojo pojo = Sjf4j.global().fromJson("{\"name\":\"han\"}", GetterOnlyWithFieldFallbackPojo.class);
        assertEquals("han", pojo.getName());
    }

    @Test
    void setterOnlyIsWritableButNotReadable() {
        SetterOnlyPojo pojo = Sjf4j.global().fromJson("{\"name\":\"han\"}", SetterOnlyPojo.class);
        assertEquals("han", pojo.peek());
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(SetterOnlyPojo.class);
        assertTrue(pi.properties.containsKey("name"));
        assertFalse(pi.readableProperties.containsKey("name"));
        assertEquals("{}", Sjf4j.global().toJsonString(pojo));
    }

    @Test
    void fieldBeanIncludesPrivateFieldFamilies() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(FieldBeanPojo.class);
        assertTrue(pi.properties.containsKey("name"));
        assertTrue(pi.properties.get("name").hasGetter());
        assertTrue(pi.properties.get("name").hasSetter());
    }

    @Test
    void methodRenameMergesPropertyFamily() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(MethodRenamePojo.class);
        assertTrue(pi.properties.containsKey("nick"));
        assertFalse(pi.properties.containsKey("name"));
        MethodRenamePojo pojo = Sjf4j.global().fromJson("{\"nick\":\"x\"}", MethodRenamePojo.class);
        assertEquals("x", pojo.getName());
    }

    @Test
    void alignedCreatorRenameUsesFinalPropertyNameOnly() {
        MethodRenameAlignedCreatorPojo pojo = Sjf4j.global().fromJson("{\"nick\":\"x\"}", MethodRenameAlignedCreatorPojo.class);
        assertEquals("x", pojo.getName());

        MethodRenameAlignedCreatorPojo same = Sjf4j.global().fromJson("{\"name\":\"x\"}", MethodRenameAlignedCreatorPojo.class);
        assertNull(same.getName());
    }

    @Test
    void creatorRenameMustMatchFinalPropertyName() {
        assertThrows(NodeException.class,
                () -> TypeRegistry.registerPojoOrElseThrow(MethodRenameCreatorPojo.class));
    }

    @Test
    void nodeIgnoreRemovesThatPropertySourceOnly() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(IgnorePojo.class);
        assertFalse(pi.properties.containsKey("ignoredField"));
        assertTrue(pi.properties.containsKey("name"));
        assertFalse(pi.properties.get("name").hasGetter());
        assertTrue(pi.properties.get("name").hasSetter());
    }
}
