package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDiscoveryTest {

    static class DefaultBeanFieldPojo {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    static class MethodRenamePojo {
        private String name;
        @NodeProperty("nick")
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    static class MethodRenameCreatorPojo {
        private final String name;

        MethodRenameCreatorPojo(@NodeProperty("name") String name) {
            this.name = name;
        }

        @NodeProperty("nick")
        public String getName() { return name; }
    }

    static class MethodRenameAlignedCreatorPojo {
        private final String name;

        MethodRenameAlignedCreatorPojo(@NodeProperty("nick") String name) {
            this.name = name;
        }

        @NodeProperty("nick")
        public String getName() { return name; }
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
        public IgnoredType getInfo() { return info; }
        public void setInfo(IgnoredType info) { this.info = info; }
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

    static class IgnorePojo {
        @NodeIgnore
        public String ignoredField;
        private String name;
        @NodeIgnore
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @NodeBinding(propertyStrategy = PropertyStrategy.FIELD_ONLY)
    static class AccessCompatPojo {
        private String name;
    }

    @NodeBinding(propertyStrategy = PropertyStrategy.BEAN_ONLY)
    static class BeanOnlyPojo {
        public String fieldOnly;
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @NodeBinding(propertyStrategy = PropertyStrategy.FIELD_ONLY)
    static class FieldOnlyPojo {
        private String name;
        public String getName() { return "getter"; }
    }

    @NodeBinding(propertyStrategy = PropertyStrategy.BEAN_FIELD)
    static class BeanFieldPojo {
        String hidden;
        public String publicField;
        public String getHidden() { return hidden; }
    }

    @NodeBinding(propertyStrategy = PropertyStrategy.FIELD_BEAN)
    static class FieldBeanPojo {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    static class PrivateBeanMethodPojo {
        private String hidden = "x";
        private String getHidden() { return hidden; }
    }

    static class CollidingRenamePojo {
        private String first;
        private String second;

        @NodeProperty("same")
        public String getFirst() { return first; }

        public void setFirst(String first) { this.first = first; }

        @NodeProperty("same")
        public String getSecond() { return second; }

        public void setSecond(String second) { this.second = second; }
    }

    static class FieldRenameToBeanImplicitPojo {
        @NodeProperty("name")
        private String userName;

        public String getName() { return userName; }
    }

    @Test
    void defaultIsBeanFieldAndFindsGetterSetter() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(DefaultBeanFieldPojo.class);
        assertEquals(PropertyStrategy.BEAN_FIELD, pi.propertyStrategy);
        assertNotNull(pi.properties.get("name"));
        assertTrue(pi.properties.get("name").hasGetter());
        assertTrue(pi.properties.get("name").hasSetter());
    }

    @Test
    void methodRenameMergesSinglePropertyFamily() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(MethodRenamePojo.class);
        assertNotNull(pi.properties.get("nick"));
        assertNull(pi.properties.get("name"));
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
        assertThrows(JsonException.class,
                () -> NodeRegistry.registerPojoOrElseThrow(MethodRenameCreatorPojo.class));
    }

    @Test
    void nodeIgnoreOnFieldAndMethodWorks() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(IgnorePojo.class);
        assertFalse(pi.properties.containsKey("ignoredField"));
        assertFalse(pi.properties.get("name").hasGetter());
    }

    @Test
    void nodeIgnoreTypeOnFieldExcludesProperty() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(TypeIgnoreContainer.class);
        assertEquals(1, pi.propertyCount);
        assertTrue(pi.properties.containsKey("name"));
        assertNull(pi.properties.get("address"));
    }

    @Test
    void nodeIgnoreTypeOnBeanMethodExcludesGetterSetter() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(TypeIgnoreBeanContainer.class);
        assertNull(pi.properties.get("info"));
    }

    @Test
    void nodeIgnoreTypeStillAllowsDirectPojoAnalysis() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(IgnoredType.class);
        assertNotNull(pi);
        assertTrue(pi.properties.containsKey("street"));
    }

    @Test
    void fieldOnlyAnnotationWorks() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(AccessCompatPojo.class);
        assertEquals(PropertyStrategy.FIELD_ONLY, pi.propertyStrategy);
        assertNotNull(pi.properties.get("name"));
    }

    @Test
    void strategySemanticsCoverage() {
        assertTrue(NodeRegistry.registerPojoOrElseThrow(BeanOnlyPojo.class).properties.containsKey("name"));
        assertFalse(NodeRegistry.registerPojoOrElseThrow(BeanOnlyPojo.class).properties.containsKey("fieldOnly"));

        assertTrue(NodeRegistry.registerPojoOrElseThrow(FieldOnlyPojo.class).properties.containsKey("name"));

        NodeRegistry.PojoInfo beanField = NodeRegistry.registerPojoOrElseThrow(BeanFieldPojo.class);
        assertTrue(beanField.properties.containsKey("hidden"));
        assertTrue(beanField.properties.containsKey("publicField"));

        NodeRegistry.PojoInfo fieldBean = NodeRegistry.registerPojoOrElseThrow(FieldBeanPojo.class);
        assertTrue(fieldBean.properties.get("name").hasGetter());
        assertTrue(fieldBean.properties.get("name").hasSetter());
    }

    @Test
    void defaultBeanFieldIgnoresPrivateImplicitBeanMethods() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(PrivateBeanMethodPojo.class);
        assertFalse(pi.properties.containsKey("hidden"));
    }

    @Test
    void conflictingFinalNamesFailFast() {
        assertThrows(JsonException.class, () -> NodeRegistry.registerPojoOrElseThrow(CollidingRenamePojo.class));
    }

    @Test
    void fieldRenameCanMergeIntoMatchingBeanImplicitFamily() {
        NodeRegistry.PojoInfo pi = NodeRegistry.registerPojoOrElseThrow(FieldRenameToBeanImplicitPojo.class);
        assertTrue(pi.properties.containsKey("name"));
        assertFalse(pi.properties.containsKey("userName"));
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
}
