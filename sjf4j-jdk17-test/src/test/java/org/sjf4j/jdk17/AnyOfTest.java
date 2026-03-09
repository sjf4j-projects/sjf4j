package org.sjf4j.jdk17;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeProperty;

import java.util.List;
import org.junit.jupiter.api.Test;

@Slf4j
public class AnyOfTest {

    @AnyOf(key = "cmd", value = {
            @AnyOf.Mapping(value = StartRequest.class, when = "start"),
            @AnyOf.Mapping(value = DialectRequest.class, when = "dialect"),
            @AnyOf.Mapping(value = RunRequest.class, when = "run"),
    })
    @Data
    public static class BaseRequest {
        private String cmd;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class StartRequest extends BaseRequest {
        private int version;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class DialectRequest extends BaseRequest {
        private String dialect;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class RunRequest extends BaseRequest {
        private Object seq;
        @NodeProperty("case")
        private TestCase testCase;
    }

    @Test
    public void testOne() {
        String json1 = "{\"cmd\": \"start\", \"version\": 1}";
        BaseRequest req1 = Sjf4j.fromJson(json1, BaseRequest.class);
        log.info("class={}, req1={}", req1.getClass().getName(), req1);

        String json2 = "{\"cmd\": \"dialect\", \"dialect\": \"2020-12\"}";
        BaseRequest req2 = Sjf4j.fromJson(json2, BaseRequest.class);
        log.info("class={}, req2={}", req2.getClass().getName(), req2);

        String json3 = "{\"cmd\": \"run\", \"seq\": 12345, \"case\": {\"description\": \"none\"}}";
        BaseRequest req3 = Sjf4j.fromJson(json3, BaseRequest.class);
        log.info("class={}, req3={}", req3.getClass().getName(), req3);
    }

}


record TestCase(String description, String comment, Object schema, JsonObject registry, List<TestOne> tests) {}

record TestOne(String description, String comment, Object instance, boolean valid) {}
