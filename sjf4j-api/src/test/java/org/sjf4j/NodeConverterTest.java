package org.sjf4j;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.converters.OffsetDateTimeConverter;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class NodeConverterTest {

    @Test
    public void testOne1() {
        ConverterRegistry.putConverter(new OffsetDateTimeConverter());
        JsonObject jo1 = new JsonObject("aa", "bb");
        jo1.put("t1", OffsetDateTime.now());
        log.info("jo1: {}", jo1);

        assertThrows(JsonException.class, () -> jo1.put("t2", ZonedDateTime.now()));
        log.info("jo1: {}", jo1);
    }

}
