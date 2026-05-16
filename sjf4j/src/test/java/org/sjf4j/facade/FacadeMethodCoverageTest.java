package org.sjf4j.facade;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.simple.SimpleYamlFacade;
import org.sjf4j.facade.snake.SnakeYamlFacade;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacadeMethodCoverageTest {

    @Test
    void jsonFacadeDefaultUnsupportedHelpersAreCovered() {
        JsonFacade<StreamingReader, StreamingWriter> facade = new JsonFacade<StreamingReader, StreamingWriter>() {
            @Override
            public StreamingReader createReader(Reader input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamingWriter createWriter(Writer output) {
                throw new UnsupportedOperationException();
            }
        };

        assertThrows(BindingException.class, () -> facade.readNodeExclusive(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), Object.class));
        assertThrows(BindingException.class, () -> facade.readNodeExclusive("{}", Object.class));
        assertThrows(BindingException.class, () -> facade.readNodeExclusive("{}".getBytes(StandardCharsets.UTF_8), Object.class));
        assertThrows(BindingException.class, () -> facade.readNodePlugin(new StringReader("{}"), Object.class));
        assertThrows(BindingException.class, () -> facade.readNodePlugin(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), Object.class));
        assertThrows(BindingException.class, () -> facade.readNodePlugin("{}", Object.class));
        assertThrows(BindingException.class, () -> facade.readNodePlugin("{}".getBytes(StandardCharsets.UTF_8), Object.class));
        assertThrows(BindingException.class, () -> facade.writeNodeExclusive(new StringWriter(), Map.of("a", 1)));
        assertThrows(BindingException.class, () -> facade.writeNodePlugin(new StringWriter(), Map.of("a", 1)));
        assertThrows(BindingException.class, () -> facade.writeNodeAsStringExclusive(Map.of("a", 1)));
        assertThrows(BindingException.class, () -> facade.writeNodeAsBytesExclusive(Map.of("a", 1)));
        assertThrows(BindingException.class, () -> facade.writeNodeAsBytesPlugin(Map.of("a", 1)));
        BindingException failed = assertThrows(BindingException.class,
                () -> facade.failedToWrite(Map.of("a", 1), new RuntimeException("x")));
        assertTrue(failed.getMessage().contains("failed to write node type"));
    }

    @Test
    void jackson2AndFastjson2ByteAndStreamPathsAreCovered() {
        String json = "{\"a\":1}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        Jackson2JsonFacade jackson2Exclusive = new Jackson2JsonFacade(new ObjectMapper(),
                new StreamingContext(Map.of(), StreamingContext.StreamingMode.EXCLUSIVE_IO, true));
        assertEquals(1, ((JsonObject) jackson2Exclusive.readNode(new ByteArrayInputStream(bytes), JsonObject.class)).getInt("a"));
        assertEquals(1, ((JsonObject) jackson2Exclusive.readNode(bytes, JsonObject.class)).getInt("a"));
        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        jackson2Exclusive.writeNode(out1, JsonObject.of("a", 1));
        assertTrue(new String(out1.toByteArray(), StandardCharsets.UTF_8).contains("\"a\":1"));
        assertTrue(new String(jackson2Exclusive.writeNodeAsBytes(JsonObject.of("a", 1)), StandardCharsets.UTF_8).contains("\"a\":1"));

        Fastjson2JsonFacade fastjson2Plugin = new Fastjson2JsonFacade(new JSONReader.Feature[0], new JSONWriter.Feature[0],
                new StreamingContext(Map.of(), StreamingContext.StreamingMode.PLUGIN_MODULE, true));
        assertEquals(1, ((JsonObject) fastjson2Plugin.readNode(new ByteArrayInputStream(bytes), JsonObject.class)).getInt("a"));
        assertEquals(1, ((JsonObject) fastjson2Plugin.readNode(bytes, JsonObject.class)).getInt("a"));
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        fastjson2Plugin.writeNode(out2, JsonObject.of("a", 1));
        assertTrue(new String(out2.toByteArray(), StandardCharsets.UTF_8).contains("\"a\":1"));
        assertTrue(new String(fastjson2Plugin.writeNodeAsBytes(JsonObject.of("a", 1)), StandardCharsets.UTF_8).contains("\"a\":1"));
    }

    @Test
    void yamlProviderPathsAreCovered() {
        SimpleYamlFacade simpleYaml = new SimpleYamlFacade();
        assertEquals(StreamingContext.EMPTY, simpleYaml.streamingContext());
        assertThrows(BindingException.class, () -> simpleYaml.createReader(new StringReader("a: 1")));
        assertThrows(BindingException.class, () -> simpleYaml.createWriter(new StringWriter()));
        assertInstanceOf(SimpleYamlFacade.class, SimpleYamlFacade.provider().create(StreamingContext.EMPTY));

        assertInstanceOf(SnakeYamlFacade.class,
                SnakeYamlFacade.provider(new LoaderOptions(), new DumperOptions()).create(StreamingContext.EMPTY));
    }
}
