package org.sjf4j.testbench.processor.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.BindingOptions;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies native compiled null omission, without changing array positions. */
public class CompiledJacksonNullOmissionTest {

    private static final ObjectMapper JACKSON = new ObjectMapper();

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Compiled {
        @WriteTo String writePojo(TestBean value) throws IOException;
        @WriteTo byte[] writePojoUtf8(TestBean value) throws IOException;
        @WriteTo void writePojo(TestBean value, Writer output) throws IOException;
        @WriteTo void writePojo(TestBean value, OutputStream output) throws IOException;
        @WriteTo String writeList(List<TestBean> value) throws IOException;
        @WriteTo String writeMap(Map<String, TestBean> value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Compiled {
        @WriteTo String writePojo(TestBean value) throws IOException;
        @WriteTo byte[] writePojoUtf8(TestBean value) throws IOException;
        @WriteTo String writeList(List<TestBean> value) throws IOException;
        @WriteTo String writeMap(Map<String, TestBean> value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    @BindingOptions(includeNulls = false)
    public interface Jackson2Omitting {
        @WriteTo String writePojo(TestBean value) throws IOException;
        @WriteTo byte[] writePojoUtf8(TestBean value) throws IOException;
        @WriteTo void writePojo(TestBean value, Writer output) throws IOException;
        @WriteTo void writePojo(TestBean value, OutputStream output) throws IOException;
        @WriteTo String writeList(List<TestBean> value) throws IOException;
        @WriteTo String writeMap(Map<String, TestBean> value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    @BindingOptions(includeNulls = false)
    public interface Jackson3Omitting {
        @WriteTo String writePojo(TestBean value) throws IOException;
        @WriteTo byte[] writePojoUtf8(TestBean value) throws IOException;
        @WriteTo String writeList(List<TestBean> value) throws IOException;
        @WriteTo String writeMap(Map<String, TestBean> value) throws IOException;
    }

    public static class TestBean {
        private int id;
        private String name;
        private List<String> tags;
        private Map<String, String> attributes;
        private Leaf nested;

        public TestBean() {}
        public TestBean(int id, String name, List<String> tags,
                        Map<String, String> attributes, Leaf nested) {
            this.id = id;
            this.name = name;
            this.tags = tags;
            this.attributes = attributes;
            this.nested = nested;
        }
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public Map<String, String> getAttributes() { return attributes; }
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
        public Leaf getNested() { return nested; }
        public void setNested(Leaf nested) { this.nested = nested; }
    }

    public static class Leaf {
        private String value;
        public Leaf() {}
        public Leaf(String value) { this.value = value; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    private static TestBean sample() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("keep", "value");
        attrs.put("drop", null);
        return new TestBean(5, null, Arrays.asList("a", null, "b"), attrs,
                new Leaf(null));
    }

    private static Map<String, TestBean> sampleMap() {
        Map<String, TestBean> map = new LinkedHashMap<>();
        map.put("drop", null);
        map.put("keep", sample());
        return map;
    }

    private static void equivalent(String expected, String actual) throws IOException {
        JsonNode expectedTree = JACKSON.readTree(expected);
        JsonNode actualTree = JACKSON.readTree(actual);
        assertEquals(expectedTree, actualTree);
    }

    @Test
    public void jackson2CompiledRespectsBothPolicies() throws IOException {
        TestBean bean = sample();
        Jackson2Compiled including = CompiledInstances.global(Jackson2Compiled.class);
        Jackson2Omitting omitting = CompiledInstances.global(Jackson2Omitting.class);
        Jackson2Binder runtime = new Jackson2Binder(new JsonFactory(), new RuntimeContext(false));

        equivalent(runtime.writeNodeAsString(bean), omitting.writePojo(bean));
        equivalent(runtime.writeNodeAsString(bean),
                new String(omitting.writePojoUtf8(bean), StandardCharsets.UTF_8));
        equivalent(JACKSON.writeValueAsString(bean), including.writePojo(bean));
        equivalent(JACKSON.writeValueAsString(bean),
                new String(including.writePojoUtf8(bean), StandardCharsets.UTF_8));

        List<TestBean> list = Arrays.asList(bean, null, new TestBean(6, "ok",
                Arrays.asList((String) null), null, null));
        equivalent(runtime.writeNodeAsString(list), omitting.writeList(list));
        equivalent(runtime.writeNodeAsString(sampleMap()), omitting.writeMap(sampleMap()));
        String json = omitting.writeList(list);
        assertTrue(json.contains(",null,"));

        StringWriter chars = new StringWriter();
        omitting.writePojo(bean, chars);
        equivalent(runtime.writeNodeAsString(bean), chars.toString());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        omitting.writePojo(bean, bytes);
        equivalent(runtime.writeNodeAsString(bean),
                new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void jackson3CompiledRespectsBothPolicies() throws IOException {
        TestBean bean = sample();
        Jackson3Compiled including = CompiledInstances.global(Jackson3Compiled.class);
        Jackson3Omitting omitting = CompiledInstances.global(Jackson3Omitting.class);
        Jackson3Binder runtime = new Jackson3Binder(
                new tools.jackson.core.json.JsonFactory(), new RuntimeContext(false));

        equivalent(runtime.writeNodeAsString(bean), omitting.writePojo(bean));
        equivalent(runtime.writeNodeAsString(bean),
                new String(omitting.writePojoUtf8(bean), StandardCharsets.UTF_8));
        equivalent(JACKSON.writeValueAsString(bean), including.writePojo(bean));
        List<TestBean> list = Arrays.asList(bean, null, sample());
        equivalent(runtime.writeNodeAsString(list), omitting.writeList(list));
        equivalent(runtime.writeNodeAsString(sampleMap()), omitting.writeMap(sampleMap()));
        assertTrue(omitting.writeList(list).contains(",null,"));
    }
}
