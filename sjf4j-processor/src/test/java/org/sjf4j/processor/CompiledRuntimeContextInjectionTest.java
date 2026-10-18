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
        sources.put("fixture/Source.java",
                "package fixture; public class Source { public Token token; }");
        sources.put("fixture/Target.java",
                "package fixture; public class Target { public String token; }");
        sources.put("fixture/Outer.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper(importing={Mapper.class}) public interface Outer { "
                + "Target map(Source source); }");
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
        String outerSource = compiled.generatedSource("fixture/Outer_Impl.java");
        assertTrue(outerSource.contains("CompiledInstances.create("), outerSource);
        assertFalse(outerSource.contains("CompiledInstances.global("), outerSource);
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
            assertSame(defaultBinder, CompiledInstances.global(binderInterface));

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

            Class outerInterface = loader.loadClass("fixture.Outer");
            Class<?> sourceClass = loader.loadClass("fixture.Source");
            Class<?> targetClass = loader.loadClass("fixture.Target");
            Object source = sourceClass.getConstructor().newInstance();
            sourceClass.getField("token").set(source, token);
            Object defaultOuter = CompiledInstances.global(outerInterface);
            Object alternateOuter = CompiledInstances.create(outerInterface, context);
            Object defaultTarget = outerInterface.getMethod("map", sourceClass)
                    .invoke(defaultOuter, source);
            Object alternateTarget = outerInterface.getMethod("map", sourceClass)
                    .invoke(alternateOuter, source);
            assertEquals("d:hello", targetClass.getField("token").get(defaultTarget));
            assertEquals("a:hello", targetClass.getField("token").get(alternateTarget));

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
    @SuppressWarnings({"rawtypes", "unchecked"})
    void allJsonBackendsUseInjectedDefaultCodecFormatAndNullPolicy() throws Exception {
        String[] backends = {"SIMPLE", "JACKSON2", "JACKSON3", "GSON", "FASTJSON2", "JSONP"};
        for (String backend : backends) {
            NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                    "fixture/Bean.java",
                    "package fixture; import java.time.Instant; "
                    + "public class Bean { public Instant when; public String empty; "
                    + "public Address address; }",
                    "fixture/Address.java",
                    "package fixture; public class Address { "
                    + "public String street = \"Main\"; public int zip = 12345; }",
                    "fixture/Binder.java",
                    "package fixture; import java.io.IOException; "
                    + "import org.sjf4j.annotation.binding.*; "
                    + "@CompiledBinder(backend=Backend." + backend + ") "
                    + "public interface Binder { "
                    + "@ReadFrom Bean read(String json) throws IOException; "
                    + "@WriteTo String write(Bean bean) throws IOException; }"
            ), CodegenProcessor.class);
            if (!compiled.success) System.err.println(backend + ": " + compiled.diagnostics());
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class type = loader.loadClass("fixture.Binder");
                Class<?> beanType = loader.loadClass("fixture.Bean");
                Object bean = beanType.getConstructor().newInstance();
                beanType.getField("when").set(bean, java.time.Instant.parse("2024-01-01T10:00:00Z"));
                beanType.getField("address").set(bean,
                        loader.loadClass("fixture.Address").getConstructor().newInstance());

                RuntimeContext context = new RuntimeContext(
                        Map.of(java.time.Instant.class, "epochMillis"), false);
                Object defaults = CompiledInstances.global(type);
                Object configured = CompiledInstances.create(type, context);
                String standard = (String) type.getMethod("write", beanType).invoke(defaults, bean);
                String custom = (String) type.getMethod("write", beanType).invoke(configured, bean);
                assertTrue(standard.contains("2024-01-01T10:00:00Z"), backend + ": " + standard);
                assertTrue(standard.contains("\"street\":\"Main\""), backend + ": " + standard);
                assertTrue(standard.contains("\"empty\":null"), backend + ": " + standard);
                assertTrue(custom.contains("1704103200000"), backend + ": " + custom);
                assertTrue(custom.contains("\"street\":\"Main\""), backend + ": " + custom);
                assertFalse(custom.contains("\"empty\""), backend + ": " + custom);

                Object decoded = type.getMethod("read", String.class).invoke(configured, custom);
                assertEquals(java.time.Instant.parse("2024-01-01T10:00:00Z"),
                        beanType.getField("when").get(decoded), backend);
            }
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void navigatorAndJdbcMapperSupportConfiguredInstances() throws Exception {
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                "fixture/Paths.java",
                "package fixture; import java.util.Map; "
                + "import org.sjf4j.annotation.path.*; "
                + "@CompiledNavigator public interface Paths { "
                + "@GetByPath(\"$.name\") String getName(Map<String, String> source); }",
                "fixture/Row.java",
                "package fixture; public class Row { public String name; }",
                "fixture/Rows.java",
                "package fixture; import java.sql.ResultSet; "
                + "import org.sjf4j.annotation.mapping.Mapping; "
                + "import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper; "
                + "@CompiledJdbcMapper public interface Rows { "
                + "@Mapping(target=\"name\", source=\"name\") "
                + "Row read(ResultSet rs); }"
        ), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        assertTrue(compiled.generatedSource("fixture/Paths_Impl.java")
                .contains("RuntimeContext context)"));
        assertTrue(compiled.generatedSource("fixture/Rows_Impl.java")
                .contains("RuntimeContext context)"));

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class pathsType = loader.loadClass("fixture.Paths");
            Class rowsType = loader.loadClass("fixture.Rows");
            RuntimeContext context = new RuntimeContext(Map.of(), false);

            Object navigator = CompiledInstances.create(pathsType, context);
            assertNotSame(navigator, CompiledInstances.global(pathsType));
            assertEquals("Alice", pathsType.getMethod("getName", Map.class)
                    .invoke(navigator, Map.of("name", "Alice")));

            Object jdbcMapper = CompiledInstances.create(rowsType, context);
            assertNotSame(jdbcMapper, CompiledInstances.global(rowsType));
            java.util.concurrent.atomic.AtomicBoolean advanced =
                    new java.util.concurrent.atomic.AtomicBoolean();
            java.sql.ResultSet resultSet = (java.sql.ResultSet)
                    java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.ResultSet.class},
                            (proxy, method, args) -> {
                                if ("next".equals(method.getName())) {
                                    return advanced.compareAndSet(false, true);
                                }
                                if ("getString".equals(method.getName())
                                        || "getObject".equals(method.getName())) return "Alice";
                                if ("wasNull".equals(method.getName())) return false;
                                throw new UnsupportedOperationException(method.getName());
                            });
            Object row = rowsType.getMethod("read", java.sql.ResultSet.class)
                    .invoke(jdbcMapper, resultSet);
            assertEquals("Alice", loader.loadClass("fixture.Row").getField("name").get(row));
        }
    }

    @Test
    void createRejectsNullConfiguration() {
        assertThrows(NullPointerException.class,
                () -> CompiledInstances.create(Runnable.class, null));
    }
}
