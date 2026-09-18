package org.sjf4j.binding.access;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.PropertyStrategy;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Source-derived access behavior that has a public SJF4J equivalent. */
public abstract class AccessDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);

    /** Retained SJF4J structural coverage (no Jackson source): unknown values are deliberately discarded. */
    @Test void testCreatorUnknownFieldsDoNotPreventLaterCreatorAndSetterValues() {
        CreatorBean bean = (CreatorBean) binding(StreamingContext.EMPTY).readNode("{\"unknown\":{\"x\":1},\"name\":\"Ada\",\"city\":\"London\"}", CreatorBean.class);
        assertEquals("Ada", bean.name); assertEquals("London", bean.city);
    }

    /** Structural source: JsonIgnorePropertiesDeserTest#testIssue426; NodeIgnore is SJF4J's explicit ignored-member equivalent. */
    @Test void testNodeIgnoreLeavesTheExistingFieldValueUntouched() {
        IgnoredBean bean = (IgnoredBean) binding(StreamingContext.EMPTY).readNode("{\"userId\":9,\"firstName\":\"Mike\"}", IgnoredBean.class);
        assertNull(bean.userId); assertEquals("Mike", bean.firstName);
    }

    /** Structural source: ReadOnlyDeserTest#testReadOnlyProps95; a getter-only property is not writable. */
    @Test void testGetterOnlyPropertyIsIgnoredWithoutConvertingItsValue() {
        GetterOnly bean = (GetterOnly) binding(StreamingContext.EMPTY).readNode("{\"values\":[\"not-an-integer\"],\"name\":\"Ada\"}", GetterOnly.class);
        assertEquals(List.of(), bean.getValues()); assertEquals("Ada", bean.name);
    }

    /** Retained SJF4J structural coverage (no Jackson source): bean setter access is selected over a matching field. */
    @Test void testBeanFieldAndSetterAccessUseOnePropertyFamily() {
        BeanAndField bean = (BeanAndField) binding(StreamingContext.EMPTY).readNode("{\"name\":\"Ada\"}", BeanAndField.class);
        assertEquals("setter:Ada", bean.getName()); assertEquals("field", bean.name);
    }

    /** Retained SJF4J structural coverage (no Jackson source): a public field remains writable without bean accessors. */
    @Test void testPublicFieldAccessWithoutBeanAccessors() {
        FieldOnly bean = (FieldOnly) binding(StreamingContext.EMPTY).readNode("{\"name\":\"Ada\"}", FieldOnly.class);
        assertEquals("Ada", bean.name);
    }

    /** Retained SJF4J structural coverage (no Jackson source): duplicate aliases retain input order. */
    @Test void testDuplicateAliasCollisionUsesTheLastInputValue() {
        AliasBean bean = (AliasBean) binding(StreamingContext.EMPTY).readNode("{\"oldName\":\"first\",\"name\":\"last\"}", AliasBean.class);
        assertEquals("last", bean.name);
    }

    /** Structural source: ReadOnlyListDeserTest#testAccessReadOnly2118; readDynamic is the public SJF4J dynamic-property policy. */
    @Test void testReadDynamicRetainsOnlyTheRealDynamicEquivalent() {
        DynamicBean bean = (DynamicBean) binding(StreamingContext.EMPTY).readNode("{\"id\":1,\"extra\":2}", DynamicBean.class);
        assertEquals(1, bean.id); assertNull(bean.getNode("extra"));
    }

    static class CreatorBean {
        final String name;
        String city;
        @NodeCreator CreatorBean(@NodeProperty("name") String name) { this.name = name; }
        public void setCity(String city) { this.city = city; }
    }
    static class IgnoredBean { @NodeIgnore public String userId; public String firstName; }
    static class GetterOnly { private final List<Integer> values = List.of(); public String name; public List<Integer> getValues() { return values; } }
    static class BeanAndField { public String name = "field"; private String setterValue; public String getName() { return setterValue; } public void setName(String value) { setterValue = "setter:" + value; } }
    static class FieldOnly { public String name; }
    static class AliasBean { @NodeProperty(value = "name", aliases = "oldName") public String name; }
    @NodeBinding(propertyStrategy = PropertyStrategy.BEAN_FIELD, readDynamic = false)
    static class DynamicBean extends JsonObject { public int id; }
}
