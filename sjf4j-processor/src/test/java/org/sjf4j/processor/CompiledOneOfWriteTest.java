package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Polymorphic writing dispatches by exact Java class inside compiled containers. */
class CompiledOneOfWriteTest {

    @Test
    void writesCompiledSubtypesAndFallsBackOnlyForInaccessibleFields() throws Exception {
        for (String backend : new String[]{
                "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"
        }) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Animal.java", "package fixture;\n"
                    + "import org.sjf4j.annotation.node.*;\n"
                    + "@OneOf(key=\"kind\", mappings={"
                    + "@OneOfMapping(type=Cat.class,when=\"cat\"),"
                    + "@OneOfMapping(type=Dog.class,when=\"dog\"),"
                    + "@OneOfMapping(type=Hidden.class,when=\"hidden\")})\n"
                    + "public interface Animal {}\n");
            sources.put("fixture/Cat.java", "package fixture;\n"
                    + "public class Cat implements Animal { public String kind=\"cat\"; public int lives=9; }\n");
            sources.put("fixture/Dog.java", "package fixture;\n"
                    + "public class Dog implements Animal { public String kind=\"dog\"; public boolean barks=true; }\n");
            sources.put("fixture/Hidden.java", "package fixture;\n"
                    + "import org.sjf4j.annotation.node.NodeProperty;\n"
                    + "public class Hidden implements Animal {\n"
                    + " @NodeProperty(\"kind\") private final String discriminator=\"hidden\";\n"
                    + " public String name=\"secret\";\n"
                    + "}\n");
            sources.put("fixture/Envelope.java", "package fixture;\n"
                    + "import java.util.*;\n"
                    + "import org.sjf4j.annotation.node.*;\n"
                    + "public class Envelope {\n"
                    + " public String title=\"test\";\n"
                    + " public List<Animal> animals;\n"
                    + " public Map<String,Animal> named;\n"
                    + " @OneOf(key=\"kind\",mappings={"
                    + "@OneOfMapping(type=Cat.class,when=\"cat\"),"
                    + "@OneOfMapping(type=Dog.class,when=\"dog\")})\n"
                    + " public Animal direct;\n"
                    + " @OneOf(mappings={@OneOfMapping(type=String.class),"
                    + "@OneOfMapping(type=Integer.class)}) public Object shape;\n"
                    + "}\n");
            sources.put("fixture/Binder.java", "package fixture;\n"
                    + "import org.sjf4j.annotation.binding.*;\n"
                    + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder {\n"
                    + " @WriteTo String write(Envelope value) throws java.io.IOException;\n"
                    + " @WriteTo byte[] writeBytes(Envelope value) throws java.io.IOException;\n"
                    + " @WriteTo void writeStream(Envelope value, java.io.OutputStream out) throws java.io.IOException;\n"
                    + " @WriteTo String writeAnimal(Animal value) throws java.io.IOException;\n"
                    + "}\n");

            NavigatorTestCompiler.Result result =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());
            String generated = result.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("Class<?> actualClass = value.getClass();"),
                    backend + ": missing cached runtime class");
            assertTrue(generated.contains("actualClass == Cat.class")
                            || generated.contains("actualClass == fixture.Cat.class"),
                    backend + ": missing Class dispatch");
            assertTrue(generated.contains("_writeEnvelope("), backend);
            assertTrue(generated.contains("_writeCat"), backend);
            assertTrue(generated.contains("requireRegisteredPojoInfo("),
                    backend + ": inaccessible subtype should use cached POJO metadata");

            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> envelope = loader.loadClass("fixture.Envelope");
                Class<?> animal = loader.loadClass("fixture.Animal");
                Object root = envelope.getConstructor().newInstance();
                Object cat = loader.loadClass("fixture.Cat").getConstructor().newInstance();
                Object dog = loader.loadClass("fixture.Dog").getConstructor().newInstance();
                Object hidden = loader.loadClass("fixture.Hidden").getConstructor().newInstance();
                envelope.getField("animals").set(root, List.of(cat, dog, hidden));
                Map<String, Object> named = new LinkedHashMap<>();
                named.put("cat", cat);
                named.put("hidden", hidden);
                envelope.getField("named").set(root, named);
                envelope.getField("direct").set(root, dog);
                envelope.getField("shape").set(root, "hello");

                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                Object compiled = impl.getConstructor().newInstance();
                SimpleJsonBinder runtime = new SimpleJsonBinder();
                Object expected = runtime.readNode(runtime.writeNodeAsString(root), Object.class);

                String json = (String) impl.getMethod("write", envelope).invoke(compiled, root);
                assertEquals(expected, runtime.readNode(json, Object.class), backend + " String");
                byte[] bytes = (byte[]) impl.getMethod("writeBytes", envelope).invoke(compiled, root);
                assertEquals(expected, runtime.readNode(
                        new String(bytes, StandardCharsets.UTF_8), Object.class), backend + " bytes");

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                impl.getMethod("writeStream", envelope, java.io.OutputStream.class).invoke(compiled, root, out);
                assertEquals(expected, runtime.readNode(out.toString(StandardCharsets.UTF_8), Object.class),
                        backend + " OutputStream");
                String animalJson = (String) impl.getMethod("writeAnimal", animal).invoke(compiled, hidden);
                Map<?, ?> hiddenNode = (Map<?, ?>) runtime.readNode(animalJson, Object.class);
                assertEquals("hidden", hiddenNode.get("kind"), backend + " hidden field");
                assertEquals("secret", hiddenNode.get("name"), backend + " hidden subtype");
            }
        }
    }
}
