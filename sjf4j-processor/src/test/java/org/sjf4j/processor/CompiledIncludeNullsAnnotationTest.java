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
 * Runtime fallback. Injected NodeConfig is ignored by compiled binders.
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
                    + " @Write(includeNulls=true) String inheritedInclude(Object value);"
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + format + ",includeNulls=false)"
                    + "public interface Binder extends Base {"
                    + " @Write String write(Model value);"
                    + " @Write(includeNulls=true) String writeInclude(Model value);"
                    + " @Write byte[] writeBytes(Model value);"
                    + " @Write(includeNulls=true) byte[] writeBytesInclude(Model value);"
                    + " @Write String writeAny(Object value);"
                    + " @Write(includeNulls=true) String writeAnyInclude(Object value);"
                    + "}");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertFalse(generated.contains("this.config.includeNulls"), backend);
            assertFalse(generated.contains("withIncludeNulls("), backend);
            assertFalse(generated.contains("this.config ="), backend);
            assertTrue(generated.contains("private static final org.sjf4j.NodeConfig config = "), backend);
            assertTrue(generated.contains("private static final org.sjf4j.NodeConfig writeConfigIncludeNulls"), backend);
            assertTrue(generated.contains("private static final org.sjf4j.NodeConfig writeConfigOmitNulls"), backend);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class binderType = loader.loadClass("fixture.Binder");
                Class<?> implType = loader.loadClass("fixture.Binder_Impl");
                java.lang.reflect.Field configField = implType.getDeclaredField("config");
                assertTrue(java.lang.reflect.Modifier.isStatic(configField.getModifiers()), backend);
                assertTrue(java.lang.reflect.Modifier.isFinal(configField.getModifiers()), backend);
                assertNotNull(implType.getConstructor(NodeConfig.class), backend);
                Class modelType = loader.loadClass("fixture.Model");
                Object model = modelType.getConstructor().newInstance();
                Object injectedTrue = CompiledInstances.create(binderType, new NodeConfig(true));
                Object injectedFalse = CompiledInstances.create(binderType, new NodeConfig(false));

                for (Object binder : Arrays.asList(injectedTrue, injectedFalse)) {
                    Map<String, Object> omitted = decode(backend, (String) binderType
                            .getMethod("write", modelType).invoke(binder, model));
                    Map<String, Object> included = decode(backend, (String) binderType
                            .getMethod("writeInclude", modelType).invoke(binder, model));
                    checkModel(omitted, false);
                    checkModel(included, true);

                    byte[] omittedBytes = (byte[]) binderType.getMethod("writeBytes", modelType)
                            .invoke(binder, model);
                    byte[] includedBytes = (byte[]) binderType.getMethod("writeBytesInclude", modelType)
                            .invoke(binder, model);
                    assertEquals(omitted, decode(backend, new String(omittedBytes, StandardCharsets.UTF_8)), backend);
                    assertEquals(included, decode(backend, new String(includedBytes, StandardCharsets.UTF_8)), backend);

                    Map<String, Object> dynamic = new LinkedHashMap<>();
                    dynamic.put("missing", null);
                    dynamic.put("present", Arrays.asList("a", null, "b"));
                    for (String prefix : new String[]{"writeAny", "inherited"}) {
                        Map<String, Object> omitDynamic = decode(backend, (String) binderType
                                .getMethod(prefix, Object.class).invoke(binder, dynamic));
                        Map<String, Object> includeDynamic = decode(backend, (String) binderType
                                .getMethod(prefix.equals("writeAny") ? "writeAnyInclude" : "inheritedInclude",
                                        Object.class).invoke(binder, dynamic));
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
    void valueCodecRawMapFallbackInheritsPerMethodNullPolicy() throws Exception {
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
                    + " @Write String omit(Token token);"
                    + " @Write(includeNulls=true) String include(Token token);"
                    + "}");
            NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertFalse(generated.contains(".writeRaw(rawValue, this.config)"), backend);
            assertTrue(generated.contains("writeConfigOmitNulls"), backend);
            assertTrue(generated.contains("writeConfigIncludeNulls"), backend);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                org.sjf4j.value.ValueRegistry.registerAnnotated(loader.loadClass("fixture.Codecs"));
                Class binderType = loader.loadClass("fixture.Binder");
                Class<?> tokenType = loader.loadClass("fixture.Token");
                Object token = tokenType.getConstructor().newInstance();
                Object binder = CompiledInstances.create(binderType, new NodeConfig(true));
                Map<String, Object> omitted = decode(backend, (String) binderType
                        .getMethod("omit", tokenType).invoke(binder, token));
                Map<String, Object> included = decode(backend, (String) binderType
                        .getMethod("include", tokenType).invoke(binder, token));
                assertFalse(omitted.containsKey("missing"), backend);
                assertTrue(included.containsKey("missing"), backend);
                assertEquals("x", omitted.get("present"), backend);
                assertEquals(Arrays.asList(null, "last"), omitted.get("items"), backend);
                assertEquals(omitted.get("items"), included.get("items"), backend);
            }
        }
    }

}
