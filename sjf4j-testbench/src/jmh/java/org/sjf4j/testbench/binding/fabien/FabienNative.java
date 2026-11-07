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
import com.google.gson.ToNumberPolicy;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.testbench.model.fabien.Users;
import org.sjf4j.testbench.model.fabien.Clients;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Direct library binding baselines, with no SJF4J code on the measured path.
 * Native and Blackbird are deliberately different implementations.
 *
 * JSON-P's native JsonValue is not Java Map/POJO binding, and Simple has no
 * external native implementation. SnakeYAML POJO binding uses a purpose-built
 * native SnakeYAML Constructor/Representer for java.time and UUID fields.
 */
final class FabienNative {
    private FabienNative() {}

    private static final ObjectMapper JACKSON2 = jackson2(false);
    private static final ObjectMapper JACKSON2_BLACKBIRD = jackson2(true);
    private static final JsonMapper JACKSON3 = new JsonMapper();
    private static final JsonMapper JACKSON3_BLACKBIRD = JsonMapper.builder()
            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build();
    private static final Gson GSON = new GsonBuilder()
            // Gson defaults Object/Map numbers to Double, losing 64-bit ID
            // precision. Preserve integral literals as Long in the native Map path.
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
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
    private static final Yaml SNAKE_POJO = snakePojo();

    private static Yaml snakePojo() {
        DumperOptions options = new DumperOptions();
        Representer representer = new Representer(options) {
            {
                addClassTag(Users.class, Tag.MAP);
                addClassTag(Users.User.class, Tag.MAP);
                addClassTag(Users.Friend.class, Tag.MAP);
                addClassTag(Clients.class, Tag.MAP);
                addClassTag(Clients.Client.class, Tag.MAP);
                addClassTag(Clients.Partner.class, Tag.MAP);
                representers.put(UUID.class, node -> representScalar(Tag.STR, node.toString()));
                representers.put(LocalDate.class, node -> representScalar(Tag.STR, node.toString()));
                representers.put(OffsetDateTime.class, node -> representScalar(Tag.STR, node.toString()));
            }
        };
        return new Yaml(new FabienYamlConstructor(), representer, options);
    }

    private static final class FabienYamlConstructor extends Constructor {
        private final Construct uuid = new AbstractConstruct() {
            @Override public Object construct(Node node) {
                return UUID.fromString(((ScalarNode) node).getValue());
            }
        };
        private final Construct date = new AbstractConstruct() {
            @Override public Object construct(Node node) {
                return LocalDate.parse(((ScalarNode) node).getValue());
            }
        };
        private final Construct dateTime = new AbstractConstruct() {
            @Override public Object construct(Node node) {
                return OffsetDateTime.parse(((ScalarNode) node).getValue());
            }
        };

        FabienYamlConstructor() {
            super(new LoaderOptions());
        }

        @Override protected Construct getConstructor(Node node) {
            if (node.getType() == UUID.class) return uuid;
            if (node.getType() == LocalDate.class) return date;
            if (node.getType() == OffsetDateTime.class) return dateTime;
            return super.getConstructor(node);
        }
    }

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
                if ("map".equals(shape)) {
                    return input -> SNAKE.load(new String(input, StandardCharsets.UTF_8));
                }
                return input -> SNAKE_POJO.loadAs(new String(input, StandardCharsets.UTF_8), type);
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
                return node -> ("map".equals(shape) ? SNAKE : SNAKE_POJO)
                        .dump(node).getBytes(StandardCharsets.UTF_8);
            default: throw unsupported(backend, implementation, shape);
        }
    }

    private static IllegalArgumentException unsupported(String backend, String implementation,
                                                        String shape) {
        return new IllegalArgumentException("Unsupported Fabien binding combination: backend="
                + backend + ", implementation=" + implementation + ", shape=" + shape
                + ". Blackbird requires Jackson2/3; native is unavailable for JSON-P/Simple"
                + ". Select a supported combination in main or the"
                + " Quick/Full benchmark script.");
    }
}
