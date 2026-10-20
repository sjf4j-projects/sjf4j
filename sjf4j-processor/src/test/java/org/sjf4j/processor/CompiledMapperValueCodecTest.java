package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.value.ValueRegistry;

import java.net.URLClassLoader;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime/compiled mapping contracts for registered and annotated logical values. */
class CompiledMapperValueCodecTest {

    private static final String UUID_TEXT = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void builtinCodecsMapPropertiesContainersAndRootValues() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Source.java",
                "package fixture; import java.util.List; "
                + "public class Source { public String id; public String when; "
                + "public List<String> ids; }");
        sources.put("fixture/Target.java",
                "package fixture; import java.util.List; import java.util.UUID; "
                + "import java.time.Instant; public class Target { "
                + "public UUID id; public Instant when; public List<UUID> ids; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "import java.util.UUID; "
                + "@CompiledMapper public interface Mapper { "
                + "Target map(Source source); Source reverse(Target source); "
                + "UUID decode(String raw); String encode(UUID value); }");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(generated.contains("NodeMapper.to("), generated);
        assertFalse(generated.contains("org.sjf4j.Nodes."), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> sourceClass = loader.loadClass("fixture.Source");
            Class<?> targetClass = loader.loadClass("fixture.Target");
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperClass.getConstructor().newInstance();
            Object source = sourceClass.getConstructor().newInstance();
            sourceClass.getField("id").set(source, UUID_TEXT);
            sourceClass.getField("when").set(source, "2024-01-01T10:00:00Z");
            sourceClass.getField("ids").set(source, List.of(UUID_TEXT));

            Object target = mapperClass.getMethod("map", sourceClass).invoke(mapper, source);
            assertEquals(UUID.fromString(UUID_TEXT), targetClass.getField("id").get(target));
            assertEquals(Instant.parse("2024-01-01T10:00:00Z"),
                    targetClass.getField("when").get(target));
            assertEquals(List.of(UUID.fromString(UUID_TEXT)),
                    targetClass.getField("ids").get(target));

            Object reversed = mapperClass.getMethod("reverse", targetClass).invoke(mapper, target);
            assertEquals(UUID_TEXT, sourceClass.getField("id").get(reversed));
            assertEquals("2024-01-01T10:00:00Z", sourceClass.getField("when").get(reversed));
            assertEquals(List.of(UUID_TEXT), sourceClass.getField("ids").get(reversed));

            assertEquals(UUID.fromString(UUID_TEXT),
                    mapperClass.getMethod("decode", String.class).invoke(mapper, UUID_TEXT));
            assertEquals(UUID_TEXT, mapperClass.getMethod("encode", UUID.class)
                    .invoke(mapper, UUID.fromString(UUID_TEXT)));
        }
    }

    @Test
    void classpathRegistriesAreExplicitAndScopedPerMapperInterface() throws Exception {
        String model = "package fixture; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "public class Target { public ClasspathRegistries.Money money; }";
        String source = "package fixture; "
                + "public class Source { public String money; }";
        String mapper = "package fixture; "
                + "import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "import org.sjf4j.annotation.binding.BindingOptions; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "%s @CompiledMapper public interface Mapper { "
                + "Target map(Source source); Source reverse(Target source); }";

        Map<String, String> fixture = new LinkedHashMap<>();
        fixture.put("fixture/Target.java", model);
        fixture.put("fixture/Source.java", source);
        fixture.put("fixture/Mapper.java", String.format(mapper, ""));
        NavigatorTestCompiler.Result missing =
                NavigatorTestCompiler.compile(fixture, CodegenProcessor.class);
        assertFalse(missing.success, "Classpath codecs must require an explicit registry");

        fixture.put("fixture/Mapper.java", String.format(mapper,
                "@BindingOptions(registries={ClasspathRegistries.Third.class})"));
        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(fixture, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String generated = compiled.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(generated.contains("NodeMapper.to("), generated);
        assertFalse(generated.contains("org.sjf4j.Nodes."), generated);

        // Registry visibility is a compile-time contract. Do not register the
        // shared ClasspathRegistries.Third globally here: other test fixtures
        // explicitly verify that registration is performed once at startup.
    }

    @Test
    void sameSourceRegistryIsDiscoveredWithoutExplicitOptions() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Token.java",
                "package fixture; public final class Token { "
                + "public final String text; public Token(String text) { this.text = text; } }");
        sources.put("fixture/Codecs.java",
                "package fixture; import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public class Codecs { @NodeValue "
                + "public static final ValueCodec<Token, String> TOKEN = "
                + "new ValueCodec.SimpleValueCodec<>(Token.class, String.class, "
                + "v -> v.text, Token::new); }");
        sources.put("fixture/Source.java",
                "package fixture; public class Source { public String token; }");
        sources.put("fixture/Target.java",
                "package fixture; public class Target { public Token token; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { Target map(Source source); }");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            ValueRegistry.registerAnnotated(loader.loadClass("fixture.Codecs"));
            Class<?> sourceType = loader.loadClass("fixture.Source");
            Class<?> targetType = loader.loadClass("fixture.Target");
            Class<?> tokenType = loader.loadClass("fixture.Token");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object source = sourceType.getConstructor().newInstance();
            sourceType.getField("token").set(source, "local");
            Object target = mapperType.getMethod("map", sourceType)
                    .invoke(mapperType.getConstructor().newInstance(), source);
            Object token = targetType.getField("token").get(target);
            assertEquals("local", tokenType.getField("text").get(token));
        }
    }

    @Test
    void annotatedMapAndListValuesRemainLogicalLeaves() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/MapValue.java",
                "package fixture; import java.util.Map; "
                + "import org.sjf4j.annotation.node.*; "
                + "@NodeValue public final class MapValue { "
                + "public final Map<String,Object> raw; "
                + "@RawToValue public MapValue(Map<String,Object> raw) { this.raw = raw; } "
                + "@ValueToRaw public Map<String,Object> raw() { return raw; } }");
        sources.put("fixture/ListValue.java",
                "package fixture; import java.util.List; "
                + "import org.sjf4j.annotation.node.*; "
                + "@NodeValue public final class ListValue { "
                + "public final List<Object> raw; "
                + "@RawToValue public ListValue(List<Object> raw) { this.raw = raw; } "
                + "@ValueToRaw public List<Object> raw() { return raw; } }");
        sources.put("fixture/Source.java",
                "package fixture; import java.util.*; "
                + "public class Source { public Map<String,Object> object; "
                + "public List<Object> array; }");
        sources.put("fixture/Target.java",
                "package fixture; public class Target { "
                + "public MapValue object; public ListValue array; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { "
                + "Target map(Source source); Source reverse(Target source); "
                + "java.util.Map<String,Object> encodeMap(MapValue value); "
                + "java.util.List<Object> encodeList(ListValue value); "
                + "MapValue decodeMap(java.util.Map<String,Object> raw); "
                + "ListValue decodeList(java.util.List<Object> raw); }");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        assertTrue(compiled.generatedSource("fixture/Mapper_Impl.java")
                .contains("NodeMapper.convertToRaw("));
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> sourceType = loader.loadClass("fixture.Source");
            Class<?> targetType = loader.loadClass("fixture.Target");
            Class<?> mapValueType = loader.loadClass("fixture.MapValue");
            Class<?> listValueType = loader.loadClass("fixture.ListValue");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object instance = mapperType.getConstructor().newInstance();
            Object source = sourceType.getConstructor().newInstance();
            Map<String, Object> rawMap = Map.of("nested", List.of(1, 2));
            List<Object> rawList = List.of("x", Map.of("y", 3));
            sourceType.getField("object").set(source, rawMap);
            sourceType.getField("array").set(source, rawList);

            Object target = mapperType.getMethod("map", sourceType).invoke(instance, source);
            assertEquals(rawMap, mapValueType.getField("raw")
                    .get(targetType.getField("object").get(target)));
            assertEquals(rawList, listValueType.getField("raw")
                    .get(targetType.getField("array").get(target)));

            Object reverse = mapperType.getMethod("reverse", targetType).invoke(instance, target);
            assertEquals(rawMap, sourceType.getField("object").get(reverse));
            assertEquals(rawList, sourceType.getField("array").get(reverse));

            assertEquals(rawMap, mapperType.getMethod("encodeMap", mapValueType)
                    .invoke(instance, targetType.getField("object").get(target)));
            assertEquals(rawList, mapperType.getMethod("encodeList", listValueType)
                    .invoke(instance, targetType.getField("array").get(target)));
            Object decodedMap = mapperType.getMethod("decodeMap", Map.class)
                    .invoke(instance, rawMap);
            Object decodedList = mapperType.getMethod("decodeList", List.class)
                    .invoke(instance, rawList);
            assertEquals(rawMap, mapValueType.getField("raw").get(decodedMap));
            assertEquals(rawList, listValueType.getField("raw").get(decodedList));
        }
    }

    @Test
    void codecRootUpdateIsRejectedInsteadOfBeingSilent() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Mapper.java",
                "package fixture; import java.util.UUID; "
                + "import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { "
                + "void update(UUID target, String raw); }"
        ), CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("root ValueCodec update is not supported"),
                result.diagnostics());
    }

    @Test
    void rejectsIncompatibleCodecRawTypesAtCompilation() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/LongValue.java",
                "package fixture; import org.sjf4j.annotation.node.*; "
                + "@NodeValue public final class LongValue { "
                + "private final Long raw; "
                + "@RawToValue public LongValue(Long raw) { this.raw = raw; } "
                + "@ValueToRaw public Long raw() { return raw; } }");
        sources.put("fixture/Source.java",
                "package fixture; import java.util.UUID; "
                + "public class Source { public UUID id; }");
        sources.put("fixture/Target.java",
                "package fixture; public class Target { public LongValue id; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { "
                + "Target map(Source source); }");

        NavigatorTestCompiler.Result mismatch =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(mismatch.success, mismatch.diagnostics());
        assertTrue(mismatch.diagnostics().contains("incompatible ValueCodec raw types"),
                mismatch.diagnostics());
        assertTrue(mismatch.diagnostics().contains("java.lang.String cannot be converted to java.lang.Long"),
                mismatch.diagnostics());

        sources.put("fixture/Source.java",
                "package fixture; public class Source { public String id; }");
        NavigatorTestCompiler.Result stringToLong =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(stringToLong.success, stringToLong.diagnostics());
        assertTrue(stringToLong.diagnostics().contains("incompatible ValueCodec raw types"),
                stringToLong.diagnostics());

        sources.put("fixture/Target.java",
                "package fixture; public class Target { public Integer id; }");
        sources.put("fixture/Source.java",
                "package fixture; import java.util.UUID; "
                + "public class Source { public UUID id; }");
        NavigatorTestCompiler.Result codecToInteger =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(codecToInteger.success, codecToInteger.diagnostics());
        assertTrue(codecToInteger.diagnostics().contains("incompatible ValueCodec raw types"),
                codecToInteger.diagnostics());
    }

    @Test
    void inheritedCodecDoesNotOverrideNativeScalarSemantics() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Codecs.java",
                "package fixture; import java.io.Serializable; "
                + "import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public class Codecs { @NodeValue "
                + "public static final ValueCodec<Serializable,String> CODEC = "
                + "new ValueCodec.SimpleValueCodec<>(Serializable.class, String.class, "
                + "Object::toString, raw -> raw); }");
        sources.put("fixture/Source.java",
                "package fixture; public class Source { public String id; }");
        sources.put("fixture/Target.java",
                "package fixture; public class Target { public Integer id; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { Target map(Source source); }");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(result.success,
                "String -> Integer must stay strict even if Serializable has a codec");
        assertTrue(result.diagnostics().contains(
                "cannot map value from java.lang.String to java.lang.Integer"),
                result.diagnostics());
    }

    @Test
    void userMapperMethodsTakePrecedenceOverDiscoveredCodecs() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Source.java",
                "package fixture; public class Source { public String id; }");
        sources.put("fixture/Target.java",
                "package fixture; import java.util.UUID; "
                + "public class Target { public UUID id; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import java.util.UUID; "
                + "import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { "
                + "default UUID custom(String text) { "
                + "return UUID.fromString(\"00000000-0000-0000-0000-000000000007\"); } "
                + "Target map(Source source); }");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> sourceType = loader.loadClass("fixture.Source");
            Class<?> targetType = loader.loadClass("fixture.Target");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object source = sourceType.getConstructor().newInstance();
            sourceType.getField("id").set(source, UUID_TEXT);
            Object target = mapperType.getMethod("map", sourceType)
                    .invoke(mapperType.getConstructor().newInstance(), source);
            assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000007"),
                    targetType.getField("id").get(target));
        }
    }

    @Test
    void namedNumericCodecIsAcceptedAlongsideStringDefault() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Token.java",
                "package fixture; public final class Token { "
                + "public final String text; public Token(String text) { this.text = text; } }");
        sources.put("fixture/Codecs.java",
                "package fixture; import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public final class Codecs { "
                + "@NodeValue public static final ValueCodec<Token,String> TEXT = "
                + "new ValueCodec.SimpleValueCodec<>(Token.class, String.class, "
                + "v -> v.text, raw -> new Token(\"text:\" + raw)); "
                + "@NodeValue(format=\"number\") public static final ValueCodec<Token,Long> NUMBER = "
                + "new ValueCodec.SimpleValueCodec<>(Token.class, Long.class, "
                + "v -> 7L, raw -> new Token(\"number:\" + raw)); }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "import org.sjf4j.annotation.binding.BindingOptions; "
                + "@BindingOptions(registries={Codecs.class}) "
                + "@CompiledMapper public interface Mapper { "
                + "Token fromNumber(Long value); Token fromText(String value); }");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            ValueRegistry.registerAnnotated(loader.loadClass("fixture.Codecs"));
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperType.getConstructor().newInstance();
            Object fromNumber = mapperType.getMethod("fromNumber", Long.class).invoke(mapper, 7L);
            Object fromText = mapperType.getMethod("fromText", String.class).invoke(mapper, "abc");
            assertEquals("number:7", fromNumber.getClass().getField("text").get(fromNumber));
            assertEquals("text:abc", fromText.getClass().getField("text").get(fromText));
        }

        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "import org.sjf4j.annotation.binding.BindingOptions; "
                + "@BindingOptions(registries={Codecs.class}) "
                + "@CompiledMapper public interface Mapper { Token fromBoolean(Boolean value); }");
        NavigatorTestCompiler.Result incompatible =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(incompatible.success, incompatible.diagnostics());
        assertTrue(incompatible.diagnostics().contains("incompatible ValueCodec raw types"),
                incompatible.diagnostics());
    }
}
