package org.sjf4j.facade;

import com.alibaba.fastjson2.JSONReader;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.facade.fastjson2.Fastjson2StreamingIO;
import org.sjf4j.facade.jackson2.Jackson2StreamingIO;

import java.io.IOException;
import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class NativeStreamingIOTypeValidationTest {

    @Test
    void fastjson2ReadNodeRejectsNullType() {
        Type type = null;
        try (JSONReader reader = JSONReader.of("null")) {
            NullPointerException error = assertThrowsExactly(NullPointerException.class,
                    () -> Fastjson2StreamingIO.readNode(reader, type, StreamingContext.EMPTY));
            assertEquals("'type' must not be null", error.getMessage());
        }
    }

    @Test
    void jackson2ReadNodeRejectsNullType() throws IOException {
        Type type = null;
        try (JsonParser parser = new ObjectMapper().getFactory().createParser("null")) {
            NullPointerException error = assertThrowsExactly(NullPointerException.class,
                    () -> Jackson2StreamingIO.readNode(parser, type, StreamingContext.EMPTY));
            assertEquals("'type' must not be null", error.getMessage());
        }
    }
}
