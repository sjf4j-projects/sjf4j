package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;
import org.sjf4j.value.ValueRegistry;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies per-instance runtime configuration in generated mapper and binder implementations. */
class CompiledRuntimeContextInjectionTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void globalAndCreateKeepFormatsAndInstancesIsolated() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Token.java",
                "package fixture; public final class Token { "
                + "public final String value; public Token(String value) { this.value = value; } }");
        sources.put("fixture/Codecs.java",
                "package fixture; import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public final class Codecs { "
                + "@NodeValue public static final ValueCodec<Token,String> DEFAULT = "
                + "new ValueCodec.SimpleValueCodec<>(Token.class, String.class, "
                + "v -> \"d:\" + v.value, s -> new Token(s.substring(2))); "
                + "@NodeValue(formats=\"alternate\") public static final ValueCodec<Token,String> ALTERNATE = "
                + "new ValueCodec.SimpleValueCodec<>(Token.class, String.class, "
                + "v -> \"a:\" + v.value, s -> new Token(s.substring(2))); }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { "
                + "Token decode(String source); String encode(Token source); }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import java.io.IOException; "
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + "@ReadFrom Token read(String json) throws IOException; "
                + "@WriteTo String write(Token token) throws IOException; "
                + "@WriteTo String writeBean(Bean bean) throws IOException; }");
        sources.put("fixture/Bean.java",
                "package fixture; public class Bean { public String name = \"x\"; public String empty; }");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String mapperSource = compiled.generatedSource("fixture/Mapper_Impl.java");
        String binderSource = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(mapperSource.contains("RuntimeContext context)"), mapperSource);
        assertTrue(binderSource.contains("RuntimeContext context)"), binderSource);
        assertTrue(binderSource.contains("context.defaultValueFormat("), binderSource);
        assertFalse(mapperSource.contains("org.sjf4j.Nodes."), mapperSource);
        assertFalse(binderSource.contains("org.sjf4j.Nodes."), binderSource);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> tokenClass = loader.loadClass("fixture.Token");
            ValueRegistry.registerAnnotated(loader.loadClass("fixture.Codecs"));

            Class mapperInterface = loader.loadClass("fixture.Mapper");
            Class binderInterface = loader.loadClass("fixture.Binder");
            Object defaultMapper = CompiledInstances.global(mapperInterface);
            Object defaultBinder = CompiledInstances.global(binderInterface);
            assertSame(defaultMapper, CompiledInstances.global(mapperInterface));
            assertSame(defaultBinder, CompiledInstances.of(binderInterface));

            RuntimeContext context = new RuntimeContext(Map.of(tokenClass, "alternate"), false);
            Object configuredMapper = CompiledInstances.create(mapperInterface, context);
            Object configuredBinder = CompiledInstances.create(binderInterface, context);
            assertNotSame(defaultMapper, configuredMapper);
            assertNotSame(defaultBinder, configuredBinder);
            assertNotSame(configuredMapper, CompiledInstances.create(mapperInterface, context));

            Object token = tokenClass.getConstructor(String.class).newInstance("hello");
            assertEquals("d:hello", mapperInterface.getMethod("encode", tokenClass)
                    .invoke(defaultMapper, token));
            assertEquals("a:hello", mapperInterface.getMethod("encode", tokenClass)
                    .invoke(configuredMapper, token));

            assertEquals("hello", tokenClass.getField("value").get(
                    mapperInterface.getMethod("decode", String.class).invoke(defaultMapper, "d:hello")));
            assertEquals("hello", tokenClass.getField("value").get(
                    mapperInterface.getMethod("decode", String.class).invoke(configuredMapper, "a:hello")));

            assertEquals("\"d:hello\"", binderInterface.getMethod("write", tokenClass)
                    .invoke(defaultBinder, token));
            assertEquals("\"a:hello\"", binderInterface.getMethod("write", tokenClass)
                    .invoke(configuredBinder, token));
            assertEquals("hello", tokenClass.getField("value").get(
                    binderInterface.getMethod("read", String.class).invoke(configuredBinder, "\"a:hello\"")));

            Class<?> beanClass = loader.loadClass("fixture.Bean");
            Object bean = beanClass.getConstructor().newInstance();
            String defaultJson = (String) binderInterface.getMethod("writeBean", beanClass)
                    .invoke(defaultBinder, bean);
            String configuredJson = (String) binderInterface.getMethod("writeBean", beanClass)
                    .invoke(configuredBinder, bean);
            assertTrue(defaultJson.contains("\"empty\":null"), defaultJson);
            assertFalse(configuredJson.contains("\"empty\""), configuredJson);
            assertTrue(configuredJson.contains("\"name\":\"x\""), configuredJson);
        }
    }

    @Test
    void createRejectsNullConfiguration() {
        assertThrows(NullPointerException.class,
                () -> CompiledInstances.create(Runnable.class, null));
    }
}
