package org.sjf4j.spring.example;

import org.junit.jupiter.api.Test;
import org.sjf4j.NodeConfig;
import org.sjf4j.TypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MyHttpMessageConverterTest {

    @RestController
    static class UserController {

        @PostMapping(path = "/users",
                consumes = {"application/json", "application/yaml"},
                produces = {"application/json", "application/yaml"})
        public User echo(@RequestBody User user) {
            return user;
        }
    }

    public static class User {
        public String name;
    }


    private final MyHttpMessageConverter converter = new MyHttpMessageConverter(NodeConfig.DEFAULT);

    @Test
    void handlesJsonRequestAndResponseInSpringMvc() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserController())
                .setMessageConverters(converter).build();

        String body = mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"name\":\"Ada\""), body);
    }

    @Test
    void handlesYamlRequestAndResponseInSpringMvc() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserController())
                .setMessageConverters(converter).build();
        MediaType yaml = MediaType.parseMediaType("application/yaml");

        String body = mvc.perform(post("/users")
                        .contentType(yaml)
                        .accept(yaml)
                        .content("name: Ada\n"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("name: Ada"), body);
    }

    @Test
    void handlesGenericJsonAndSuffixMediaTypes() throws Exception {
        Type target = new TypeReference<List<User>>() {}.getType();
        MediaType vendorJson = MediaType.parseMediaType("application/vnd.example+json");
        assertTrue(converter.canRead(target, null, vendorJson));
        assertTrue(converter.canRead(User.class, null, MediaType.APPLICATION_JSON));

        MockHttpInputMessage input = new MockHttpInputMessage(
                "[{\"name\":\"Ada\"}]".getBytes(StandardCharsets.UTF_8));
        input.getHeaders().setContentType(vendorJson);

        @SuppressWarnings("unchecked")
        List<User> users = (List<User>) converter.read(target, null, input);
        assertEquals(1, users.size());
        assertEquals("Ada", users.get(0).name);

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(users, target, vendorJson, output);
        assertTrue(output.getBodyAsString(StandardCharsets.UTF_8).contains("Ada"));
    }

    @Test
    void leavesSpecialBodyTypesToSpring() {
        assertFalse(converter.canRead(String.class, MediaType.APPLICATION_JSON));
        assertFalse(converter.canRead(byte[].class, MediaType.APPLICATION_JSON));
        assertFalse(converter.canWrite(ProblemDetail.class, MediaType.APPLICATION_JSON));
    }

}
