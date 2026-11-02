package org.sjf4j.testbench.processor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.*;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.annotation.node.NodeProperty;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CompiledBindingDirectionTest {

    public static class User {
        @NodeProperty(bindingRead = false)
        public String generated = "server";
        @NodeProperty(bindingWrite = false)
        public String password;
        @NodeIgnore
        public String internal;
        public String name;
    }

    public interface Access {
        User read(String source) throws IOException;
        String write(User value) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface Simple extends Access {
        @Override @ReadFrom User read(String source) throws IOException;
        @Override @WriteTo String write(User value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2 extends Access {
        @Override @ReadFrom User read(String source) throws IOException;
        @Override @WriteTo String write(User value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3 extends Access {
        @Override @ReadFrom User read(String source) throws IOException;
        @Override @WriteTo String write(User value) throws IOException;
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface Gson extends Access {
        @Override @ReadFrom User read(String source) throws IOException;
        @Override @WriteTo String write(User value) throws IOException;
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2 extends Access {
        @Override @ReadFrom User read(String source) throws IOException;
        @Override @WriteTo String write(User value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface Jsonp extends Access {
        @Override @ReadFrom User read(String source) throws IOException;
        @Override @WriteTo String write(User value) throws IOException;
    }

    static Stream<Access> backends() {
        return Stream.of(
                CompiledInstances.create(Simple.class),
                CompiledInstances.create(Jackson2.class),
                CompiledInstances.create(Jackson3.class),
                CompiledInstances.create(Gson.class),
                CompiledInstances.create(Fastjson2.class),
                CompiledInstances.create(Jsonp.class));
    }

    @ParameterizedTest
    @MethodSource("backends")
    void bindingDirectionsDoNotChangeStructuralVisibility(Access binder) throws Exception {
        User user = binder.read("{\"generated\":\"incoming\",\"password\":\"secret\",\"internal\":\"hidden\",\"name\":\"Ada\"}");
        assertEquals("server", user.generated);
        assertEquals("secret", user.password);
        assertNull(user.internal);
        assertEquals("Ada", user.name);
        String json = binder.write(user);
        assertTrue(json.contains("generated"), json);
        assertTrue(json.contains("name"), json);
        assertFalse(json.contains("password"), json);
        assertFalse(json.contains("internal"), json);
    }
}
