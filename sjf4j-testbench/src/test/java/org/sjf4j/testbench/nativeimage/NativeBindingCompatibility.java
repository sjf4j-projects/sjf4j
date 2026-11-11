package org.sjf4j.testbench.nativeimage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Standalone Native Image compatibility probes, NOT JMH benchmarks or JUnit.
 *
 * <p>Each nested entry point references exactly one backend/implementation.
 * Build a separate image for every entry point, using only that backend's
 * dependency classpath. Do not force library class initialization or register
 * model reflection here: missing metadata is an observable compatibility result.
 *
 * <p>Canonical JSON is generated ahead of time on the host JVM so probe images
 * never need a second JSON backend to prepare the fixture.
 */
public final class NativeBindingCompatibility {
    private NativeBindingCompatibility() {}

    public interface Codec {
        Object read(byte[] input, Class<?> type) throws Exception;
        byte[] write(Object value, Class<?> type) throws Exception;
    }

    public static void run(String backend, String implementation, Codec codec, String[] args)
            throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected fixture directory");
        Path directory = Path.of(args[0]);
        SimpleJsonBinder verifier = new SimpleJsonBinder();
        int checks = 0;

        for (String model : new String[]{"users", "clients"}) {
            byte[] input = Files.readAllBytes(directory.resolve(model + ".json"));
            Object expectedPojo = "users".equals(model) ? Users.sample() : Clients.sample();
            @SuppressWarnings("unchecked")
            Map<String, Object> expectedMap = (Map<String, Object>) verifier.readNode(input, Map.class);

            for (String shape : new String[]{"pojo", "map"}) {
                Class<?> target = "map".equals(shape) ? Map.class
                        : "users".equals(model) ? Users.class : Clients.class;
                Object expected = "map".equals(shape) ? expectedMap : expectedPojo;
                String label = backend + "/" + implementation + "/" + model + "/" + shape;
                try {
                    Object actual = codec.read(input, target);
                    if ("pojo".equals(shape) ? !expectedPojo.equals(actual)
                            : !sameJson(expectedMap, actual)) {
                        throw new AssertionError("Read mismatch");
                    }
                    byte[] output = codec.write(expected, target);
                    Map<?, ?> actualMap = (Map<?, ?>) verifier.readNode(output, Map.class);
                    if (!sameJson(expectedMap, actualMap)) {
                        throw new AssertionError("Write mismatch");
                    }
                } catch (Exception | AssertionError failure) {
                    throw new IllegalStateException("FAILED " + label, failure);
                }
                System.out.println("PASS " + label + " read+write");
                checks += 2;
            }
        }
        System.out.println("PASS " + backend + "/" + implementation + " " + checks + "/8 checks");
    }

    /** Compare JSON structurally and numerically, without Java POJO introspection. */
    public static boolean sameJson(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        }
        if (left instanceof Map && right instanceof Map) {
            Map<?, ?> a = (Map<?, ?>) left;
            Map<?, ?> b = (Map<?, ?>) right;
            if (!a.keySet().equals(b.keySet())) return false;
            for (Object key : a.keySet()) {
                if (!sameJson(a.get(key), b.get(key))) return false;
            }
            return true;
        }
        if (left instanceof List && right instanceof List) {
            List<?> a = (List<?>) left;
            List<?> b = (List<?>) right;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                if (!sameJson(a.get(i), b.get(i))) return false;
            }
            return true;
        }
        return Objects.equals(left, right);
    }

    public static Codec runtime(Binder<?, ?> binder) {
        return new Codec() {
            @Override public Object read(byte[] input, Class<?> type) throws Exception {
                return binder.readNode(input, type);
            }
            @Override public byte[] write(Object value, Class<?> type) throws Exception {
                return binder.writeNodeAsBytes(value);
            }
        };
    }

    public static Gson gson() {
        return new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                .serializeNulls()
                .registerTypeAdapter(LocalDate.class, new TypeAdapter<LocalDate>() {
                    @Override public void write(JsonWriter out, LocalDate value) throws IOException {
                        if (value == null) out.nullValue(); else out.value(value.toString());
                    }
                    @Override public LocalDate read(JsonReader in) throws IOException {
                        if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
                        return LocalDate.parse(in.nextString());
                    }
                })
                .registerTypeAdapter(OffsetDateTime.class, new TypeAdapter<OffsetDateTime>() {
                    @Override public void write(JsonWriter out, OffsetDateTime value) throws IOException {
                        if (value == null) out.nullValue(); else out.value(value.toString());
                    }
                    @Override public OffsetDateTime read(JsonReader in) throws IOException {
                        if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
                        return OffsetDateTime.parse(in.nextString());
                    }
                })
                .create();
    }

    public static ObjectMapper jackson2() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static Codec compiled(Jackson2Binding binder) {
        return new Codec() {
            @Override public Object read(byte[] input, Class<?> type) {
                if (type == Map.class) return binder.readMap(input);
                return type == Users.class ? binder.readUsers(input) : binder.readClients(input);
            }
            @Override public byte[] write(Object value, Class<?> type) {
                if (type == Map.class) return binder.writeMap(castMap(value));
                return type == Users.class ? binder.writeUsers((Users) value) : binder.writeClients((Clients) value);
            }
        };
    }
    public static Codec compiled(Jackson3Binding binder) {
        return new Codec() {
            @Override public Object read(byte[] input, Class<?> type) {
                if (type == Map.class) return binder.readMap(input);
                return type == Users.class ? binder.readUsers(input) : binder.readClients(input);
            }
            @Override public byte[] write(Object value, Class<?> type) {
                if (type == Map.class) return binder.writeMap(castMap(value));
                return type == Users.class ? binder.writeUsers((Users) value) : binder.writeClients((Clients) value);
            }
        };
    }
    public static Codec compiled(Fastjson2Binding binder) {
        return new Codec() {
            @Override public Object read(byte[] input, Class<?> type) {
                if (type == Map.class) return binder.readMap(input);
                return type == Users.class ? binder.readUsers(input) : binder.readClients(input);
            }
            @Override public byte[] write(Object value, Class<?> type) {
                if (type == Map.class) return binder.writeMap(castMap(value));
                return type == Users.class ? binder.writeUsers((Users) value) : binder.writeClients((Clients) value);
            }
        };
    }
    public static Codec compiled(GsonBinding binder) {
        return new Codec() {
            @Override public Object read(byte[] input, Class<?> type) {
                if (type == Map.class) return binder.readMap(input);
                return type == Users.class ? binder.readUsers(input) : binder.readClients(input);
            }
            @Override public byte[] write(Object value, Class<?> type) {
                if (type == Map.class) return binder.writeMap(castMap(value));
                return type == Users.class ? binder.writeUsers((Users) value) : binder.writeClients((Clients) value);
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Binding {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }
    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Binding {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }
    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2Binding {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }
    @CompiledBinder(backend = Backend.GSON)
    public interface GsonBinding {
        @Read Users readUsers(byte[] input);
        @Read Clients readClients(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
        @Write byte[] writeUsers(Users value);
        @Write byte[] writeClients(Clients value);
        @Write byte[] writeMap(Map<String, Object> value);
    }

    // Separate entry points guarantee that image reachability does not include
    // the other backend or implementation, despite sharing the same test source.
    public static final class Jackson2Native {
        public static void main(String[] args) throws Exception {
            ObjectMapper mapper = jackson2();
            run("jackson2", "native", new Codec() {
                @Override public Object read(byte[] b, Class<?> t) throws Exception {
                    return mapper.readValue(b, t);
                }
                @Override public byte[] write(Object v, Class<?> t) throws Exception {
                    return mapper.writeValueAsBytes(v);
                }
            }, args);
        }
    }
    public static final class Jackson2Runtime {
        public static void main(String[] args) throws Exception {
            run("jackson2", "runtime", runtime(new Jackson2Binder()), args);
        }
    }
    public static final class Jackson2Compiled {
        public static void main(String[] args) throws Exception {
            run("jackson2", "compiled",
                    compiled(new NativeBindingCompatibility$Jackson2Binding_Impl()), args);
        }
    }

    public static final class Jackson3Native {
        public static void main(String[] args) throws Exception {
            JsonMapper mapper = new JsonMapper();
            run("jackson3", "native", new Codec() {
                @Override public Object read(byte[] b, Class<?> t) throws Exception {
                    return mapper.readValue(b, t);
                }
                @Override public byte[] write(Object v, Class<?> t) throws Exception {
                    return mapper.writeValueAsBytes(v);
                }
            }, args);
        }
    }
    public static final class Jackson3Runtime {
        public static void main(String[] args) throws Exception {
            run("jackson3", "runtime", runtime(new Jackson3Binder()), args);
        }
    }
    public static final class Jackson3Compiled {
        public static void main(String[] args) throws Exception {
            run("jackson3", "compiled",
                    compiled(new NativeBindingCompatibility$Jackson3Binding_Impl()), args);
        }
    }

    public static final class Fastjson2Native {
        public static void main(String[] args) throws Exception {
            run("fastjson2", "native", new Codec() {
                @Override public Object read(byte[] b, Class<?> t) {
                    return JSON.parseObject(b, t);
                }
                @Override public byte[] write(Object v, Class<?> t) {
                    return JSON.toJSONBytes(v, JSONWriter.Feature.WriteNulls);
                }
            }, args);
        }
    }
    public static final class Fastjson2Runtime {
        public static void main(String[] args) throws Exception {
            run("fastjson2", "runtime", runtime(new Fastjson2Binder()), args);
        }
    }
    public static final class Fastjson2Compiled {
        public static void main(String[] args) throws Exception {
            run("fastjson2", "compiled",
                    compiled(new NativeBindingCompatibility$Fastjson2Binding_Impl()), args);
        }
    }

    public static final class GsonNative {
        public static void main(String[] args) throws Exception {
            Gson gson = gson();
            run("gson", "native", new Codec() {
                @Override public Object read(byte[] b, Class<?> t) {
                    return gson.fromJson(new String(b, StandardCharsets.UTF_8), t);
                }
                @Override public byte[] write(Object v, Class<?> t) {
                    return gson.toJson(v).getBytes(StandardCharsets.UTF_8);
                }
            }, args);
        }
    }
    public static final class GsonRuntime {
        public static void main(String[] args) throws Exception {
            run("gson", "runtime", runtime(new GsonBinder()), args);
        }
    }
    public static final class GsonCompiled {
        public static void main(String[] args) throws Exception {
            run("gson", "compiled",
                    compiled(new NativeBindingCompatibility$GsonBinding_Impl()), args);
        }
    }
}
