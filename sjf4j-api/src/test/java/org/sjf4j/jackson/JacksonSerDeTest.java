package org.sjf4j.jackson;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class JacksonSerDeTest {

    @Test
    public void testSerDe1() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Object.class, new SimpleJsonDeserializer());
        module.addSerializer(Object.class, new SimpleJsonSerializer());
        objectMapper.registerModule(module);

        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        Object obj1 = objectMapper.readValue(new StringReader(json1), Object.class);
        String res1 = objectMapper.writeValueAsString(obj1);
        log.info("obj1 {}: {}", obj1.getClass().getName(), res1);
        assertEquals(json1, res1);
    }

}
