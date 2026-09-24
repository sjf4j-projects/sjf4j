package org.sjf4j.facade;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jackson3.Jackson3JsonFacade;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class NativeFacadeNullTargetTest {

    private static final byte[] JSON_BYTES = "null".getBytes(StandardCharsets.UTF_8);

    @Test
    void gsonPluginHooksRejectNullTypeBeforeReading() {
        GsonJsonFacade facade = new GsonJsonFacade(new GsonBuilder());
        Type type = null;

        assertNullType(() -> facade.readNodePlugin(new StringReader("null"), type));
        assertNullType(() -> facade.readNodePlugin("null", type));
    }

    @Test
    void jackson2PluginAndExclusiveHooksRejectNullTypeBeforeReading() {
        Jackson2JsonFacade facade = new Jackson2JsonFacade(new ObjectMapper());
        Type type = null;

        assertNullType(() -> facade.readNodePlugin(new StringReader("null"), type));
        assertNullType(() -> facade.readNodePlugin(new ByteArrayInputStream(JSON_BYTES), type));
        assertNullType(() -> facade.readNodePlugin("null", type));
        assertNullType(() -> facade.readNodePlugin(JSON_BYTES, type));
        assertNullType(() -> facade.readNodeExclusive(new StringReader("null"), type));
        assertNullType(() -> facade.readNodeExclusive(new ByteArrayInputStream(JSON_BYTES), type));
        assertNullType(() -> facade.readNodeExclusive("null", type));
        assertNullType(() -> facade.readNodeExclusive(JSON_BYTES, type));
    }

    @Test
    void fastjson2PluginAndExclusiveHooksRejectNullTypeBeforeReading() {
        Fastjson2JsonFacade facade = new Fastjson2JsonFacade(new JSONReader.Feature[0], new JSONWriter.Feature[0]);
        Type type = null;

        assertNullType(() -> facade.readNodePlugin(new StringReader("null"), type));
        assertNullType(() -> facade.readNodePlugin(new ByteArrayInputStream(JSON_BYTES), type));
        assertNullType(() -> facade.readNodePlugin("null", type));
        assertNullType(() -> facade.readNodePlugin(JSON_BYTES, type));
        assertNullType(() -> facade.readNodeExclusive(new StringReader("null"), type));
        assertNullType(() -> facade.readNodeExclusive(new ByteArrayInputStream(JSON_BYTES), type));
        assertNullType(() -> facade.readNodeExclusive("null", type));
        assertNullType(() -> facade.readNodeExclusive(JSON_BYTES, type));
    }

    @Test
    void jackson3PluginHooksRejectNullTypeBeforeReadingWhenAvailable() {
        Assumptions.assumeTrue(isJackson3Available());
        Jackson3JsonFacade facade = new Jackson3JsonFacade();
        Type type = null;

        assertNullType(() -> facade.readNodePlugin(new StringReader("null"), type));
        assertNullType(() -> facade.readNodePlugin(new ByteArrayInputStream(JSON_BYTES), type));
        assertNullType(() -> facade.readNodePlugin("null", type));
        assertNullType(() -> facade.readNodePlugin(JSON_BYTES, type));
    }

    private static boolean isJackson3Available() {
        try {
            Class.forName("tools.jackson.databind.json.JsonMapper");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void assertNullType(ThrowingOperation operation) {
        assertThrowsExactly(NullPointerException.class, operation::run);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
