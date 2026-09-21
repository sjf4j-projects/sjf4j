package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NamingStrategy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** NodeProperty alias and NodeBinding naming contracts structurally adapted from Jackson tests. */
public abstract class PropertyAliasDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Structural source: PropertyAliasTest#testSimpleAliases; NodeProperty aliases replace JsonAlias. */
    @Test void testSimpleAliases() {
        AliasBean bean = (AliasBean) binding(StreamingContext.EMPTY).readNode("{\"Name\":\"Foobar\"}", AliasBean.class);
        assertEquals("Foobar", bean.name);
    }
    /** Structural source: PropertyAliasTest#testAliasDeserializedToLastMatchingKey_ascendingKeys; NodeProperty aliases preserve input-order overwrite. */
    @Test void testAliasDeserializedToLastMatchingKeyAscendingKeys() {
        AliasBean bean = (AliasBean) binding(StreamingContext.EMPTY).readNode("{\"n\":\"first\",\"legacy_name\":\"last\"}", AliasBean.class);
        assertEquals("last", bean.name);
    }
    /** Structural source: PropertyAliasTest#testNoAliasNameInSerialization; verifies NodeProperty's primary output name, not JsonIgnore interaction. */
    @Test void testNoAliasNameInSerialization() {
        AliasBean bean = new AliasBean(); bean.name = "han";
        Map<?, ?> output = (Map<?, ?>) binding(StreamingContext.EMPTY).readNode(binding(StreamingContext.EMPTY).writeNodeAsString(bean), Map.class);
        assertEquals("han", output.get("name")); assertFalse(output.containsKey("legacy_name"));
    }
    /** Structural source: CreatorWithNamingStrategyTest#testSnakeCaseWithOneArg; NodeBinding applies naming to a field instead of a creator parameter. */
    @Test void testSnakeCaseWithOneArg() {
        SnakeBean bean = (SnakeBean) binding(StreamingContext.EMPTY).readNode("{\"user_name\":\"han\"}", SnakeBean.class);
        assertEquals("han", bean.userName);
    }
    static class AliasBean { @NodeProperty(value = "name", aliases = {"Name", "n", "legacy_name"}) public String name; }
    @NodeObject(naming = NamingStrategy.SNAKE_CASE) static class SnakeBean { public String userName; }
}
