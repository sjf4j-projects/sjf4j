package org.sjf4j.obnt;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeStream;
import org.sjf4j.Nodes;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class ExceptionRefactorTest {

    enum State { ON, OFF }

    static class ThrowingJsonObject extends JsonObject {
        BindingException failure;

        @Override
        public Object getNode(String key) {
            throw failure;
        }
    }

    @Test
    void typedTargetsAreRequiredBeforeNullOrMissingValues() {
        assertThrowsExactly(NullPointerException.class, () -> Nodes.to(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.as(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.to(null, (TypeReference<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toMap(null, null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toList(null, null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toArray(null, null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toSet(null, null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toPojo(null, null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toJojo(null, null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toJajo(null, null));

        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        assertThrowsExactly(NullPointerException.class, () -> object.get("missing", (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> object.getMap("missing", null));
        assertThrowsExactly(NullPointerException.class, () -> array.get(0, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> array.getList(0, null));
        assertThrowsExactly(NullPointerException.class, () -> array.toArray(null));

        JsonPath path = JsonPath.parse("$.missing");
        assertThrowsExactly(NullPointerException.class, () -> path.get(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> path.getAs(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> path.find(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> path.eval(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class,
                () -> object.getByPath("$.missing", (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class,
                () -> NodeStream.of(Collections.emptyList()).getByPath("$.missing", null));
    }

    @Test
    void enumAndFactoryDefinitionErrorsUseTheirDefinedTypes() {
        assertThrowsExactly(NullPointerException.class, () -> Nodes.toEnum(null, null));
        assertThrowsExactly(NullPointerException.class, () -> Nodes.asEnum(null, null));
        assertNull(Nodes.toEnum(null, State.class));
        assertEquals(State.ON, Nodes.toEnum(State.ON, State.class));
        assertNull(Nodes.asEnum("missing", State.class));

        NodeException invalid = assertThrowsExactly(NodeException.class,
                () -> Nodes.toEnum("missing", State.class));
        assertInstanceOf(IllegalArgumentException.class, invalid.getCause());

        assertThrowsExactly(IllegalArgumentException.class, () -> JsonObject.of("key"));
        assertThrowsExactly(IllegalArgumentException.class, () -> JsonObject.of(1, "value"));
        assertEquals(0, JsonObject.of((Object[]) null).size());
        assertThrowsExactly(NullPointerException.class, () -> CompiledInstances.of(null));
        assertThrowsExactly(IllegalArgumentException.class, () -> CompiledInstances.of(String.class));
    }

    @Test
    void jsonPathReportsNodeErrorsForUnsupportedBindingTargets() {
        JsonObject document = JsonObject.of(
                "value", 1,
                "object", JsonObject.of("value", 1),
                "values", JsonArray.of(1),
                "objects", JsonArray.of(JsonObject.of("id", 1))
        );

        assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.value").get(document, Thread.class));
        assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.value").getAs(document, Thread.class));
        assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.object").getMap(document, Thread.class));
        assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.values").getList(document, Thread.class));
        assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.values").getArray(document, Thread.class));
        assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.values").getSet(document, Thread.class));
        assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.value").eval(document, Thread.class));
        assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.value").evalAs(document, Thread.class));

        ThrowingJsonObject throwing = new ThrowingJsonObject();
        BindingException unpathed = new BindingException("read failure");
        throwing.failure = unpathed;
        BindingException read = assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").get(throwing, String.class));
        assertFalse(read.hasPathSegment());
        assertSame(unpathed, read.getCause());
        assertFalse(assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").getAs(throwing, String.class)).hasPathSegment());
        assertFalse(assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").eval(throwing, String.class)).hasPathSegment());
        assertFalse(assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").evalAs(throwing, String.class)).hasPathSegment());

        BindingException pathed = new BindingException("nested failure",
                new PathSegment.Name(PathSegment.Root.INSTANCE, "nested"));
        throwing.failure = pathed;
        assertSame(pathed, assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").get(throwing, String.class)));
        assertSame(pathed, assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").eval(throwing, String.class)));

        NodeException structural = assertThrowsExactly(NodeException.class,
                () -> JsonPath.parse("$.value").getString(document));
        assertEquals(NodeException.class, structural.getClass());
    }
}
