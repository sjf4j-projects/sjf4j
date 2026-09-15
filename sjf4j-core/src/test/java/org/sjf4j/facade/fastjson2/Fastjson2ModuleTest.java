package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.util.List;

@Slf4j
public class Fastjson2ModuleTest {

//    @BeforeAll
//    public static void init() {
//        JSONFactory.getDefaultObjectReaderProvider().register(new Fastjson2Module.MyObjectReaderModule());
//    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Baby extends JsonObject {
        private String name;
        private int month;
    }

    @Getter @Setter
    @NoArgsConstructor
    public static class Person extends JsonObject {
        private String name;
        private int age;
        private JsonObject info;
        private List<Baby> babies;
    }

    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":55,\"kk\":{\"jj\":11}},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";


    @Test
    public void testOne() {
        Person p1 = JSON.parseObject(JSON_DATA, Person.class);
        log.info("p1={}", p1);

        ObjectReaderProvider provider = JSONFactory.getDefaultObjectReaderProvider();
        JSONReader.Context context = new JSONReader.Context(provider);
        context.setExtraProcessor((object, key, value) -> {
            log.info("acceptExtra name={}, value.type={}", key, value.getClass());
            if (object instanceof JsonObject) {
                ((JsonObject) object).put(key, value);
            }
        });
        JSONReader reader = JSONReader.of(JSON_DATA, context);
        Person p2 = reader.read(Person.class);
        log.info("p2={}", p2);
        log.info("type={}", p2.getInfo().getNode("kk").getClass());
    }


}
