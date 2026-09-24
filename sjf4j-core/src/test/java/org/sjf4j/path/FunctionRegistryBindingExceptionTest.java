package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.BindingException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class FunctionRegistryBindingExceptionTest {

    @Test
    void evalAndEvalAsPropagateFunctionBindingExceptionUnchanged() {
        PathSegment segment = new PathSegment.Name(PathSegment.Root.INSTANCE, "failed");
        IllegalStateException cause = new IllegalStateException("cause");
        BindingException expected = new BindingException("failed", segment, cause);
        FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("throwsBindingException", (target, args) -> {
            throw expected;
        }));

        JsonPath path = JsonPath.parse("$.throwsBindingException()");

        BindingException eval = assertThrowsExactly(BindingException.class,
                () -> path.eval(JsonObject.of(), String.class));
        assertSame(expected, eval);
        assertSame(segment, eval.getPathSegment());
        assertSame(cause, eval.getCause());

        BindingException evalAs = assertThrowsExactly(BindingException.class,
                () -> path.evalAs(JsonObject.of(), String.class));
        assertSame(expected, evalAs);
        assertSame(segment, evalAs.getPathSegment());
        assertSame(cause, evalAs.getCause());
    }
}
