package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeConfig;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledExactNumbersAnnotationTest {
    private static final String DECIMAL = "0.123456789123456789";
    private static final String MODEL = "{\"number\":" + DECIMAL
            + ",\"raw\":" + DECIMAL + ",\"map\":{\"n\":" + DECIMAL + "}"
            + ",\"list\":[" + DECIMAL + "],\"object\":{\"n\":" + DECIMAL + "}"
            + ",\"array\":[" + DECIMAL + "],\"child\":{\"number\":" + DECIMAL + "}"
            + ",\"typed\":1.25,\"decimal\":" + DECIMAL + "}";
    private static final String[] BACKENDS = {
            "SIMPLE", "JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP", "SNAKE"
    };

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void annotationControlsAllReadPathsIncludingInheritedMethods() throws Exception {
        for (String backend : BACKENDS) {
            String format = backend.equals("SNAKE") ? ",format=Format.YAML" : "";
            String annotation = "@CompiledBinder(backend=Backend." + backend + format;
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Model.java",
                    "package fixture; import java.util.*; import org.sjf4j.*;"
                    + "public class Model { public Number number; public Object raw;"
                    + " public Map<String,Object> map; public List<Number> list;"
                    + " public JsonObject object; public JsonArray array; public Model child;"
                    + " public double typed; public java.math.BigDecimal decimal; }");
            sources.put("fixture/Base.java",
                    "package fixture; import org.sjf4j.annotation.binding.Read;"
                    + "public interface Base { @Read Number inherited(String json); }");
            sources.put("fixture/Binder.java",
                    "package fixture; import java.util.*; import java.io.*; import org.sjf4j.annotation.binding.*;"
                    + annotation + ",exactNumbers=true) public interface Binder extends Base {"
                    + " @Read Model read(String json);"
                    + " @Read Model bytes(byte[] json); @Read Model stream(InputStream json);"
                    + " @Read Model reader(Reader json); @Read Object any(String json);"
                    + " @Read Map<String,Object> map(String json); @Read List<Object> list(String json); }");
            sources.put("fixture/DefaultBinder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + annotation + ") public interface DefaultBinder extends Binder {}");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("readNumberExact()"), generated);
            assertFalse(generated.contains("readNumber()"), generated);
            assertTrue(generated.contains("NodeConfig.EXACT_NUMBERS"), generated);
            String ordinarySource = compiled.generatedSource("fixture/DefaultBinder_Impl.java");
            assertTrue(ordinarySource.contains("readNumber()"), ordinarySource);
            assertFalse(ordinarySource.contains("readNumberExact()"), ordinarySource);
            assertTrue(ordinarySource.contains("NodeConfig.DEFAULT"), ordinarySource);
            assertEquals(backend.equals("FASTJSON2"), generated.contains(".forCompiledRead("), generated);
            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class type = loader.loadClass("fixture.Binder");
                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                assertEquals(0, Arrays.stream(impl.getDeclaredFields())
                        .filter(f -> f.getType() == NodeConfig.class).count(), backend);
                Class defaultType = loader.loadClass("fixture.DefaultBinder");
                if (backend.equals("FASTJSON2")) {
                    for (Class<?> single : new Class<?>[]{impl, loader.loadClass("fixture.DefaultBinder_Impl")}) {
                        assertEquals(1, Arrays.stream(single.getDeclaredFields())
                                .filter(f -> f.getType() == Fastjson2Binder.class).count());
                    }
                }
                for (NodeConfig config : new NodeConfig[]{NodeConfig.DEFAULT, NodeConfig.EXACT_NUMBERS}) {
                    Object binder = CompiledTestBinders.create(type, backend, config);
                    checkModel(type.getMethod("read", String.class).invoke(binder, MODEL), true);
                    Object ordinary = CompiledTestBinders.create(defaultType, backend, config);
                    checkModel(type.getMethod("read", String.class).invoke(ordinary, MODEL), false);
                    byte[] bytes = MODEL.getBytes(StandardCharsets.UTF_8);
                    checkModel(type.getMethod("bytes", byte[].class).invoke(binder, bytes), true);
                    checkModel(type.getMethod("stream", InputStream.class)
                            .invoke(binder, new ByteArrayInputStream(bytes)), true);
                    checkModel(type.getMethod("reader", Reader.class)
                            .invoke(binder, new StringReader(MODEL)), true);
                    checkNumber(type.getMethod("inherited", String.class).invoke(binder, DECIMAL), true);
                    checkNumber(type.getMethod("inherited", String.class).invoke(ordinary, DECIMAL), false);
                    checkNumber(type.getMethod("any", String.class).invoke(binder, DECIMAL), true);
                    checkNumber(type.getMethod("any", String.class).invoke(ordinary, DECIMAL), false);
                    Map<?, ?> map = (Map<?, ?>) type.getMethod("map", String.class)
                            .invoke(binder, "{\"n\":" + DECIMAL + "}");
                    checkNumber(map.get("n"), true);
                    List<?> list = (List<?>) type.getMethod("list", String.class)
                            .invoke(binder, "[" + DECIMAL + ",1,9223372036854775808,1.25e-400]");
                    checkNumber(list.get(0), true);
                    assertEquals(1, list.get(1));
                    assertEquals(new BigInteger("9223372036854775808"), list.get(2));
                    assertEquals(0, new BigDecimal("1.25e-400").compareTo((BigDecimal) list.get(3)));

                }
                checkNumber(type.getMethod("inherited", String.class)
                        .invoke(CompiledInstances.create(type), DECIMAL), true);
                checkNumber(type.getMethod("inherited", String.class)
                        .invoke(CompiledInstances.create(defaultType), DECIMAL), false);
            }
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void exactPolicyReachesCodecsAndPolymorphicFallback() throws Exception {
        for (String backend : BACKENDS) {
            String format = backend.equals("SNAKE") ? ",format=Format.YAML" : "";
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Wrapped.java",
                    "package fixture; public final class Wrapped { public final Number n;"
                    + " public Wrapped(Number n) { this.n=n; } }");
            sources.put("fixture/Codecs.java",
                    "package fixture; import org.sjf4j.annotation.node.*; import org.sjf4j.value.ValueCodec;"
                    + " @NodeRegistry public interface Codecs {"
                    + " @NodeValue ValueCodec<Wrapped,Number> WRAPPED = new ValueCodec.SimpleValueCodec<>("
                    + " Wrapped.class, Number.class, v -> v.n, Wrapped::new); }");
            sources.put("fixture/Animal.java",
                    "package fixture; import org.sjf4j.annotation.node.*;"
                    + " @OneOf(discriminatorKey=\"kind\",cases={@OneOfCase(type=Dog.class,when=\"dog\")})"
                    + " public interface Animal {}");
            sources.put("fixture/Dog.java",
                    "package fixture; public class Dog implements Animal { public String kind;"
                    + " public Number n; public Object raw; }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + format
                    + ",exactNumbers=true,registries={Codecs.class}) public interface Binder {"
                    + " @Read Wrapped codec(String json); @Read Animal animal(String json); }");
            sources.put("fixture/DefaultBinder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + format
                    + ",registries={Codecs.class}) public interface DefaultBinder extends Binder {}");
            NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                org.sjf4j.value.ValueRegistry.registerAnnotated(loader.loadClass("fixture.Codecs"));
                Class type = loader.loadClass("fixture.Binder");
                Object binder = CompiledTestBinders.create(type, backend, NodeConfig.DEFAULT);
                Object ordinaryBinder = CompiledTestBinders.create(loader.loadClass("fixture.DefaultBinder"),
                        backend, NodeConfig.EXACT_NUMBERS);
                Object wrapped = type.getMethod("codec", String.class).invoke(binder, DECIMAL);
                checkNumber(wrapped.getClass().getField("n").get(wrapped), true);
                Object ordinary = type.getMethod("codec", String.class).invoke(ordinaryBinder, DECIMAL);
                checkNumber(ordinary.getClass().getField("n").get(ordinary), false);
                for (boolean exact : new boolean[]{true, false}) {
                    // Values on both sides of the discriminator must use the same policy.
                    String json = "{\"n\":" + DECIMAL + ",\"kind\":\"dog\",\"raw\":" + DECIMAL + "}";
                    Object animal = type.getMethod("animal", String.class)
                            .invoke(exact ? binder : ordinaryBinder, json);
                    checkNumber(animal.getClass().getField("n").get(animal), exact);
                    checkNumber(animal.getClass().getField("raw").get(animal), exact);
                }
            }
        }
    }

    private static void checkModel(Object model, boolean exact) throws Exception {
        Class<?> type = model.getClass();
        checkNumber(type.getField("number").get(model), exact);
        checkNumber(type.getField("raw").get(model), exact);
        checkNumber(((Map<?, ?>) type.getField("map").get(model)).get("n"), exact);
        checkNumber(((List<?>) type.getField("list").get(model)).get(0), exact);
        checkNumber(((JsonObject) type.getField("object").get(model)).getNumber("n"), exact);
        checkNumber(((JsonArray) type.getField("array").get(model)).get(0, Number.class), exact);
        checkNumber(type.getField("number").get(type.getField("child").get(model)), exact);
        assertEquals(1.25d, type.getField("typed").getDouble(model));
        assertEquals(new BigDecimal(DECIMAL), type.getField("decimal").get(model));
    }

    private static void checkNumber(Object value, boolean exact) {
        if (exact) {
            assertEquals(0, new BigDecimal(DECIMAL).compareTo(assertInstanceOf(BigDecimal.class, value)));
        } else {
            assertEquals(Double.valueOf(DECIMAL), assertInstanceOf(Double.class, value));
        }
    }
}
