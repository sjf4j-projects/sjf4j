package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.value.ValueRegistry;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Contracts shared by runtime and compiled ValueCodec resolution. */
class CompiledValueCodecReviewTest {

    @Test
    void rejectsUnknownDiscoveredCodecFormatAtCompileTime() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java",
                "package fixture; import org.sjf4j.annotation.node.NodeProperty; "
                + "import java.time.Instant; public class Bean { "
                + "@NodeProperty(codecName=\"misspelled\") "
                + "public Instant when; }",
                "fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import java.io.IOException; "
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + "@ReadFrom Bean read(String json) throws IOException; }"
        ), CodegenProcessor.class);

        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("Unknown @NodeProperty codecName 'misspelled'"),
                result.diagnostics());
    }

    @Test
    void codecRegisteredForParentAlsoCompilesForSubclassWrites() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Parent.java",
                "package fixture; public class Parent { "
                + " public String text; public Parent(String text) { this.text=text; } }");
        sources.put("fixture/Child.java",
                "package fixture; public class Child extends Parent { "
                + " public Child(String text) { super(text); } }");
        sources.put("fixture/Codecs.java",
                "package fixture; import org.sjf4j.value.ValueCodec; "
                + "import org.sjf4j.annotation.node.NodeValue; "
                + "public class Codecs { "
                + "@NodeValue public static final ValueCodec<Parent,String> CODEC = "
                + "new ValueCodec.SimpleValueCodec<>(Parent.class, String.class, "
                + "x -> x.text, Parent::new); }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import java.io.IOException; "
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + "@WriteTo String write(Child value) throws IOException; }");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String source = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains("requireRegisteredValueInfo"), source);
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            ValueRegistry.registerAnnotated(loader.loadClass("fixture.Codecs"));
            Class<?> child = loader.loadClass("fixture.Child");
            Class<?> binder = loader.loadClass("fixture.Binder_Impl");
            Object object = child.getConstructor(String.class).newInstance("inherited");
            assertEquals("\"inherited\"", binder.getMethod("write", child)
                    .invoke(binder.getConstructor().newInstance(), object));
        }
    }

    @Test
    void externalTreeTakesPrecedenceOverAnAnnotatedCodecDeclaration() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Codecs.java",
                "package fixture; "
                + "import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "import com.google.gson.JsonElement; import com.google.gson.JsonNull; "
                + "public class Codecs { "
                + "@NodeValue public static final ValueCodec<JsonElement,String> CODEC = "
                + "new ValueCodec.SimpleValueCodec<>(JsonElement.class, "
                + "String.class, Object::toString, value -> JsonNull.INSTANCE); }",
                "fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import java.io.IOException; "
                + "import com.google.gson.JsonElement; "
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + "@WriteTo String write(JsonElement value) "
                + "throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String source = result.generatedSource("fixture/Binder_Impl.java");
        assertFalse(source.contains("requireRegisteredValueInfo"), source);
    }
}
