package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles actual Binder implementations with inherited and generated-name collisions. */
class CompiledBinderNamingTest {

    @Test
    void retainsOnlyReachableStringHelpersForEachWritePolicy() throws Exception {
        for (String backend : new String[] {"JACKSON2", "JACKSON3"}) {
            for (boolean include : new boolean[] {true, false}) {
                Map<String, String> sources = new LinkedHashMap<>();
                sources.put("fixture/Bean.java",
                        "package fixture; public class Bean { public String name; }");
                sources.put("fixture/Binder.java",
                        "package fixture; import org.sjf4j.annotation.binding.*; "
                                + "@CompiledBinder(backend=Backend." + backend + ",includeNulls=" + include + ") "
                                + "public interface Binder { @Write String write(Bean value) throws java.io.IOException; }");
                NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                        sources, CodegenProcessor.class);
                assertTrue(result.success, result.diagnostics());
                String generated = result.generatedSource("fixture/Binder_Impl.java");
                String used = include ? "IncludeNulls" : "OmitNulls";
                String unused = include ? "OmitNulls" : "IncludeNulls";
                assertTrue(generated.contains("private static void _writeStringMember" + used + "("), generated);
                assertFalse(generated.contains("private static void _writeStringMember" + unused + "("), generated);
                assertFalse(generated.contains("private static void _writeString("), generated);
            }
        }
    }

    @Test
    void retainsTransitiveHelpersForFixedWritePolicy() throws Exception {
        for (String backend : new String[] {"JACKSON2", "JACKSON3"}) {
            for (boolean include : new boolean[]{true, false}) {
                Map<String, String> sources = new LinkedHashMap<>();
                sources.put("fixture/Bean.java",
                        "package fixture; public class Bean { public Child child; }");
                sources.put("fixture/Child.java",
                        "package fixture; public class Child { public String name; }");
                sources.put("fixture/Binder.java",
                        "package fixture; import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend." + backend + ",includeNulls=" + include + ") "
                        + "public interface Binder { @Write String write(Bean value) throws java.io.IOException; }");
                NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
                assertTrue(result.success, result.diagnostics());
                String generated = result.generatedSource("fixture/Binder_Impl.java");
                assertEqualsPolicyHelper(generated, include);
            }
        }
    }

    private static void assertEqualsPolicyHelper(String generated, boolean include) {
        String used = include ? "IncludeNulls" : "OmitNulls";
        String unused = include ? "OmitNulls" : "IncludeNulls";
        assertTrue(generated.contains("private static void _writeStringMember" + used + "("), generated);
        assertFalse(generated.contains("private static void _writeStringMember" + unused + "("), generated);
        assertFalse(generated.contains("private static void _writeString("), generated);
    }

    @Test
    void inheritsMemberNamesWithoutConflictingWithGeneratedHelpers() throws Exception {
        for (String backend : new String[]{"JACKSON2", "FASTJSON2", "GSON", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture; public class Bean {"
                            + " public String name; public Status status;"
                            + " public enum Status { ACTIVE, INACTIVE } }");
            sources.put("fixture/Base.java",
                    "package fixture; public interface Base {"
                            + " String BEAN_NAME_MATCHER = \"reserved\";"
                            + " String BEAN_NAME = \"reserved\";"
                            + " String STATUS_ORDINALS = \"reserved\";"
                            + " String FASTJSON2_LITTLE_ENDIAN = \"reserved\";"
                            + " default void _readBean() {}"
                            + " default void _writeBean() {}"
                            + " default void _writeBeanUtf16() {}"
                            + " default void _writeString() {}"
                            + " default void _writeStringMemberIncludeNulls() {}"
                            + " }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                            + " @CompiledBinder(backend=Backend." + backend + ")"
                            + " public interface Binder extends Base {"
                            + " @Read Bean read(String source) throws java.io.IOException;"
                            + " @Write String write(Bean value) throws java.io.IOException;"
                            + " }");

            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                    sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());

            String generated = result.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("_readBean2("), backend);
            if ("FASTJSON2".equals(backend)) {
                assertTrue(generated.contains("_writeBeanUtf162("), backend);
            } else {
                assertTrue(generated.contains("_writeBean2("), backend);
            }
            // Only helpers actually used by this graph should survive.
            if ("JACKSON2".equals(backend)) {
                assertFalse(generated.contains("private static void _writeString2("), backend);
            }
            assertFalse(generated.contains("_readBean_names"), backend);
            assertFalse(generated.contains("_writeBean_name_"), backend);

            if ("JACKSON2".equals(backend) || "FASTJSON2".equals(backend)) {
                assertTrue(generated.contains("BEAN_NAME_MATCHER2"), backend);
                assertTrue(generated.contains("BEAN_NAME2"), backend);
            }
            if ("JACKSON2".equals(backend)) {
                assertTrue(generated.contains("_writeStringMemberIncludeNulls2("), backend);
                assertFalse(generated.contains("private static void _writeStringMemberOmitNulls("), backend);
            }
            if ("FASTJSON2".equals(backend)) {
                assertTrue(generated.contains("_readStatusEnum("), backend);
                assertTrue(generated.contains("STATUS_ORDINALS2"), backend);
                assertTrue(generated.contains("FASTJSON2_LITTLE_ENDIAN2"), backend);
            }
        }
    }
    @Test
    void propertyLocalsAvoidTemplateMetadataAndNormalizedNameCollisions() throws Exception {
        for (String backend : new String[]{"JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                    + "public class Bean { "
                    + "public final String parser; public final String value; "
                    + "@NodeProperty(\\\"first-name\\\") public final String first; "
                    + "@NodeProperty(\\\"first_name\\\") public final String second; "
                    + "public String item; public String nativeGenerator; "
                    + "public String uuidValueInfo; public java.util.UUID uuid; "
                    + "@NodeCreator public Bean(String parser, String value, "
                    + "@NodeProperty(\\\"first-name\\\") String first, "
                    + "@NodeProperty(\\\"first_name\\\") String second) { "
                    + "this.parser=parser; this.value=value; this.first=first; this.second=second; } }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                    + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder { "
                    + "@Read Bean read(String parser) throws java.io.IOException; "
                    + "@Write String write(Bean nativeGenerator) throws java.io.IOException; }");
            NavigatorTestCompiler.Result first = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(first.success, backend + ": " + first.diagnostics());
            String generated = first.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("String firstName = "), generated);
            assertTrue(generated.contains("String firstName2 = "), generated);
            assertTrue(generated.contains("String parser2 = "), generated);
            assertTrue(generated.contains("String value2 = "), generated);
            assertFalse(generated.contains("String arg0 = "), generated);
            assertFalse(generated.contains("String property0 = "), generated);
            NavigatorTestCompiler.Result second = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(second.success, second.diagnostics());
            org.junit.jupiter.api.Assertions.assertEquals(generated,
                    second.generatedSource("fixture/Binder_Impl.java"));
        }
    }

    @Test
    void structuralHelperHintsIncludeTypeArgumentsAndNativeSuffixBeforeNumbering() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Child.java", "package fixture; public class Child { public String name; }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.JACKSON3) public interface Binder { "
                + "default void _readChild() {} default void _readChildNative() {} "
                + "@Read Child read(byte[] source) throws java.io.IOException; "
                + "@Read java.util.List<Child> readList(byte[] source) throws java.io.IOException; "
                + "@Read Child[] readArray(String source) throws java.io.IOException; }");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("_readChildNative2("), generated);
        assertTrue(generated.contains("_readChildListNative("), generated);
        assertTrue(generated.contains("_readChildArray("), generated);
        assertFalse(generated.contains("_readChild2Native("), generated);
    }
}
