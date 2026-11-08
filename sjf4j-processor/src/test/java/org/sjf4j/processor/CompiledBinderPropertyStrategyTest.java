package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The compiled Binder must respect Runtime's property discovery and access order. */
class CompiledBinderPropertyStrategyTest {

    private NavigatorTestCompiler.Result compile(String strategy, String model,
                                                 String direction) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java", "package fixture;\n"
                + "import org.sjf4j.annotation.node.*;\n"
                + "@NodeObject(propertyStrategy=PropertyStrategy." + strategy + ")\n"
                + "public class Bean {\n" + model + "\n}\n");
        sources.put("fixture/Binder.java", "package fixture;\n"
                + "import org.sjf4j.annotation.binding.*;\n"
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder {\n"
                + ("READ".equals(direction)
                    ? "  @Read Bean bind(String input);\n"
                    : "  @Write String bind(Bean input);\n")
                + "}\n");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class,
                "-Xlint:cast", "-Werror");
    }

    private void expectFailure(String strategy, String model, String direction, String member)
            throws Exception {
        NavigatorTestCompiler.Result result = compile(strategy, model, direction);
        assertFalse(result.success, strategy + " " + direction + ": expected a compiler error");
        String diagnostics = result.diagnostics();
        assertTrue(diagnostics.contains("@CompiledBinder cannot access non-public"),
                diagnostics);
        assertTrue(diagnostics.contains("propertyStrategy=" + strategy), diagnostics);
        assertTrue(diagnostics.contains(member), diagnostics);
    }

    @Test
    void fieldOnlyRejectsPrivateFieldsInsteadOfUsingPublicBeanAccessors() throws Exception {
        String model = "private String value;\n"
                + "public String getValue() { return value; }\n"
                + "public void setValue(String v) { value = v; }";
        expectFailure("FIELD_ONLY", model, "READ", "value");
        expectFailure("FIELD_ONLY", model, "WRITE", "value");
    }

    @Test
    void fieldBeanRejectsInaccessiblePrimaryFieldsEvenWithPublicBeans() throws Exception {
        String model = "private String value;\n"
                + "public String getValue() { return value; }\n"
                + "public void setValue(String v) { value = v; }";
        expectFailure("FIELD_BEAN", model, "READ", "value");
        expectFailure("FIELD_BEAN", model, "WRITE", "value");
    }

    @Test
    void beanFieldAllowsPublicGetterButRejectsPrivateFieldSetterFallback() throws Exception {
        String model = "@NodeProperty(\"value\") private String value;\n"
                + "public String getValue() { return value; }";
        NavigatorTestCompiler.Result write = compile("BEAN_FIELD", model, "WRITE");
        assertTrue(write.success, write.diagnostics());
        expectFailure("BEAN_FIELD", model, "READ", "value");
    }

    @Test
    void beanOnlyIgnoresPrivateAndPublicFieldsWithoutBeanAccessors() throws Exception {
        String model = "private String secret;\n"
                + "public String fieldOnly;\n"
                + "private String value;\n"
                + "public String getValue() { return value; }\n"
                + "public void setValue(String v) { value = v; }";
        for (String direction : new String[]{"READ", "WRITE"}) {
            NavigatorTestCompiler.Result result = compile("BEAN_ONLY", model, direction);
            assertTrue(result.success, direction + ": " + result.diagnostics());
            String generated = result.generatedSource("fixture/Binder_Impl.java");
            assertFalse(generated.contains("fieldOnly"), direction);
            assertFalse(generated.contains("secret"), direction);
        }
    }

    @Test
    void beanOnlyRejectsNonPublicAnnotatedBeanMembers() throws Exception {
        expectFailure("BEAN_ONLY",
                "@NodeProperty(\"wire\") private String getValue() { return \"x\"; }",
                "WRITE", "getValue");
        expectFailure("BEAN_ONLY",
                "@NodeProperty(\"wire\") private void setValue(String value) {}",
                "READ", "setValue");
    }

    @Test
    void fieldOnlyIgnoresPublicGetterAndUsesPublicField() throws Exception {
        String model = "public String value;\n"
                + "public String getValue() { return \"wrong\"; }\n"
                + "public void setValue(String v) { value = \"wrong\"; }";
        NavigatorTestCompiler.Result result = compile("FIELD_ONLY", model, "WRITE");
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> bean = Class.forName("fixture.Bean", true, loader);
            Object value = bean.getConstructor().newInstance();
            bean.getField("value").set(value, "expected");
            Class<?> binder = Class.forName("fixture.Binder_Impl", true, loader);
            Object instance = binder.getConstructor().newInstance();
            assertEquals("{\"value\":\"expected\"}",
                    binder.getMethod("bind", bean).invoke(instance, value));
        }
    }

    @Test
    void fieldBeanUsesPublicFieldBeforePublicGetter() throws Exception {
        String model = "public String value;\n"
                + "public String getValue() { return \"wrong\"; }";
        NavigatorTestCompiler.Result result = compile("FIELD_BEAN", model, "WRITE");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("value.value"), generated);
        assertFalse(generated.contains("value.getValue()"), generated);
    }

    @Test
    void ignoredOrTransientFieldsDoNotTriggerErrors() throws Exception {
        String model = "@NodeIgnore private String ignored;\n"
                + "private transient String hidden;\n"
                + "public String visible;";
        for (String strategy : new String[]{"FIELD_ONLY", "FIELD_BEAN", "BEAN_FIELD", "BEAN_ONLY"}) {
            for (String direction : new String[]{"READ", "WRITE"}) {
                NavigatorTestCompiler.Result result = compile(strategy, model, direction);
                assertTrue(result.success, strategy + " " + direction + ": " + result.diagnostics());
            }
        }
    }

    @Test
    void defaultBeanFieldIgnoresUnannotatedPrivateBackingFields() throws Exception {
        String model = "private String value;\n"
                + "public String getValue() { return value; }\n"
                + "public void setValue(String v) { value = v; }";
        for (String direction : new String[]{"READ", "WRITE"}) {
            NavigatorTestCompiler.Result result = compile("BEAN_FIELD", model, direction);
            assertTrue(result.success, direction + ": " + result.diagnostics());
        }
    }
    @Test
    void inheritedPrivateFieldCannotDisappearFromFieldOnlyBinding() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Parent.java",
                "package fixture; public class Parent {"
                        + " private String secret;"
                        + " public String getSecret() { return secret; }"
                        + " public void setSecret(String v) { secret = v; }"
                        + " }");
        sources.put("fixture/Bean.java",
                "package fixture; import org.sjf4j.annotation.node.*;"
                        + " @NodeObject(propertyStrategy=PropertyStrategy.FIELD_ONLY)"
                        + " public class Bean extends Parent {}");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*;"
                        + " @CompiledBinder(backend=Backend.SIMPLE) public interface Binder {"
                        + " @Read Bean read(String json);"
                        + " @Write String write(Bean value);"
                        + " }");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class, "-Xlint:cast", "-Werror");
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("secret"), result.diagnostics());
        assertTrue(result.diagnostics().contains("FIELD_ONLY"), result.diagnostics());
    }

    @Test
    void beanOnlyDoesNotSuppressAccessorsForTransientBackingFields() throws Exception {
        String model = "private transient String value;\n"
                + "public String getValue() { return value; }\n"
                + "public void setValue(String v) { value = v; }";
        NavigatorTestCompiler.Result read = compile("BEAN_ONLY", model, "READ");
        NavigatorTestCompiler.Result write = compile("BEAN_ONLY", model, "WRITE");
        assertTrue(read.success, read.diagnostics());
        assertTrue(write.success, write.diagnostics());
        try (URLClassLoader loader = read.classLoader(getClass().getClassLoader())) {
            Class<?> binder = Class.forName("fixture.Binder_Impl", true, loader);
            Object impl = binder.getConstructor().newInstance();
            Object bean = binder.getMethod("bind", String.class)
                    .invoke(impl, "{\"value\":\"from-bean\"}");
            assertEquals("from-bean", bean.getClass().getMethod("getValue").invoke(bean));
        }
        String generated = write.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("getValue()"), generated);
    }

    @Test
    void beanFieldRetainsFieldAliasWithPublicGetterAndSetter() throws Exception {
        String model = "@NodeProperty(value=\"name\", aliases={\"legacy\"}) public String name;\n"
                + "public String getName() { return name; }\n"
                + "public void setName(String name) { this.name = name; }";
        NavigatorTestCompiler.Result result = compile("BEAN_FIELD", model, "READ");
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> binder = Class.forName("fixture.Binder_Impl", true, loader);
            Object instance = binder.getConstructor().newInstance();
            Object bean = binder.getMethod("bind", String.class)
                    .invoke(instance, "{\"legacy\":\"Alice\"}");
            assertEquals("Alice", bean.getClass().getField("name").get(bean));
        }
    }

    @Test
    void beanFieldRetainsFieldValuePatternWithPublicAccessors() throws Exception {
        String model = "@NodeProperty(valuePattern=\"yyyy/MM/dd\") public java.time.LocalDate date;\n"
                + "public java.time.LocalDate getDate() { return date; }\n"
                + "public void setDate(java.time.LocalDate v) { date = v; }";
        NavigatorTestCompiler.Result result = compile("BEAN_FIELD", model, "READ");
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> binder = Class.forName("fixture.Binder_Impl", true, loader);
            Object instance = binder.getConstructor().newInstance();
            Object bean = binder.getMethod("bind", String.class)
                    .invoke(instance, "{\"date\":\"2026/11/08\"}");
            assertEquals(java.time.LocalDate.of(2026, 11, 8),
                    bean.getClass().getField("date").get(bean));
        }
    }
}

