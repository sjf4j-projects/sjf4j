package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.NodeConfig;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Format;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;
import org.sjf4j.testbench.model.fabien.Users;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Temporary experiment: only input binding is measured, not validation. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
public class ExactNumbersReadBenchmark {
    @Param({"jackson2", "jackson3", "fastjson2", "gson", "jsonp", "simple", "snake"})
    public String backend;
    @Param({"runtime", "compiled"})
    public String implementation;
    @Param({"false", "true"})
    public boolean exactNumbers;
    @Param({"pojo", "map"})
    public String shape;
    @Param({"10", "100"})
    public int sizeKb;
    @Param({"12345", "324"})
    public long seed;

    private byte[] input;
    private FabienBindings.Reader reader;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        FabienFixtures.Fixture fixture = FabienFixtures.create(backend, "users", shape, sizeKb, seed);
        input = fixture.input;
        boolean map = "map".equals(shape);
        if (!exactNumbers) {
            reader = FabienBindings.reader(backend, implementation, "users", shape);
        } else if ("runtime".equals(implementation)) {
            Binder<?, ?> binder = switch (backend) {
                case "jackson2" -> new Jackson2Binder(NodeConfig.EXACT_NUMBERS);
                case "jackson3" -> new Jackson3Binder(NodeConfig.EXACT_NUMBERS);
                case "fastjson2" -> new Fastjson2Binder(NodeConfig.EXACT_NUMBERS);
                case "gson" -> new GsonBinder(NodeConfig.EXACT_NUMBERS);
                case "jsonp" -> new JsonpBinder(NodeConfig.EXACT_NUMBERS);
                case "simple" -> new SimpleJsonBinder(NodeConfig.EXACT_NUMBERS);
                case "snake" -> new SnakeBinder(NodeConfig.EXACT_NUMBERS);
                default -> throw new IllegalArgumentException(backend);
            };
            Class<?> target = map ? Map.class : Users.class;
            reader = bytes -> binder.readNode(bytes, target);
        } else {
            reader = switch (backend) {
                case "jackson2" -> {
                    Jackson2Exact b = CompiledInstances.create(Jackson2Exact.class);
                    yield map ? b::readMap : b::readUsers;
                }
                case "jackson3" -> {
                    Jackson3Exact b = CompiledInstances.create(Jackson3Exact.class);
                    yield map ? b::readMap : b::readUsers;
                }
                case "fastjson2" -> {
                    Fastjson2Exact b = CompiledInstances.create(Fastjson2Exact.class);
                    yield map ? b::readMap : b::readUsers;
                }
                case "gson" -> {
                    GsonExact b = CompiledInstances.create(GsonExact.class);
                    yield map ? b::readMap : b::readUsers;
                }
                case "jsonp" -> {
                    JsonpExact b = CompiledInstances.create(JsonpExact.class);
                    yield map ? b::readMap : b::readUsers;
                }
                case "simple" -> {
                    SimpleExact b = CompiledInstances.create(SimpleExact.class);
                    yield map ? b::readMap : b::readUsers;
                }
                case "snake" -> {
                    SnakeExact b = CompiledInstances.create(SnakeExact.class);
                    yield map ? b::readMap : b::readUsers;
                }
                default -> throw new IllegalArgumentException(backend);
            };
        }
        Object actual = reader.read(input);
        Object expected = fixture.value;
        if (map && exactNumbers && !"snake".equals(backend)) {
            expected = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(input, Map.class);
        }
        FabienFixtures.verify(backend + "/" + implementation + "/" + exactNumbers + "/" + shape,
                expected, actual);
        if (map) {
            List<?> users = (List<?>) ((Map<?, ?>) actual).get("users");
            for (Object item : users) {
                Map<?, ?> user = (Map<?, ?>) item;
                for (String key : List.of("latitude", "longitude")) {
                    Object coordinate = user.get(key);
                    if (exactNumbers && !(coordinate instanceof BigDecimal)
                            || !exactNumbers && !(coordinate instanceof Double)) {
                        throw new IllegalStateException("Unexpected coordinate type: " + coordinate.getClass());
                    }
                }
            }
        }
    }

    @Benchmark
    public Object read() throws Exception {
        return reader.read(input);
    }

    @CompiledBinder(backend = Backend.JACKSON2, exactNumbers = true)
    public interface Jackson2Exact {
        @Read Users readUsers(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
    }

    @CompiledBinder(backend = Backend.JACKSON3, exactNumbers = true)
    public interface Jackson3Exact {
        @Read Users readUsers(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
    }

    @CompiledBinder(backend = Backend.FASTJSON2, exactNumbers = true)
    public interface Fastjson2Exact {
        @Read Users readUsers(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
    }

    @CompiledBinder(backend = Backend.GSON, exactNumbers = true)
    public interface GsonExact {
        @Read Users readUsers(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
    }

    @CompiledBinder(backend = Backend.JSONP, exactNumbers = true)
    public interface JsonpExact {
        @Read Users readUsers(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
    }

    @CompiledBinder(backend = Backend.SIMPLE, exactNumbers = true)
    public interface SimpleExact {
        @Read Users readUsers(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
    }

    @CompiledBinder(backend = Backend.SNAKE, format = Format.YAML, exactNumbers = true)
    public interface SnakeExact {
        @Read Users readUsers(byte[] input);
        @Read Map<String, Object> readMap(byte[] input);
    }
}
