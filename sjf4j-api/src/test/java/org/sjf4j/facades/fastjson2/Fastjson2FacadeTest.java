package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class Fastjson2FacadeTest {

    @Test
    public void testSerDe1() {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = SimpleJsonReader.readObject(new StringReader(json1), new JSONReader.Feature[0]);
        String res1 = SimpleJsonWriter.toJson(jo1, new JSONWriter.Feature[0]);
        log.info("res1: {}", res1);
        assertEquals(json1, res1);
    }

}
