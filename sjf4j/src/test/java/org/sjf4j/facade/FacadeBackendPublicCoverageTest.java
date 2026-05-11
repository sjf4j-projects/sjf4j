package org.sjf4j.facade;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.facade.snake.SnakeYamlFacade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacadeBackendPublicCoverageTest {

    @FunctionalInterface
    interface FacadeFactory {
        JsonFacade<?, ?> create();
    }

    private static void assertScalarReaderMethods(JsonFacade<?, ?> facade) throws Exception {
        try (StreamingReader reader = facade.createReader("1")) {
            reader.peekToken();
            assertEquals(1L, reader.nextLong().longValue());
        }
        try (StreamingReader reader = facade.createReader("2")) {
            reader.peekToken();
            assertEquals((short) 2, reader.nextShort().shortValue());
        }
        try (StreamingReader reader = facade.createReader("3")) {
            reader.peekToken();
            assertEquals((byte) 3, reader.nextByte().byteValue());
        }
        try (StreamingReader reader = facade.createReader("1.5")) {
            reader.peekToken();
            assertEquals(1.5d, reader.nextDouble());
        }
        try (StreamingReader reader = facade.createReader("2.5")) {
            reader.peekToken();
            assertEquals(2.5f, reader.nextFloat());
        }
        try (StreamingReader reader = facade.createReader("12345678901234567890")) {
            reader.peekToken();
            assertEquals("12345678901234567890", reader.nextBigInteger().toString());
        }
        try (StreamingReader reader = facade.createReader("3.14")) {
            reader.peekToken();
            assertEquals("3.14", reader.nextBigDecimal().toPlainString());
        }
        try (StreamingReader reader = facade.createReader("null")) {
            reader.peekToken();
            reader.nextNull();
        }
    }

    @Test
    void jsonBackendReaderScalarMethodsAndOverloads() throws Exception {
        FacadeFactory[] factories = new FacadeFactory[]{
                () -> new Jackson2JsonFacade(new ObjectMapper()),
                () -> new Fastjson2JsonFacade(new JSONReader.Feature[0], new JSONWriter.Feature[0]),
                () -> new GsonJsonFacade(new GsonBuilder()),
                JsonpJsonFacade::new
        };
        for (FacadeFactory factory : factories) {
            JsonFacade<?, ?> facade = factory.create();
            assertScalarReaderMethods(facade);

            byte[] bytes = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
            assertTrue(facade.readNode(new ByteArrayInputStream(bytes), Map.class) instanceof Map);
            assertTrue(facade.readNode(bytes, Map.class) instanceof Map);
        }
    }

    @Test
    void jsonBackendWriterBooleanNullAndCloseMethods() throws Exception {
        FacadeFactory[] factories = new FacadeFactory[]{
                () -> new Jackson2JsonFacade(new ObjectMapper()),
                () -> new Fastjson2JsonFacade(new JSONReader.Feature[0], new JSONWriter.Feature[0]),
                () -> new GsonJsonFacade(new GsonBuilder()),
                JsonpJsonFacade::new
        };

        for (FacadeFactory factory : factories) {
            JsonFacade<?, ?> facade = factory.create();
            StringWriter sw = new StringWriter();
            StreamingWriter writer = facade.createWriter(sw);
            writer.startObject();
            writer.writeName("b");
            writer.writeBoolean(true);
            writer.writeName("n");
            writer.writeNull();
            writer.endObject();
            writer.flush();
            writer.flushTo(sw);
            writer.close();
            String json = sw.toString();
            assertTrue(json.contains("\"b\":true") || json.contains("\"b\" : true"));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            facade.writeNode(out, Map.of("x", 1));
            assertTrue(new String(out.toByteArray(), StandardCharsets.UTF_8).contains("\"x\""));
            assertTrue(new String(facade.writeNodeAsBytes(Map.of("x", 1)), StandardCharsets.UTF_8).contains("\"x\""));
        }
    }

    @Test
    void snakeYamlReaderWriterMethods() throws Exception {
        SnakeYamlFacade facade = new SnakeYamlFacade();
        try (StreamingReader reader = facade.createReader(new java.io.StringReader("v: 3\n"))) {
            reader.startDocument();
            reader.startObject();
            assertEquals("v", reader.nextName());
            assertEquals(3L, reader.nextLong().longValue());
            reader.endObject();
            reader.endDocument();
        }

        StringWriter out = new StringWriter();
        StreamingWriter writer = facade.createWriter(out);
        writer.startDocument();
        writer.startObject();
        writer.writeName("ok");
        writer.writeBoolean(true);
        writer.writeName("none");
        writer.writeNull();
        writer.endObject();
        writer.endDocument();
        writer.flush();
        writer.close();
        assertTrue(out.toString().contains("ok"));
    }

    @Test
    void concreteBackendReaderWriterOverloadsAndExclusivePaths() throws Exception {
        byte[] one = "1".getBytes(StandardCharsets.UTF_8);
        byte[] object = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        Jackson2JsonFacade jackson2 = new Jackson2JsonFacade(new ObjectMapper());
        try (StreamingReader reader = jackson2.createReader(new ByteArrayInputStream(one))) {
            reader.peekToken();
            assertEquals(1L, reader.nextLong().longValue());
        }
        try (StreamingReader reader = jackson2.createReader(one)) {
            reader.peekToken();
            assertEquals(1L, reader.nextLong().longValue());
        }
        ByteArrayOutputStream jackson2Out = new ByteArrayOutputStream();
        StreamingWriter jackson2Writer = jackson2.createWriter(jackson2Out);
        jackson2Writer.startObject();
        jackson2Writer.writeName("ok");
        jackson2Writer.writeBoolean(true);
        jackson2Writer.endObject();
        jackson2Writer.flush();
        jackson2Writer.close();
        assertTrue(new String(jackson2Out.toByteArray(), StandardCharsets.UTF_8).contains("\"ok\":true"));

        Fastjson2JsonFacade fastjson2 = new Fastjson2JsonFacade(
                new JSONReader.Feature[0],
                new JSONWriter.Feature[0],
                new StreamingContext(Map.of(), StreamingContext.StreamingMode.EXCLUSIVE_IO, true)
        );
        assertInstanceOf(Fastjson2JsonFacade.class,
                Fastjson2JsonFacade.provider(new JSONReader.Feature[0], new JSONWriter.Feature[0])
                        .create(StreamingContext.EMPTY));
        try (StreamingReader reader = fastjson2.createReader(new ByteArrayInputStream(one))) {
            reader.peekToken();
            assertEquals(1L, reader.nextLong().longValue());
        }
        try (StreamingReader reader = fastjson2.createReader(one)) {
            reader.peekToken();
            assertEquals(1L, reader.nextLong().longValue());
        }
        assertInstanceOf(JsonObject.class, fastjson2.readNodeExclusive(new ByteArrayInputStream(object), JsonObject.class));
        assertInstanceOf(JsonObject.class, fastjson2.readNodeExclusive(object, JsonObject.class));
        ByteArrayOutputStream fastjson2Out = new ByteArrayOutputStream();
        StreamingWriter fastjson2Writer = fastjson2.createWriter(fastjson2Out);
        fastjson2Writer.startObject();
        fastjson2Writer.writeName("ok");
        fastjson2Writer.writeBoolean(true);
        fastjson2Writer.endObject();
        fastjson2Writer.flush();
        fastjson2Writer.flushTo(fastjson2Out);
        fastjson2Writer.close();
        assertTrue(new String(fastjson2Out.toByteArray(), StandardCharsets.UTF_8).contains("\"ok\":true"));

        ByteArrayOutputStream fastjson2ExclusiveOut = new ByteArrayOutputStream();
        fastjson2.writeNodeExclusive(fastjson2ExclusiveOut, Map.of("a", 1));
        assertTrue(new String(fastjson2ExclusiveOut.toByteArray(), StandardCharsets.UTF_8).contains("\"a\":1"));
        assertTrue(new String(fastjson2.writeNodeAsBytesExclusive(Map.of("a", 1)), StandardCharsets.UTF_8)
                .contains("\"a\":1"));
    }
}
