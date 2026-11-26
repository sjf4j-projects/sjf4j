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
    void annotationControlsAllReadPathsAndInheritedOverrides() throws Exception {
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
                    + "public interface Base { @Read Number inherited(String json);"
                    + " @Read(exactNumbers=false) Number inheritedOrdinary(String json); }");
            sources.put("fixture/Binder.java",
                    "package fixture; import java.util.*; import java.io.*; import org.sjf4j.annotation.binding.*;"
                    + annotation + ",exactNumbers=true) public interface Binder extends Base {"
                    + " @Read Model read(String json); @Read(exactNumbers=false) Model ordinary(String json);"
                    + " @Read Model bytes(byte[] json); @Read Model stream(InputStream json);"
                    + " @Read Model reader(Reader json); @Read Object any(String json);"
                    + " @Read Map<String,Object> map(String json); @Read List<Object> list(String json);"
                    + " @Read(exactNumbers=false) Object ordinaryAny(String json); }");
            sources.put("fixture/DefaultBinder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + annotation + ") public interface DefaultBinder {"
                    + " @Read Number read(String json); @Read(exactNumbers=true) Number exact(String json); }");
            sources.put("fixture/ExactOnly.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + annotation + ",exactNumbers=true) public interface ExactOnly { @Read Number read(String json); }");
            sources.put("fixture/OrdinaryOnly.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + annotation + ") public interface OrdinaryOnly { @Read Number read(String json); }");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("readNumberExact()"), generated);
            assertTrue(generated.contains("readNumber()"), generated);
            assertTrue(generated.contains("NodeConfig.EXACT_NUMBERS"), generated);
            assertTrue(generated.contains("NodeConfig.DEFAULT"), generated);
            assertEquals(backend.equals("FASTJSON2"), generated.contains(".forCompiledRead("), generated);
            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class type = loader.loadClass("fixture.Binder");
                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                assertEquals(0, Arrays.stream(impl.getDeclaredFields())
                        .filter(f -> f.getType() == NodeConfig.class).count(), backend);
                Class defaultType = loader.loadClass("fixture.DefaultBinder");
                Class exactOnly = loader.loadClass("fixture.ExactOnly");
                Class ordinaryOnly = loader.loadClass("fixture.OrdinaryOnly");
                if (backend.equals("FASTJSON2")) {
                    assertEquals(2, Arrays.stream(impl.getDeclaredFields())
                            .filter(f -> f.getType() == Fastjson2Binder.class).count());
                    for (String simpleName : new String[]{"ExactOnly", "OrdinaryOnly"}) {
                        Class<?> single = loader.loadClass("fixture." + simpleName + "_Impl");
                        assertEquals(1, Arrays.stream(single.getDeclaredFields())
                                .filter(f -> f.getType() == Fastjson2Binder.class).count());
                    }
                }
                for (NodeConfig config : new NodeConfig[]{NodeConfig.DEFAULT, NodeConfig.EXACT_NUMBERS}) {
                    Object binder = CompiledTestBinders.create(type, backend, config);
                    checkModel(type.getMethod("read", String.class).invoke(binder, MODEL), true);
                    checkModel(type.getMethod("ordinary", String.class).invoke(binder, MODEL), false);
                    byte[] bytes = MODEL.getBytes(StandardCharsets.UTF_8);
                    checkModel(type.getMethod("bytes", byte[].class).invoke(binder, bytes), true);
                    checkModel(type.getMethod("stream", InputStream.class)
                            .invoke(binder, new ByteArrayInputStream(bytes)), true);
                    checkModel(type.getMethod("reader", Reader.class)
                            .invoke(binder, new StringReader(MODEL)), true);
                    checkNumber(type.getMethod("inherited", String.class).invoke(binder, DECIMAL), true);
                    checkNumber(type.getMethod("inheritedOrdinary", String.class).invoke(binder, DECIMAL), false);
                    checkNumber(type.getMethod("any", String.class).invoke(binder, DECIMAL), true);
                    checkNumber(type.getMethod("ordinaryAny", String.class).invoke(binder, DECIMAL), false);
                    Map<?, ?> map = (Map<?, ?>) type.getMethod("map", String.class)
                            .invoke(binder, "{\"n\":" + DECIMAL + "}");
                    checkNumber(map.get("n"), true);
                    List<?> list = (List<?>) type.getMethod("list", String.class)
                            .invoke(binder, "[" + DECIMAL + ",1,9223372036854775808,1.25e-400]");
                    checkNumber(list.get(0), true);
                    assertEquals(1, list.get(1));
                    assertEquals(new BigInteger("9223372036854775808"), list.get(2));
                    assertEquals(0, new BigDecimal("1.25e-400").compareTo((BigDecimal) list.get(3)));

                    Object inverse = CompiledTestBinders.create(defaultType, backend, config);
                    checkNumber(defaultType.getMethod("read", String.class).invoke(inverse, DECIMAL), false);
                    checkNumber(defaultType.getMethod("exact", String.class).invoke(inverse, DECIMAL), true);
                    checkNumber(exactOnly.getMethod("read", String.class).invoke(
                            CompiledTestBinders.create(exactOnly, backend, config), DECIMAL), true);
                    checkNumber(ordinaryOnly.getMethod("read", String.class).invoke(
                            CompiledTestBinders.create(ordinaryOnly, backend, config), DECIMAL), false);
                }
                checkNumber(exactOnly.getMethod("read", String.class)
                        .invoke(CompiledInstances.create(exactOnly), DECIMAL), true);
                checkNumber(ordinaryOnly.getMethod("read", String.class)
                        .invoke(CompiledInstances.create(ordinaryOnly), DECIMAL), false);
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
