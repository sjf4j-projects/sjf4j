package org.sjf4j.facade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.node.TypeReference;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CodecFacadeRegressionTest {

    @NodeValue
    static class Day {
        final LocalDate value;

        Day(LocalDate value) {
            this.value = value;
        }

        @ValueToRaw
        String encode() {
            return value.toString();
        }

        @RawToValue
        static Day decode(String raw) {
            return new Day(LocalDate.parse(raw));
        }

        @ValueCopy
        Day copy() {
            return new Day(value);
        }
    }

    static class BigDay extends Day {
        BigDay(LocalDate value) {
            super(value);
        }

        static BigDay decode(String raw) {
            return new BigDay(LocalDate.parse(raw));
        }

        BigDay copy() {
            return new BigDay(value);
        }
    }

    static class NodeValueHolder {
        public BigDay day;
        public List<BigDay> days;
    }

    @Test
    void testInheritedNodeValueRoundTripsAcrossBackends() {
        assertAcrossBackends(StreamingContext.StreamingMode.SHARED_IO, CodecFacadeRegressionTest::assertInheritedNodeValueRoundTrip);
        assertAcrossBackends(StreamingContext.StreamingMode.PLUGIN_MODULE, CodecFacadeRegressionTest::assertInheritedNodeValueRoundTrip);
    }

    private static void assertInheritedNodeValueRoundTrip(Sjf4j runtime) {
        BigDay first = new BigDay(LocalDate.parse("2024-10-01"));
        BigDay second = new BigDay(LocalDate.parse("2025-12-18"));

        assertEquals("\"2024-10-01\"", runtime.toJsonString(first));
        BigDay decoded = runtime.fromJson("\"2024-10-01\"", BigDay.class);
        assertInstanceOf(BigDay.class, decoded);
        assertEquals(first.value, decoded.value);

        String listJson = "[\"2024-10-01\",\"2025-12-18\"]";
        List<BigDay> days = runtime.fromJson(listJson, new TypeReference<List<BigDay>>() {});
        assertEquals(Arrays.asList(first.value, second.value), Arrays.asList(days.get(0).value, days.get(1).value));
        assertEquals(listJson, runtime.toJsonString(days));

        NodeValueHolder holder = new NodeValueHolder();
        holder.day = first;
        holder.days = Arrays.asList(first, second);
        String holderJson = "{\"day\":\"2024-10-01\",\"days\":[\"2024-10-01\",\"2025-12-18\"]}";
        assertEquals(holderJson, runtime.toJsonString(holder));

        NodeValueHolder decodedHolder = runtime.fromJson(holderJson, NodeValueHolder.class);
        assertInstanceOf(BigDay.class, decodedHolder.day);
        assertEquals(first.value, decodedHolder.day.value);
        assertEquals(Arrays.asList(first.value, second.value),
                Arrays.asList(decodedHolder.days.get(0).value, decodedHolder.days.get(1).value));
    }

    private static void assertAcrossBackends(StreamingContext.StreamingMode mode, Consumer<Sjf4j> assertion) {
        assertion.accept(Sjf4j.builder(Sjf4j.global())
                .streamingMode(mode)
                .jsonFacadeProvider(Jackson2JsonFacade.provider(new ObjectMapper()))
                .build());

        assertion.accept(Sjf4j.builder(Sjf4j.global())
                .streamingMode(mode)
                .jsonFacadeProvider(GsonJsonFacade.provider(new GsonBuilder()))
                .build());

        assertion.accept(Sjf4j.builder(Sjf4j.global())
                .streamingMode(mode)
                .jsonFacadeProvider(Fastjson2JsonFacade.provider())
                .build());
    }
}
