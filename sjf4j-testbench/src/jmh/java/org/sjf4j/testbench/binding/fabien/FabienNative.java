package org.sjf4j.testbench.binding.fabien;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.testbench.model.fabien.Users;
import org.sjf4j.testbench.model.fabien.Clients;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Direct library binding baselines, with no SJF4J code on the measured path.
 * Native and Blackbird are deliberately different implementations.
 *
 * JSON-P's native JsonValue is not Java Map/POJO binding, and Simple has no
 * external native implementation. SnakeYAML native is Map-only: its bean
 * construction does not have matching Java-time/UUID semantics for Clients.
 */
final class FabienNative {
    private FabienNative() {}

    private static final ObjectMapper JACKSON2 = jackson2(false);
    private static final ObjectMapper JACKSON2_BLACKBIRD = jackson2(true);
    private static final JsonMapper JACKSON3 = new JsonMapper();
    private static final JsonMapper JACKSON3_BLACKBIRD = JsonMapper.builder()
            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build();
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(LocalDate.class, new TypeAdapter<LocalDate>() {
                @Override public void write(JsonWriter out, LocalDate value) throws IOException {
                    if (value == null) out.nullValue(); else out.value(value.toString());
                }

                @Override public LocalDate read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    return LocalDate.parse(in.nextString());
                }
            })
            .registerTypeAdapter(OffsetDateTime.class, new TypeAdapter<OffsetDateTime>() {
                @Override public void write(JsonWriter out, OffsetDateTime value) throws IOException {
                    if (value == null) out.nullValue(); else out.value(value.toString());
                }

                @Override public OffsetDateTime read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    return OffsetDateTime.parse(in.nextString());
                }
            })
            .create();
    private static final Yaml SNAKE = new Yaml();

    private static ObjectMapper jackson2(boolean blackbird) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return blackbird ? mapper.registerModule(new BlackbirdModule()) : mapper;
    }

    static FabienBindings.Reader reader(String backend, String implementation,
                                        String model, String shape) {
        Class<?> type = "map".equals(shape) ? Map.class
                : "users".equals(model) ? Users.class : Clients.class;
        if ("blackbird".equals(implementation)) {
            switch (backend) {
                case "jackson2": return input -> JACKSON2_BLACKBIRD.readValue(input, type);
                case "jackson3": return input -> JACKSON3_BLACKBIRD.readValue(input, type);
                default: throw unsupported(backend, implementation, shape);
            }
        }
        if (!"native".equals(implementation)) {
            throw unsupported(backend, implementation, shape);
        }
        switch (backend) {
            case "jackson2": return input -> JACKSON2.readValue(input, type);
            case "jackson3": return input -> JACKSON3.readValue(input, type);
            case "fastjson2": return input -> JSON.parseObject(input, type);
            case "gson": return input -> GSON.fromJson(
                    new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8),
                    type);
            case "snake":
                if (!"map".equals(shape)) throw unsupported(backend, implementation, shape);
                return input -> SNAKE.load(new String(input, StandardCharsets.UTF_8));
            default: throw unsupported(backend, implementation, shape);
        }
    }

    static FabienBindings.Writer writer(String backend, String implementation,
                                        String model, String shape) {
        if ("blackbird".equals(implementation)) {
            switch (backend) {
                case "jackson2": return JACKSON2_BLACKBIRD::writeValueAsBytes;
                case "jackson3": return JACKSON3_BLACKBIRD::writeValueAsBytes;
                default: throw unsupported(backend, implementation, shape);
            }
        }
        if (!"native".equals(implementation)) {
            throw unsupported(backend, implementation, shape);
        }
        switch (backend) {
            case "jackson2": return JACKSON2::writeValueAsBytes;
            case "jackson3": return JACKSON3::writeValueAsBytes;
            case "fastjson2": return node -> JSON.toJSONBytes(node, JSONWriter.Feature.WriteNulls);
            case "gson": return node -> GSON.toJson(node).getBytes(StandardCharsets.UTF_8);
            case "snake":
                if (!"map".equals(shape)) throw unsupported(backend, implementation, shape);
                return node -> SNAKE.dump(node).getBytes(StandardCharsets.UTF_8);
            default: throw unsupported(backend, implementation, shape);
        }
    }

    private static IllegalArgumentException unsupported(String backend, String implementation,
                                                        String shape) {
        return new IllegalArgumentException("Unsupported Fabien binding combination: backend="
                + backend + ", implementation=" + implementation + ", shape=" + shape
                + ". Blackbird requires Jackson2/3; native is unavailable for JSON-P/Simple"
                + " and for SnakeYAML POJO. Select a supported combination in main or the"
                + " Quick/Full benchmark script.");
    }
}
