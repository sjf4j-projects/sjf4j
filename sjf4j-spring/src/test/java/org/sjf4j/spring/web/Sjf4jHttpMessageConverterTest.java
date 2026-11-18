package org.sjf4j.spring.web;

import org.junit.jupiter.api.Test;
import org.sjf4j.TypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sjf4jHttpMessageConverterTest {

    private final Sjf4jHttpMessageConverter converter = new Sjf4jHttpMessageConverter();

    @Test
    void readsAndWritesJson() throws Exception {
        MockHttpInputMessage input = input("{\"name\":\"Ada\"}", MediaType.APPLICATION_JSON);
        User user = (User) converter.read(User.class, null, input);
        assertEquals("Ada", user.name);

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(user, User.class, MediaType.APPLICATION_JSON, output);
        assertTrue(output.getBodyAsString(StandardCharsets.UTF_8).contains("\"name\":\"Ada\""));
    }

    @Test
    void readsGenericJsonAndStructuredSyntaxSuffix() throws Exception {
        Type usersType = new TypeReference<List<User>>() {}.getType();
        MediaType vendorJson = MediaType.parseMediaType("application/vnd.example+json");
        assertTrue(converter.canRead(usersType, null, vendorJson));
        assertTrue(converter.canRead(User.class, null, MediaType.APPLICATION_JSON));

        MockHttpInputMessage input = input("[{\"name\":\"Ada\"}]", vendorJson);
        @SuppressWarnings("unchecked")
        List<User> users = (List<User>) converter.read(usersType, null, input);
        assertEquals("Ada", users.get(0).name);
    }

    @Test
    void readsAndWritesYaml() throws Exception {
        MediaType yaml = MediaType.parseMediaType("application/yaml");
        MockHttpInputMessage input = input("name: Ada\n", yaml);
        User user = (User) converter.read(User.class, null, input);
        assertEquals("Ada", user.name);

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(user, User.class, yaml, output);
        assertTrue(output.getBodyAsString(StandardCharsets.UTF_8).contains("Ada"));
    }

    @Test
    void defersSpecialBodyTypesToSpring() {
        assertFalse(converter.canWrite(String.class, MediaType.APPLICATION_JSON));
        assertFalse(converter.canRead(byte[].class, MediaType.APPLICATION_JSON));
        assertFalse(converter.canWrite(ProblemDetail.class, MediaType.APPLICATION_JSON));
    }

    private static MockHttpInputMessage input(String content, MediaType mediaType) {
        MockHttpInputMessage message = new MockHttpInputMessage(content.getBytes(StandardCharsets.UTF_8));
        message.getHeaders().setContentType(mediaType);
        return message;
    }

    public static class User {
        public String name;
    }
}
