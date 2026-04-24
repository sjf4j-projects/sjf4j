package org.sjf4j;


import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.Json;
import org.openjdk.jmh.Main;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.gson.GsonModule;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.jackson2.Jackson2Module;
import org.sjf4j.facade.jackson2.Jackson2Reader;
import org.sjf4j.facade.jsonp.JsonpJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.node.TypeReference;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
@Threads(1)
@State(Scope.Thread)
public class ReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{ReadBenchmark.class.getName()});
    }

//    private static final String JSON_DATA = "{\"name\":\"Alice\"}";
//    private static final String JSON_DATA = "{\"age\":25}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"city\":\"Singapore\"}}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"babies\":[{\"name\":\"Baby-0\",\"age\":1}]}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";
    // Mixed structure JSON keeps nested objects/arrays so each framework covers the same workload.
    private static final String JSON_DATA = "{\"name\":\"Alice\",\"no_way\":99,\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";

    private static final String JSON_DATA2 = "{\n" +
            "  \"name\": \"Alice\",\n" +
            "  \"friends\": [\n" +
            "    {\"name\": \"Bill\", \"active\": true, \"score\": 88.5 },\n" +
            "    {\"name\": \"Eve\", \"active\": false, \"tags\": [\"x\",\"y\"] },\n" +
            "    {\n" +
            "      \"name\": \"Cindy\",\n" +
            "      \"friends\": [\n" +
            "        {\"name\": \"David\"},\n" +
            "        {\"id\": 5, \"info\": \"blabla\"},\n" +
            "        {\"name\": \"Frank\", \"friends\": [{\"name\": \"Gina\"}, {\"name\": \"Hank\"}]},\n" +
            "        {\"name\": \"Ivy\", \"meta\": {\"a\":1,\"b\":2}},\n" +
            "        {}\n" +
            "      ]\n" +
            "    },\n" +
            "    {\"name\": \"Jane\"},\n" +
            "    {\"name\": \"Kyle\", \"friends\": [{\"name\": \"Liam\"}, {\"name\": \"Mia\"}]},\n" +
            "    {\"name\": \"Nina\", \"age\": 19, \"city\": \"SG\"}\n" +
            "  ],\n" +
            "  \"age\": 18,\n" +
            "  \"city\": \"Singapore\",\n" +
            "  \"ext\": {\"k1\": 1, \"k2\": true, \"k3\": [1,2,3]},\n" +
            "  \"extra1\": 12345,\n" +
            "  \"extra2\": \"hello\",\n" +
            "  \"extra3\": {\"nested\": {\"x\": 1, \"y\": [1,2,3,4]}}\n" +
            "}\n";

    // Kept for compatibility with older benchmark variants.
    private static final String JSON_DATA2_NO_DYN = JSON_DATA2;

    // Pure list of JojoUser to focus on array/collection parsing overhead.
    private static final String JSON_DATA2_LIST = "[\n" +
            "  {\"name\": \"Alice\", \"friends\": [\n" +
            "    {\"name\": \"Bill\", \"friends\": []},\n" +
            "    {\"name\": \"Cindy\", \"friends\": []}\n" +
            "  ]},\n" +
            "  {\"name\": \"David\", \"friends\": []}\n" +
            "]\n";

    private static final Type JOJO_USER_LIST_TYPE =
            new TypeReference<List<UserJojo>>() {}.getType();

    private static final ObjectMapper JACKSON2 = new ObjectMapper();
    private static final Gson GSON = createNativeGson();
    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT = createFastjson2NativeContext();
    private static final SimpleJsonFacade SIMPLE_JSON_FACADE = new SimpleJsonFacade();
    private static final JsonpJsonFacade JSONP_JSON_FACADE = new JsonpJsonFacade();

    static {
        JACKSON2.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static Gson createNativeGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setFieldNamingStrategy(field -> {
            String name = ReflectUtil.getExplicitName(field);
            return name != null ? name : field.getName();
        });
        return builder.create();
    }

    private static JSONReader.Context createFastjson2NativeContext() {
        ObjectReaderProvider provider = new ObjectReaderProvider();
        return JSONFactory.createReadContext(provider, JSONReader.Feature.UseDoubleForDecimals);
    }

    private static ObjectMapper createJackson2PluginMapper(SimpleModule module) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AnnotationIntrospector serializationAi = objectMapper.getSerializationConfig().getAnnotationIntrospector();
        AnnotationIntrospector deserializationAi = objectMapper.getDeserializationConfig().getAnnotationIntrospector();
        objectMapper.setAnnotationIntrospectors(
                AnnotationIntrospectorPair.create(new Jackson2Module.NodePropertyAnnotationIntrospector(), serializationAi),
                AnnotationIntrospectorPair.create(new Jackson2Module.NodePropertyAnnotationIntrospector(), deserializationAi)
        );
        objectMapper.registerModule(module);
        return objectMapper;
    }

    @State(Scope.Thread)
    public static class Jackson2PluginState {
        public ObjectMapper specializedMapper;
        public ObjectMapper sharedMapper;

        @Setup(Level.Trial)
        public void setup() {
            StreamingContext context = new StreamingContext(StreamingContext.StreamingMode.PLUGIN_MODULE);
            specializedMapper = createJackson2PluginMapper(new Jackson2Module.TwoSimpleModule(context));
            sharedMapper = createJackson2PluginMapper(new SharedStreamingJackson2ReadModule(context));
        }
    }

    @State(Scope.Thread)
    public static class FacadeState {
        @Param({"SHARED_IO", "EXCLUSIVE_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public Jackson2JsonFacade jackson2Facade;
        public Fastjson2JsonFacade fastjson2Facade;

        @Setup(Level.Trial)
        public void setup() {
            StreamingContext.StreamingMode mode = StreamingContext.StreamingMode.valueOf(streamingMode);
            StreamingContext context = new StreamingContext(mode);
            jackson2Facade = new Jackson2JsonFacade(new ObjectMapper(), context);
            fastjson2Facade = new Fastjson2JsonFacade(null, null, context);
        }
    }

    @State(Scope.Thread)
    public static class FacadeState2 {
        @Param({"SHARED_IO", "PLUGIN_MODULE"})
        public String streamingMode;

        public GsonJsonFacade gsonFacade;

        @Setup(Level.Trial)
        public void setup() {
            StreamingContext.StreamingMode mode = StreamingContext.StreamingMode.valueOf(streamingMode);
            StreamingContext context = new StreamingContext(mode);
            gsonFacade = new GsonJsonFacade(new GsonBuilder(), context);
        }
    }


    // ----- Jackson2 baselines -----
    @Benchmark
    public Object json_jackson2_pojo_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jackson2_jojo_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, UserHasAny.class);
    }

    @Benchmark
    public Object json_jackson2_map_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_plugin_specialized(Jackson2PluginState state) throws IOException {
        return state.specializedMapper.readValue(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_plugin_shared(Jackson2PluginState state) throws IOException {
        return state.sharedMapper.readValue(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jackson2_jojo_plugin_specialized(Jackson2PluginState state) throws IOException {
        return state.specializedMapper.readValue(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_jackson2_jojo_plugin_shared(Jackson2PluginState state) throws IOException {
        return state.sharedMapper.readValue(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_jackson2_map_plugin_specialized(Jackson2PluginState state) throws IOException {
        return state.specializedMapper.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_map_plugin_shared(Jackson2PluginState state) throws IOException {
        return state.sharedMapper.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_facade(FacadeState state) throws IOException {
        return state.jackson2Facade.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jackson2_jojo_facade(FacadeState state) throws IOException {
        return state.jackson2Facade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_jackson2_map_facade(FacadeState state) throws IOException {
        return state.jackson2Facade.readNode(JSON_DATA2, Map.class);
    }


    // ----- Gson baselines -----
    @Benchmark
    public Object json_gson_pojo_native() {
        return GSON.fromJson(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_gson_map_native() {
        return GSON.fromJson(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_gson_pojo_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_gson_jojo_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_gson_map_facade(FacadeState2 state) {
        return state.gsonFacade.readNode(JSON_DATA2, Map.class);
    }


    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_pojo_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(UserPojo.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_jojo_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(UserHasAny.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_map_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_pojo_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_fastjson2_jojo_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, UserJojo.class);
    }

    @Benchmark
    public Object json_fastjson2_map_facade(FacadeState state) throws IOException {
        return state.fastjson2Facade.readNode(JSON_DATA2, Map.class);
    }

    // ----- JSON-P baselines -----
    @Benchmark
    public Object json_jsonp_map_native() {
        return Json.createReader(new StringReader(JSON_DATA2)).read();
    }

    @Benchmark
    public Object json_jsonp_pojo_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_jsonp_map_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jsonp_jojo_facade() {
        return JSONP_JSON_FACADE.readNode(JSON_DATA2, UserJojo.class);
    }

    // ----- Simple JSON baselines -----
    @Benchmark
    public Object json_simple_pojo_facade() throws IOException {
        return SIMPLE_JSON_FACADE.readNode(JSON_DATA2, UserPojo.class);
    }

    @Benchmark
    public Object json_simple_jojo_facade() throws IOException {
        return SIMPLE_JSON_FACADE.readNode(JSON_DATA2, UserJojo.class);
    }



    // Define a POJO `User`
    static class UserPojo {
        public String name;
        public List<UserPojo> friends;

    }

    // Define a JOJO `JojoUser`
    static class UserJojo extends JsonObject {
        public String name;
        public List<UserJojo> friends;
    }

    static class UserHasAny {
        public String name;
        public List<UserHasAny> friends;
        @JsonAnySetter @JsonAnyGetter
        public Map<String, Object> ext = new LinkedHashMap<>();
    }

    static final class SharedStreamingJackson2ReadModule extends SimpleModule {
        SharedStreamingJackson2ReadModule(StreamingContext streamingContext) {
            String valueFormat = streamingContext.valueFormatMapping.defaultValueFormat(Instant.class);
            NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerTypeInfo(Instant.class).getFormattedValueCodecInfo(valueFormat);
            addDeserializer(Instant.class, new Jackson2Module.NodeValueDeserializer<>(vci));

            setDeserializerModifier(new BeanDeserializerModifier() {
                @Override
                public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                                                             BeanDescription beanDesc,
                                                             BeanDeserializerBuilder builder) {
                    if (JsonObject.class.isAssignableFrom(beanDesc.getBeanClass())) {
                        JavaType objType = config.constructType(Object.class);
                        if (builder.getAnySetter() == null) {
                            builder.setAnySetter(new Jackson2Module.JsonObjectAnySetter(objType));
                        }
                    }
                    return builder;
                }

                @Override
                public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                                                              BeanDescription beanDesc,
                                                              JsonDeserializer<?> deserializer) {
                    Class<?> clazz = beanDesc.getBeanClass();
                    if (JsonObject.class.isAssignableFrom(clazz)) {
                        return new SharedJsonObjectDeserializer<>(beanDesc.getType(), streamingContext);
                    }
                    if (org.sjf4j.JsonArray.class.isAssignableFrom(clazz)) {
                        return new Jackson2Module.JsonArrayDeserializer<>(clazz);
                    }
                    NodeRegistry.TypeInfo ti = NodeRegistry.registerTypeInfo(clazz);
                    if (ti.anyOfInfo != null) {
                        return new SharedAnyOfDeserializer<>(ti.anyOfInfo, streamingContext);
                    }
                    if (ti.hasValueCodecs()) {
                        String valueFormat = streamingContext.valueFormatMapping.defaultValueFormat(clazz);
                        NodeRegistry.ValueCodecInfo vci = ti.getFormattedValueCodecInfo(valueFormat);
                        if (vci != null) {
                            return new Jackson2Module.NodeValueDeserializer<>(vci);
                        }
                    }
                    if (ti.requiresPojoReader()) {
                        return new SharedPojoDeserializer<>(ti.pojoInfo, streamingContext);
                    }
                    return deserializer;
                }
            });
        }
    }

    static final class SharedJsonObjectDeserializer<T extends JsonObject> extends JsonDeserializer<T> {
        private final Type ownerType;
        private final Class<?> ownerRawClazz;
        private final NodeRegistry.PojoInfo pi;
        private final StreamingContext streamingContext;

        SharedJsonObjectDeserializer(JavaType javaType, StreamingContext streamingContext) {
            this.ownerType = toType(javaType);
            this.ownerRawClazz = Types.rawBox(ownerType);
            this.pi = ownerRawClazz == JsonObject.class ? null : NodeRegistry.registerPojoOrElseThrow(ownerRawClazz);
            this.streamingContext = streamingContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws IOException {
            if (pi == null) {
                Object value = ctxt.readValue(p, Object.class);
                if (value == null) return null;
                if (value instanceof JsonObject) return (T) value;
                if (value instanceof Map) return (T) new JsonObject((Map<String, Object>) value);
                throw new IOException("Expected object value for JsonObject, but got " + value.getClass().getName());
            }
            return (T) StreamingIO.readPojo(new Jackson2Reader(p), ownerType, ownerRawClazz, pi, streamingContext);
        }
    }

    static final class SharedAnyOfDeserializer<T> extends JsonDeserializer<T> {
        private final NodeRegistry.AnyOfInfo anyOfInfo;
        private final StreamingContext streamingContext;

        SharedAnyOfDeserializer(NodeRegistry.AnyOfInfo anyOfInfo, StreamingContext streamingContext) {
            this.anyOfInfo = anyOfInfo;
            this.streamingContext = streamingContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws IOException {
            return (T) StreamingIO.readAnyOf(new Jackson2Reader(p), anyOfInfo, streamingContext);
        }
    }

    static final class SharedPojoDeserializer<T> extends JsonDeserializer<T> {
        private final NodeRegistry.PojoInfo pi;
        private final StreamingContext streamingContext;

        SharedPojoDeserializer(NodeRegistry.PojoInfo pi, StreamingContext streamingContext) {
            this.pi = pi;
            this.streamingContext = streamingContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws IOException {
            return (T) StreamingIO.readPojo(new Jackson2Reader(p), pi.clazz, pi.clazz, pi, streamingContext);
        }
    }

    private static Type toType(JavaType javaType) {
        if (javaType == null) return Object.class;
        Class<?> raw = javaType.getRawClass();
        if (raw == null) return Object.class;
        int n = javaType.containedTypeCount();
        if (n <= 0) return raw;
        Type[] args = new Type[n];
        for (int i = 0; i < n; i++) {
            args[i] = toType(javaType.containedType(i));
        }
        return new Types.ParameterizedTypeImpl(raw, args, raw.getDeclaringClass());
    }


}
