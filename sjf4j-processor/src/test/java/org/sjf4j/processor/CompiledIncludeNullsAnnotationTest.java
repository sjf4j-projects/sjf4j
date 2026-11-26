package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The annotation determines the null policy for every backend, including
 * Runtime fallback. Concrete backend configuration is ignored by compiled binders.
 */
class CompiledIncludeNullsAnnotationTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void annotationControlsCompiledAndRuntimeFallbackForAllBackends() throws Exception {
        for (String backend : new String[]{
                "SIMPLE", "JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP", "SNAKE"}) {
            String format = backend.equals("SNAKE") ? ",format=Format.YAML" : "";
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Model.java",
                    "package fixture; import java.util.*;"
                    + "public class Model {"
                    + " public String missing;"
                    + " public List<String> tags = Arrays.asList(\"a\", null, \"b\");"
                    + " public Map<String,Object> extras = new LinkedHashMap<>();"
                    + " public Object unknown = new LinkedHashMap<String,Object>();"
                    + " public Model() { extras.put(\"keep\", 1); extras.put(\"missing\", null);"
                    + " ((Map<String,Object>)unknown).put(\"missing\", null);"
                    + " ((Map<String,Object>)unknown).put(\"items\", Arrays.asList(null, \"x\")); }"
                    + "}");
            sources.put("fixture/Base.java",
                    "package fixture; import org.sjf4j.annotation.binding.Write;"
                    + "public interface Base {"
                    + " @Write String inherited(Object value);"
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + format + ",includeNulls=false)"
                    + "public interface Binder extends Base {"
                    + " @Write String write(Model value);"
                    + " @Write byte[] writeBytes(Model value);"
                    + " @Write String writeAny(Object value);"
                    + "}");
            sources.put("fixture/DefaultBinder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + format + ")"
                    + "public interface DefaultBinder extends Binder {}");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class binderType = loader.loadClass("fixture.Binder");
                Class<?> implType = loader.loadClass("fixture.Binder_Impl");
                assertEquals(0, Arrays.stream(implType.getDeclaredFields())
                        .filter(field -> field.getType() == NodeConfig.class).count(), backend);
                assertThrows(NoSuchMethodException.class, () -> implType.getConstructor(NodeConfig.class), backend);
                assertNotNull(implType.getConstructor(), backend);
                Class modelType = loader.loadClass("fixture.Model");
                Object model = modelType.getConstructor().newInstance();
                Object injectedTrue = CompiledTestBinders.create(binderType, backend, new NodeConfig(true));

                Class defaultBinderType = loader.loadClass("fixture.DefaultBinder");
                Class<?> defaultImpl = loader.loadClass("fixture.DefaultBinder_Impl");
                assertEquals(0, Arrays.stream(defaultImpl.getDeclaredFields())
                        .filter(field -> field.getType() == NodeConfig.class).count(), backend);
                Object includeDefault = CompiledTestBinders.create(defaultBinderType, backend, new NodeConfig(false));
                Map<String, Object> testDynamic = new LinkedHashMap<>();
                testDynamic.put("missing", null);
                testDynamic.put("present", Arrays.asList("a", null, "b"));
                Map<String, Object> includeDefaultResult = decode(backend, (String) defaultBinderType
                        .getMethod("writeAny", Object.class).invoke(includeDefault, testDynamic));
                Map<String, Object> omitResult = decode(backend, (String) binderType
                        .getMethod("writeAny", Object.class).invoke(injectedTrue, testDynamic));
                assertTrue(includeDefaultResult.containsKey("missing"), backend);
                assertFalse(omitResult.containsKey("missing"), backend);
                assertEquals(Arrays.asList("a", null, "b"), omitResult.get("present"), backend);

                for (NodeConfig config : new NodeConfig[]{NodeConfig.DEFAULT, NodeConfig.OMIT_NULLS}) {
                    Object binder = CompiledTestBinders.create(binderType, backend, config);
                    Object including = CompiledTestBinders.create(defaultBinderType, backend, config);
                    Map<String, Object> omitted = decode(backend, (String) binderType
                            .getMethod("write", modelType).invoke(binder, model));
                    Map<String, Object> included = decode(backend, (String) binderType
                            .getMethod("write", modelType).invoke(including, model));
                    checkModel(omitted, false);
                    checkModel(included, true);

                    byte[] omittedBytes = (byte[]) binderType.getMethod("writeBytes", modelType)
                            .invoke(binder, model);
                    byte[] includedBytes = (byte[]) binderType.getMethod("writeBytes", modelType)
                            .invoke(including, model);
                    assertEquals(omitted, decode(backend, new String(omittedBytes, StandardCharsets.UTF_8)), backend);
                    assertEquals(included, decode(backend, new String(includedBytes, StandardCharsets.UTF_8)), backend);

                    Map<String, Object> dynamic = new LinkedHashMap<>();
                    dynamic.put("missing", null);
                    dynamic.put("present", Arrays.asList("a", null, "b"));
                    for (String prefix : new String[]{"writeAny", "inherited"}) {
                        Map<String, Object> omitDynamic = decode(backend, (String) binderType
                                .getMethod(prefix, Object.class).invoke(binder, dynamic));
                        Map<String, Object> includeDynamic = decode(backend, (String) binderType
                                .getMethod(prefix, Object.class).invoke(including, dynamic));
                        assertFalse(omitDynamic.containsKey("missing"), backend + " " + prefix);
                        assertTrue(includeDynamic.containsKey("missing"), backend + " " + prefix);
                        assertEquals(Arrays.asList("a", null, "b"), omitDynamic.get("present"), backend);
                        assertEquals(omitDynamic.get("present"), includeDynamic.get("present"), backend);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decode(String backend, String text) {
        return backend.equals("SNAKE")
                ? (Map<String, Object>) new SnakeBinder().readNode(text, Map.class)
                : (Map<String, Object>) new SimpleJsonBinder().readNode(text, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static void checkModel(Map<String, Object> value, boolean includeNulls) {
        assertEquals(includeNulls, value.containsKey("missing"));
        assertEquals(Arrays.asList("a", null, "b"), value.get("tags"));
        Map<String, Object> extras = (Map<String, Object>) value.get("extras");
        assertEquals(includeNulls, extras.containsKey("missing"));
        Map<String, Object> unknown = (Map<String, Object>) value.get("unknown");
        assertEquals(includeNulls, unknown.containsKey("missing"));
        assertEquals(Arrays.asList(null, "x"), unknown.get("items"));
    }


    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void valueCodecRawMapFallbackUsesInterfaceNullPolicy() throws Exception {
        for (String backend : new String[]{
                "SIMPLE", "JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP", "SNAKE"}) {
            String format = backend.equals("SNAKE") ? ",format=Format.YAML" : "";
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Token.java",
                    "package fixture; public final class Token { public String id = \"x\"; }");
            sources.put("fixture/Codecs.java",
                    "package fixture; import org.sjf4j.annotation.node.*;"
                    + "import org.sjf4j.value.ValueCodec; import java.util.*;"
                    + "@NodeRegistry public interface Codecs {"
                    + " @NodeValue ValueCodec<Token, Map> TOKEN = "
                    + "new ValueCodec.SimpleValueCodec<>(Token.class, Map.class,"
                    + "v -> { Map<String,Object> data = new LinkedHashMap<>();"
                    + "data.put(\"present\", v.id); data.put(\"missing\", null);"
                    + "data.put(\"items\", Arrays.asList(null, \"last\")); return data; },"
                    + "raw -> new Token());"
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + format
                    + ",registries={Codecs.class},includeNulls=false)"
                    + "public interface Binder {"
                    + " @Write String write(Token token);"
                    + "}");
            sources.put("fixture/IncludeBinder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + format + ",registries={Codecs.class})"
                    + "public interface IncludeBinder extends Binder {}");
            NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertFalse(generated.contains(".writeRaw(rawValue, this.config)"), backend);
            assertFalse(generated.contains("writeConfigOmitNulls"), backend);
            assertFalse(generated.contains("writeConfigIncludeNulls"), backend);
            assertTrue(generated.contains("NodeConfig.OMIT_NULLS"), backend);
            assertTrue(compiled.generatedSource("fixture/IncludeBinder_Impl.java").contains("NodeConfig.DEFAULT"), backend);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                org.sjf4j.value.ValueRegistry.registerAnnotated(loader.loadClass("fixture.Codecs"));
                Class binderType = loader.loadClass("fixture.Binder");
                Class<?> tokenType = loader.loadClass("fixture.Token");
                Object token = tokenType.getConstructor().newInstance();
                Object binder = CompiledTestBinders.create(binderType, backend, new NodeConfig(true));
                Map<String, Object> omitted = decode(backend, (String) binderType
                        .getMethod("write", tokenType).invoke(binder, token));
                Map<String, Object> included = decode(backend, (String) binderType
                        .getMethod("write", tokenType).invoke(CompiledTestBinders.create(
                                loader.loadClass("fixture.IncludeBinder"), backend, NodeConfig.OMIT_NULLS), token));
                assertFalse(omitted.containsKey("missing"), backend);
                assertTrue(included.containsKey("missing"), backend);
                assertEquals("x", omitted.get("present"), backend);
                assertEquals(Arrays.asList(null, "last"), omitted.get("items"), backend);
                assertEquals(omitted.get("items"), included.get("items"), backend);
            }
        }
    }

}
