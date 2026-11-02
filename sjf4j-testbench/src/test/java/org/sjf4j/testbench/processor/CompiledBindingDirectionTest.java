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

    public static class CreatorOnlyOption {
        private final String token;
        @org.sjf4j.annotation.node.NodeCreator
        public CreatorOnlyOption(@NodeProperty(value = "token", bindingWrite = false) String token) {
            this.token = token;
        }
        public String getToken() { return token; }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface CreatorOnlyBinder {
        @ReadFrom CreatorOnlyOption read(String json) throws IOException;
        @WriteTo String write(CreatorOnlyOption value) throws IOException;
    }

    @org.junit.jupiter.api.Test
    void creatorParameterCanExcludeOnlyBindingOutput() throws Exception {
        CreatorOnlyBinder binder = CompiledInstances.create(CreatorOnlyBinder.class);
        CreatorOnlyOption value = binder.read("{\"token\":\"abc\"}");
        assertEquals("abc", value.getToken());
        assertFalse(binder.write(value).contains("token"));
        assertFalse(org.sjf4j.Sjf4j.global().toJsonString(value).contains("token"));
    }

    public static class PrivateFieldUser {
        @NodeProperty(bindingWrite = false)
        private String password;
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface PrivateFieldBinder {
        @ReadFrom PrivateFieldUser read(String json) throws IOException;
        @WriteTo String write(PrivateFieldUser value) throws IOException;
    }

    @org.junit.jupiter.api.Test
    void privateFieldAnnotationControlsBindingWithoutHidingAccessor() throws Exception {
        PrivateFieldBinder binder = CompiledInstances.create(PrivateFieldBinder.class);
        PrivateFieldUser user = binder.read("{\"password\":\"secret\"}");
        assertEquals("secret", user.getPassword());
        assertFalse(binder.write(user).contains("password"));
    }

    public record Credential(@NodeProperty(bindingWrite = false) String token, String username) {}

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface CredentialBinder {
        @ReadFrom Credential read(String json) throws IOException;
        @WriteTo String write(Credential value) throws IOException;
    }

    @org.junit.jupiter.api.Test
    void recordComponentMayDisableOnlyBindingOutput() throws Exception {
        CredentialBinder binder = CompiledInstances.create(CredentialBinder.class);
        Credential value = binder.read("{\"token\":\"abc\",\"username\":\"Ada\"}");
        assertEquals("abc", value.token());
        String json = binder.write(value);
        assertFalse(json.contains("token"), json);
        assertTrue(json.contains("username"), json);
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
