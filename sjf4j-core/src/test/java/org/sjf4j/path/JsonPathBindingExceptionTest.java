package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.node.NodeStream;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class JsonPathBindingExceptionTest {
    static class ThrowingJsonObject extends JsonObject {
        BindingException failure;

        @Override
        public Object getNode(String key) { throw failure; }
    }

    @Test
    void rejectsNullClassTargetsEvenForNullNodes() {
        JsonPath path = JsonPath.parse("$.missing");
        assertThrowsExactly(NullPointerException.class, () -> path.get(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> path.getAs(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> path.find(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class, () -> path.eval(null, (Class<Object>) null));
        assertThrowsExactly(NullPointerException.class,
                () -> NodeStream.of(Collections.emptyList()).getByPath("$.missing", null));
    }

    @Test
    void jsonPathReportsBindingErrorsForUnsupportedBindingTargets() {
        JsonObject document = JsonObject.of(
                "value", 1,
                "object", JsonObject.of("value", 1),
                "values", JsonArray.of(1),
                "objects", JsonArray.of(JsonObject.of("id", 1))
        );

        assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").get(document, Thread.class));
        assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").getAs(document, Thread.class));
        assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.object").getMap(document, Thread.class));
        assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.values").getList(document, Thread.class));
        assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.values").getArray(document, Thread.class));
        assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.values").getSet(document, Thread.class));
        assertThrowsExactly(BindingException.class,
                () -> JsonPath.parse("$.value").eval(document, Thread.class));
        assertThrowsExactly(BindingException.class,
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
