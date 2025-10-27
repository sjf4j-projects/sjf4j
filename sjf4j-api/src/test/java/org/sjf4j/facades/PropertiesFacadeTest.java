package org.sjf4j.facades;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class PropertiesFacadeTest {

    @Test
    public void testToFrom1() throws IOException {
        String json1 = "{\"id\":123,\"height\":175.3,\"name\":\"han\",\"friends\":{\"jack\":\"good\",\"rose\":{\"age\":[18,20]}},\"sex\":true}";
        JsonObject jo1 = JsonObject.fromJson(json1);
        Properties props = new Properties();
        PropertiesFacade.toProps(props, jo1);
        StringWriter sw = new StringWriter();
        props.store(sw, "");
        log.info("props:\n{}", sw.toString());

        JsonObject jo2 = PropertiesFacade.fromProps(props);
//        assertEquals(jo1, jo2);
        assertEquals("18", jo2.getObjectByPath("$.friends.rose.age[0]"));
    }


}
