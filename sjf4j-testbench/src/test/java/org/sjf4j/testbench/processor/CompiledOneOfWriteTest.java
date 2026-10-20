package org.sjf4j.testbench.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Output-level checks for compiled OneOf Class dispatch.
 * Parsing the output as JSON detects omitted fields without relying on generated source text.
 */
public class CompiledOneOfWriteTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @OneOf(key = "kind", mappings = {
            @OneOfMapping(type = Cat.class, when = "cat"),
            @OneOfMapping(type = Dog.class, when = "dog"),
            @OneOfMapping(type = Secret.class, when = "secret")
    })
    public interface Pet {}

    public static class Cat implements Pet {
        public String kind = "cat";
        public String variant = "feline";
        public int lives = 9;
    }

    public static class Dog implements Pet {
        public String kind = "dog";
        public String variant = "canine";
        public boolean trained = true;
    }

    /** The discriminator is not accessible to the generated writer. */
    public static class Secret implements Pet {
        @NodeProperty("kind")
        private final String discriminator = "secret";
        public String payload = "kept";
    }

    /** Not declared in Pet's mappings; existing Runtime semantics must be retained. */
    public static class Bird implements Pet {
        public String kind = "bird";
        public String name = "Kiwi";
    }

    public static class Envelope {
        public long id;
        public List<Pet> pets;
        public Map<String, Pet> byName;
        public Pet featured;
        @OneOf(key = "variant", mappings = {
                @OneOfMapping(type = Cat.class, when = "feline"),
                @OneOfMapping(type = Dog.class, when = "canine")
        })
        public Pet selected;
        public String after;
    }

    @OneOf(key = "kind", mappings = {
            @OneOfMapping(type = Leaf.class, when = "leaf"),
            @OneOfMapping(type = Branch.class, when = "branch")
    })
    public interface Tree {}

    public static class Leaf implements Tree {
        public String kind = "leaf";
        public String value;
        public Leaf() {}
        public Leaf(String value) { this.value = value; }
    }

    public static class Branch implements Tree {
        public String kind = "branch";
        public List<Tree> children;
        public Tree next;
    }

    @OneOf(key = "kind", mappings = {
            @OneOfMapping(type = RawListValue.class, when = "raw"),
            @OneOfMapping(type = NumericMapValue.class, when = "numberMap"),
            @OneOfMapping(type = IgnoredFieldValue.class, when = "ignoredField"),
            @OneOfMapping(type = IgnoredGetterValue.class, when = "ignoredGetter"),
            @OneOfMapping(type = PrivateGetterValue.class, when = "privateGetter"),
            @OneOfMapping(type = InheritedPrivateValue.class, when = "inherited"),
            @OneOfMapping(type = PlainValue.class, when = "plain")
    })
    public interface ComplexValue {}

    public static class PlainValue implements ComplexValue {
        public String kind = "plain";
        public String visible = "compiled";
    }

    public static class RawListValue implements ComplexValue {
        public String kind = "raw";
        @SuppressWarnings("rawtypes")
        public List values = List.of("text", 7);
    }

    public static class NumericMapValue implements ComplexValue {
        public String kind = "numberMap";
        public Map<Integer, String> attributes = Map.of(17, "seventeen");
    }

    public static class IgnoredFieldValue implements ComplexValue {
        public String kind = "ignoredField";
        public String visible = "public";
        @NodeIgnore
        public String secret = "must-not-appear";
    }

    public static class IgnoredGetterValue implements ComplexValue {
        public String kind = "ignoredGetter";
        public String visible = "public";
        @NodeIgnore
        public String getPassword() { return "must-not-appear"; }
    }

    public static class PrivateGetterValue implements ComplexValue {
        public String kind = "privateGetter";
        @NodeProperty("privateValue")
        private String getPrivateValue() { return "getter-retained"; }
    }

    public static class AnnotatedParent {
        @NodeProperty("inheritedPrivate")
        private final String discriminator = "inherited-retained";
    }

    public static class InheritedPrivateValue extends AnnotatedParent implements ComplexValue {
        public String kind = "inherited";
        public int number = 5;
    }

    public static class ComplexEnvelope {
        public String header = "before";
        public List<ComplexValue> values;
        public Map<String, ComplexValue> byName;
        public String trailer = "after";
    }

    public interface OneOfWriter {
        @WriteTo String writeEnvelope(Envelope value) throws IOException;
        @WriteTo byte[] writeEnvelopeBytes(Envelope value) throws IOException;
        @WriteTo void writeEnvelope(Envelope value, java.io.OutputStream output) throws IOException;
        @WriteTo void writeEnvelope(Envelope value, java.io.Writer output) throws IOException;
        @ReadFrom Envelope readEnvelope(String json) throws IOException;
        @WriteTo String writePet(Pet value) throws IOException;
        @ReadFrom Pet readPet(String json) throws IOException;
        @WriteTo String writeComplex(ComplexEnvelope value) throws IOException;
        @WriteTo String writeTree(Tree value) throws IOException;
        @ReadFrom Tree readTree(String json) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleWriter extends OneOfWriter {}

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonWriter extends OneOfWriter {}

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Writer extends OneOfWriter {}

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Writer extends OneOfWriter {}

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2Writer extends OneOfWriter {}

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpWriter extends OneOfWriter {}

    static Stream<Arguments> backends() {
        return Stream.of(
                Arguments.of("simple", CompiledInstances.global(SimpleWriter.class), new SimpleJsonBinder()),
                Arguments.of("gson", CompiledInstances.global(GsonWriter.class), new GsonBinder()),
                Arguments.of("jackson2", CompiledInstances.global(Jackson2Writer.class), new Jackson2Binder()),
                Arguments.of("jackson3", CompiledInstances.global(Jackson3Writer.class), new Jackson3Binder()),
                Arguments.of("fastjson2", CompiledInstances.global(Fastjson2Writer.class), new Fastjson2Binder()),
                Arguments.of("jsonp", CompiledInstances.global(JsonpWriter.class), new JsonpBinder()));
    }

    private static Envelope envelope() {
        Cat cat = new Cat();
        Dog dog = new Dog();
        Secret secret = new Secret();
        Envelope value = new Envelope();
        value.id = 42;
        value.pets = new ArrayList<>();
        value.pets.add(cat);
        value.pets.add(dog);
        value.pets.add(null);
        value.pets.add(secret);
        value.byName = new LinkedHashMap<>();
        value.byName.put("cat", cat);
        value.byName.put("secret", secret);
        value.featured = dog;
        value.selected = cat;
        value.after = "tail";
        return value;
    }

    private static JsonNode tree(String json) throws IOException {
        return JSON.readTree(json);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void writesClassAndMemberOneOfWithinListAndMap(String backend,
            OneOfWriter compiled, Binder<?, ?> runtime) throws Exception {
        Envelope input = envelope();
        JsonNode expected = tree(runtime.writeNodeAsString(input));
        JsonNode result = tree(compiled.writeEnvelope(input));
        assertEquals(expected, result, backend);
        assertEquals(42, result.path("id").asLong(), backend);
        assertEquals("cat", result.path("pets").get(0).path("kind").asText(), backend);
        assertEquals("dog", result.path("pets").get(1).path("kind").asText(), backend);
        assertTrue(result.path("pets").get(2).isNull(), backend);
        assertEquals("secret", result.path("pets").get(3).path("kind").asText(), backend);
        assertEquals("secret", result.path("byName").path("secret").path("kind").asText(), backend);
        assertEquals("feline", result.path("selected").path("variant").asText(), backend);
        assertEquals("tail", result.path("after").asText(), backend);

        Envelope roundTrip = compiled.readEnvelope(compiled.writeEnvelope(input));
        assertInstanceOf(Cat.class, roundTrip.pets.get(0), backend);
        assertInstanceOf(Dog.class, roundTrip.pets.get(1), backend);
        assertNull(roundTrip.pets.get(2), backend);
        assertInstanceOf(Secret.class, roundTrip.pets.get(3), backend);
        assertInstanceOf(Secret.class, roundTrip.byName.get("secret"), backend);
        assertInstanceOf(Cat.class, roundTrip.selected, backend);
        assertEquals("tail", roundTrip.after, backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void writesAllOutputFormsWithSameJson(String backend,
            OneOfWriter compiled, Binder<?, ?> runtime) throws Exception {
        Envelope input = envelope();
        JsonNode expected = tree(runtime.writeNodeAsString(input));
        assertEquals(expected, tree(compiled.writeEnvelope(input)), backend + " String");
        assertEquals(expected, tree(new String(compiled.writeEnvelopeBytes(input),
                StandardCharsets.UTF_8)), backend + " byte[]");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        compiled.writeEnvelope(input, bytes);
        assertEquals(expected, tree(bytes.toString(StandardCharsets.UTF_8)), backend + " OutputStream");

        StringWriter writer = new StringWriter();
        compiled.writeEnvelope(input, writer);
        assertEquals(expected, tree(writer.toString()), backend + " Writer");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void retainsPrivateDiscriminatorAndUnknownSubtype(String backend,
            OneOfWriter compiled, Binder<?, ?> runtime) throws Exception {
        Secret secret = new Secret();
        JsonNode output = tree(compiled.writePet(secret));
        assertEquals(tree(runtime.writeNodeAsString(secret)), output, backend);
        assertEquals("secret", output.path("kind").asText(), backend);
        assertEquals("kept", output.path("payload").asText(), backend);
        assertInstanceOf(Secret.class, compiled.readPet(compiled.writePet(secret)), backend);

        Bird unmapped = new Bird();
        assertEquals(tree(runtime.writeNodeAsString(unmapped)),
                tree(compiled.writePet(unmapped)), backend + " unmapped");
        assertTrue(tree(compiled.writePet(null)).isNull(), backend + " null");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void fallsBackForUnsupportedAndSemanticallyDifferentSubtypeGraphs(String backend,
            OneOfWriter compiled, Binder<?, ?> runtime) throws Exception {
        ComplexEnvelope model = new ComplexEnvelope();
        model.values = List.of(
                new PlainValue(),
                new RawListValue(),
                new NumericMapValue(),
                new IgnoredFieldValue(),
                new IgnoredGetterValue(),
                new PrivateGetterValue(),
                new InheritedPrivateValue());
        model.byName = new LinkedHashMap<>();
        model.byName.put("numeric", model.values.get(2));
        model.byName.put("inherited", model.values.get(6));

        JsonNode actual = tree(compiled.writeComplex(model));
        assertEquals(tree(runtime.writeNodeAsString(model)), actual, backend);
        assertEquals("before", actual.path("header").asText(), backend);
        assertEquals("after", actual.path("trailer").asText(), backend);
        assertEquals("compiled", actual.path("values").get(0).path("visible").asText(), backend);
        assertEquals("text", actual.path("values").get(1).path("values").get(0).asText(), backend);
        assertEquals("seventeen", actual.path("values").get(2)
                .path("attributes").path("17").asText(), backend);
        assertFalse(actual.path("values").get(3).has("secret"), backend);
        assertFalse(actual.path("values").get(4).has("password"), backend);
        assertEquals("getter-retained", actual.path("values").get(5)
                .path("privateValue").asText(), backend);
        assertEquals("inherited-retained", actual.path("values").get(6)
                .path("inheritedPrivate").asText(), backend);
        assertEquals("seventeen", actual.path("byName").path("numeric")
                .path("attributes").path("17").asText(), backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void writesRecursiveOneOfTree(String backend,
            OneOfWriter compiled, Binder<?, ?> runtime) throws Exception {
        Branch nested = new Branch();
        nested.children = List.of(new Leaf("child"));
        nested.next = new Leaf("next");
        Branch root = new Branch();
        root.children = List.of(new Leaf("first"), nested);
        root.next = new Leaf("last");

        String json = compiled.writeTree(root);
        assertEquals(tree(runtime.writeNodeAsString(root)), tree(json), backend);
        Tree decoded = compiled.readTree(json);
        Branch actual = assertInstanceOf(Branch.class, decoded, backend);
        assertEquals("first", assertInstanceOf(Leaf.class, actual.children.get(0), backend).value, backend);
        Branch child = assertInstanceOf(Branch.class, actual.children.get(1), backend);
        assertEquals("child", assertInstanceOf(Leaf.class, child.children.get(0), backend).value, backend);
        assertEquals("next", assertInstanceOf(Leaf.class, child.next, backend).value, backend);
        assertEquals("last", assertInstanceOf(Leaf.class, actual.next, backend).value, backend);
    }
}
