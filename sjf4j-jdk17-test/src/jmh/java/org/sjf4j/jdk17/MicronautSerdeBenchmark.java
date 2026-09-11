package org.sjf4j.jdk17;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import io.micronaut.serde.support.deserializers.ErrorCatchingDeserializer;
import io.micronaut.serde.support.serializers.ErrorCatchingSerializer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Micronaut Serde 3.1.1 requires JDK 25 for this processor/runtime JMH comparison;
 * the module's normal Java source target remains 17.
 */
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class MicronautSerdeBenchmark {
    private static final Argument<Users> USERS = Argument.of(Users.class);
    private static final Argument<?>[] MODEL_TYPES = {
            Argument.of(Users.class),
            Argument.of(User.class),
            Argument.of(Address.class),
            Argument.of(Friend.class)
    };

    @State(Scope.Thread)
    public static class Holder {
        @Param({"JACKSON", "JACKSON_HANDWRITTEN", "JACKSON_BLACKBIRD", "MICRONAUT_GENERATED", "MICRONAUT_RUNTIME"})
        public String stack;

        private ApplicationContext context;
        private JsonMapper jackson;
        private Jackson3HandUsersWriter jacksonHandwritten;
        private boolean handwritten;
        private ObjectMapper micronaut;
        private Users value;
        private byte[] input;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            value = users();
            jackson = JsonMapper.builder().build();
            input = jackson.writeValueAsBytes(value);
            jacksonHandwritten = new Jackson3HandUsersWriter(jackson.tokenStreamFactory());
            handwritten = "JACKSON_HANDWRITTEN".equals(stack);
            if (!Arrays.equals(input, jacksonHandwritten.write(value))) {
                throw new IllegalStateException("JACKSON_HANDWRITTEN bytes differ from Jackson 3 native output");
            }

            if ("JACKSON".equals(stack) || handwritten) {
                return;
            }
            if ("JACKSON_BLACKBIRD".equals(stack)) {
                jackson = JsonMapper.builder().addModule(new BlackbirdModule()).build();
                assertJson(input, jackson.writeValueAsBytes(value), stack);
                return;
            }

            Map<String, Object> properties = new java.util.LinkedHashMap<String, Object>();
            properties.put("micronaut.serde.serialization.inclusion", "ALWAYS");
            if ("MICRONAUT_RUNTIME".equals(stack)) {
                properties.put("micronaut.serde.serialization.disable-generated-serializer", true);
                properties.put("micronaut.serde.deserialization.disable-generated-deserializer", true);
            }
            context = ApplicationContext.run(properties);
            micronaut = (ObjectMapper) context.getBean(JacksonJsonMapper.class).createSpecific(USERS);

            if ("MICRONAUT_GENERATED".equals(stack)) {
                assertGenerated(micronaut.getSerdeRegistry());
            } else if ("MICRONAUT_RUNTIME".equals(stack)) {
                assertRuntime(micronaut.getSerdeRegistry());
            } else {
                throw new IllegalArgumentException(stack);
            }
            assertJson(input, micronaut.writeValueAsBytes(USERS, value), stack);
            Users decoded = micronaut.readValue(input, USERS);
            if (decoded == null || decoded.users.length != 1 || decoded.users[0].friends.length != 20) {
                throw new IllegalStateException(stack + " did not read the benchmark graph");
            }
            assertJson(input, jackson.writeValueAsBytes(decoded), stack + " decoded graph");
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }

        private static void assertJson(byte[] expected, byte[] actual, String stack) throws IOException {
            JsonMapper mapper = JsonMapper.builder().build();
            if (!mapper.readTree(expected).equals(mapper.readTree(actual))) {
                throw new IllegalStateException(stack + " JSON differs from canonical tree");
            }
        }

        private static void assertGenerated(SerdeRegistry registry) throws Exception {
            for (Argument<?> type : MODEL_TYPES) {
                Serializer<?> serializer = specificSerializer(registry, type);
                Deserializer<?> deserializer = specificDeserializer(registry, type);
                assertGenerated(type, "serializer", unwrapSerializer(serializer));
                assertGenerated(type, "deserializer", unwrapDeserializer(deserializer));
            }
        }

        private static void assertGenerated(Argument<?> type, String direction, Object serde) {
            String name = serde.getClass().getName();
            if (!name.startsWith("org.sjf4j.jdk17.Serde") || name.contains(".support.")) {
                throw new IllegalStateException("Generated " + type.getTypeName() + " " + direction + " is not per-type: " + name);
            }
        }

        private static void assertRuntime(SerdeRegistry registry) throws Exception {
            for (Argument<?> type : MODEL_TYPES) {
                Serializer<?> serializer = (Serializer<?>) unwrapSerializer(specificSerializer(registry, type));
                Deserializer<?> deserializer = (Deserializer<?>) unwrapDeserializer(specificDeserializer(registry, type));
                String names = serializer.getClass().getName() + " / " + deserializer.getClass().getName();
                String expected = "io.micronaut.serde.support.serializers.SimpleObjectSerializer / "
                        + "io.micronaut.serde.support.deserializers.SimpleObjectDeserializer";
                if (!expected.equals(names)) {
                    throw new IllegalStateException("Runtime " + type.getTypeName() + " specific serdes differ: " + names);
                }
            }
        }

        private static Object unwrapSerializer(Object value) {
            while (value instanceof ErrorCatchingSerializer) {
                value = ((ErrorCatchingSerializer<?>) value).getSerializer();
            }
            return value;
        }

        private static Object unwrapDeserializer(Object value) {
            while (value instanceof ErrorCatchingDeserializer) {
                value = ((ErrorCatchingDeserializer<?>) value).getDeserializer();
            }
            return value;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Serializer<?> specificSerializer(SerdeRegistry registry, Argument<?> type) throws Exception {
            Serializer serializer = registry.findSerializer((Argument) type);
            return serializer.createSpecific(registry.newEncoderContext(null), (Argument) type);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Deserializer<?> specificDeserializer(SerdeRegistry registry, Argument<?> type) throws Exception {
            Deserializer deserializer = registry.findDeserializer((Argument) type);
            return deserializer.createSpecific(registry.newDecoderContext(null), (Argument) type);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void serialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.micronaut == null ? (holder.handwritten
                ? holder.jacksonHandwritten.write(holder.value) : holder.jackson.writeValueAsBytes(holder.value))
                : holder.micronaut.writeValueAsBytes(USERS, holder.value));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void deserialize(Holder holder, Blackhole blackhole) throws IOException {
        // JACKSON_HANDWRITTEN deliberately uses Jackson 3 native parsing: it isolates the write path.
        blackhole.consume(holder.micronaut == null ? holder.jackson.readValue(holder.input, Users.class)
                : holder.micronaut.readValue(holder.input, USERS));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void roundTrip(Holder holder, Blackhole blackhole) throws IOException {
        byte[] bytes = holder.micronaut == null ? (holder.handwritten
                ? holder.jacksonHandwritten.write(holder.value) : holder.jackson.writeValueAsBytes(holder.value))
                : holder.micronaut.writeValueAsBytes(USERS, holder.value);
        blackhole.consume(holder.micronaut == null ? holder.jackson.readValue(bytes, Users.class)
                : holder.micronaut.readValue(bytes, USERS));
    }

    private static Users users() {
        Users users = new Users();
        users.requestId = "request-1001";
        users.generatedAt = 1720000000123L;
        users.source = "benchmark";
        User user = new User();
        user.id = 42;
        user.name = "Ada";
        user.email = "ada@example.test";
        user.age = 36;
        user.active = true;
        user.score = 99.75;
        user.balance = 12345.67;
        user.createdAt = 1710000000000L;
        user.updatedAt = 1720000000000L;
        user.loginCount = 123;
        user.rank = 7;
        user.verified = true;
        user.department = "engineering";
        user.title = "principal";
        user.phone = "+1-555-0100";
        user.website = "https://example.test";
        user.locale = "en_US";
        user.timeZone = "UTC";
        user.status = "ACTIVE";
        user.note = "rich public-field serde graph";
        user.address = new Address();
        user.address.street = "123 Engine Way";
        user.address.city = "London";
        user.address.state = "Greater London";
        user.address.postalCode = "SW1A 1AA";
        user.address.country = "GB";
        user.address.latitude = 51.5014;
        user.address.longitude = -0.1419;
        user.tags = new String[24];
        for (int i = 0; i < user.tags.length; i++) {
            user.tags[i] = "tag-" + i;
        }
        user.friends = new Friend[20];
        for (int i = 0; i < user.friends.length; i++) {
            Friend friend = new Friend();
            friend.id = 1000 + i;
            friend.name = "Friend " + i;
            friend.email = "friend" + i + "@example.test";
            friend.age = 20 + i;
            friend.active = (i & 1) == 0;
            friend.score = 50 + i;
            friend.since = 1600000000000L + i;
            user.friends[i] = friend;
        }
        users.users = new User[]{user};
        return users;
    }
}
