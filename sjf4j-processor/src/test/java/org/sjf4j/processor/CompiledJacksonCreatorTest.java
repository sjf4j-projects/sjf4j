package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Jackson annotations should select the same Creator in Runtime and CompiledBinder. */
class CompiledJacksonCreatorTest {

    private static final String[] BACKENDS = {"SIMPLE", "JACKSON2", "JACKSON3"};

    @Test
    void jacksonCreatorConstructorsAndStaticFactoriesMatchRuntime() throws Exception {
        for (boolean factory : new boolean[]{false, true}) {
                for (String backend : BACKENDS) {
                    Map<String, String> sources = new LinkedHashMap<>();
                    sources.put("fixture/Bean.java", beanSource(factory));
                    sources.put("fixture/Binder.java", "package fixture;\n"
                            + "import org.sjf4j.annotation.binding.*;\n"
                            + "@CompiledBinder(backend=Backend." + backend + ")\n"
                            + "public interface Binder {\n"
                            + " @ReadFrom Bean read(String json) throws java.io.IOException;\n"
                            + "}\n");

                    NavigatorTestCompiler.Result result =
                            NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
                    String label = (factory ? "factory" : "constructor") + " / " + backend;
                    assertTrue(result.success, label + ": " + result.diagnostics());

                    try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                        Class<?> beanType = Class.forName("fixture.Bean", true, loader);
                        Class<?> impl = Class.forName("fixture.Binder_Impl", true, loader);
                        Object compiled = impl.getConstructor().newInstance();
                        SimpleJsonBinder runtime = new SimpleJsonBinder();

                        for (String json : new String[]{
                                "{\"extra\":\"tail\",\"legacyLabel\":\"Ada\",\"count\":7}",
                                "{\"count\":7,\"label\":\"Ada\",\"extra\":\"tail\"}"
                        }) {
                            Object actual = impl.getMethod("read", String.class).invoke(compiled, json);
                            Object expected = runtime.readNode(json, beanType);
                            assertEquals(beanType.getField("value").get(expected),
                                    beanType.getField("value").get(actual), label);
                            assertEquals("Ada", beanType.getField("value").get(actual), label);
                            assertEquals(beanType.getField("count").getInt(expected),
                                    beanType.getField("count").getInt(actual), label);
                            assertEquals(7, beanType.getField("count").getInt(actual), label);
                            assertEquals("tail", beanType.getField("extra").get(actual), label);
                        }
                    }
                }
        }
    }

    @Test
    void multipleCreatorAnnotationsProduceCompileError() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java", "package fixture;\n"
                + "import org.sjf4j.annotation.node.*;\n"
                + "import com.fasterxml.jackson.annotation.*;\n"
                + "public class Bean {\n"
                + " @JsonCreator public Bean(@JsonProperty(\"label\") String label) {}\n"
                + " @NodeCreator public static Bean create() { return new Bean(\"default\"); }\n"
                + "}\n");
        sources.put("fixture/Binder.java", "package fixture;\n"
                + "import org.sjf4j.annotation.binding.*;\n"
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder {\n"
                + " @ReadFrom Bean read(String json) throws java.io.IOException;\n"
                + "}\n");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("multiple creator definitions"), result.diagnostics());
    }

    private static String beanSource(boolean factory) {
        String prefix = "package fixture;\nimport com.fasterxml.jackson.annotation.*;\n"
                + "public class Bean {\n"
                + " public final String value;\n"
                + " public final int count;\n"
                + " public String extra;\n";
        if (factory) {
            return prefix
                    + " private Bean(String value, int count) { this.value=value; this.count=count; }\n"
                    + " @JsonCreator public static Bean create(\n"
                    + "   @JsonProperty(\"label\") @JsonAlias({\"legacyLabel\"}) String value,\n"
                    + "   @JsonProperty(\"count\") int count) { return new Bean(value,count); }\n"
                    + "}\n";
        }
        return prefix
                + " public Bean() { this.value=\"wrong\"; this.count=-1; }\n"
                + " @JsonCreator public Bean(\n"
                + "   @JsonProperty(\"label\") @JsonAlias({\"legacyLabel\"}) String value,\n"
                + "   @JsonProperty(\"count\") int count) { this.value=value; this.count=count; }\n"
                + "}\n";
    }
}
