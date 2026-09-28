package org.sjf4j.facade;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.TypeReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
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
        assertAcrossBackends(StreamingContext.StreamingMode.SHARED_IO,
                CodecFacadeRegressionTest::assertInheritedNodeValueRoundTrip);
        assertAcrossBackends(StreamingContext.StreamingMode.PLUGIN_MODULE,
                CodecFacadeRegressionTest::assertInheritedNodeValueRoundTrip);
    }

    @SuppressWarnings("unchecked")
    private static void assertInheritedNodeValueRoundTrip(JsonFacade<?, ?> facade) {
        BigDay first = new BigDay(LocalDate.parse("2024-10-01"));
        BigDay second = new BigDay(LocalDate.parse("2025-12-18"));

        assertEquals("\"2024-10-01\"", facade.writeNodeAsString(first));
        BigDay decoded = (BigDay) facade.readNode("\"2024-10-01\"", BigDay.class);
        assertInstanceOf(BigDay.class, decoded);
        assertEquals(first.value, decoded.value);

        String listJson = "[\"2024-10-01\",\"2025-12-18\"]";
        List<BigDay> days = (List<BigDay>) facade.readNode(listJson,
                new TypeReference<List<BigDay>>() {}.getType());
        assertEquals(Arrays.asList(first.value, second.value), Arrays.asList(days.get(0).value, days.get(1).value));
        assertEquals(listJson, facade.writeNodeAsString(days));

        NodeValueHolder holder = new NodeValueHolder();
        holder.day = first;
        holder.days = Arrays.asList(first, second);
        String holderJson = "{\"day\":\"2024-10-01\",\"days\":[\"2024-10-01\",\"2025-12-18\"]}";
        assertEquals(holderJson, facade.writeNodeAsString(holder));

        NodeValueHolder decodedHolder = (NodeValueHolder) facade.readNode(holderJson, NodeValueHolder.class);
        assertInstanceOf(BigDay.class, decodedHolder.day);
        assertEquals(first.value, decodedHolder.day.value);
        assertEquals(Arrays.asList(first.value, second.value),
                Arrays.asList(decodedHolder.days.get(0).value, decodedHolder.days.get(1).value));
    }

    private static void assertAcrossBackends(StreamingContext.StreamingMode mode,
                                             Consumer<JsonFacade<?, ?>> assertion) {
        assertion.accept(new Jackson2JsonFacade(new ObjectMapper(), new StreamingContext(mode)));
        assertion.accept(new GsonJsonFacade(new GsonBuilder(), new StreamingContext(mode)));
        assertion.accept(new Fastjson2JsonFacade(null, null, new StreamingContext(mode)));
    }

}
