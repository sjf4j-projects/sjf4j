package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationPropertyIntegrationTest {

    @Test
    void annotatedArbitraryGetterAndInheritedGenericPropertyUseExplicitAliases() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/Models.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Models {\n"
                        + "  public static class Protocol {\n"
                        + "    private String value = \"old-protocol\";\n"
                        + "    @NodeProperty(\"wire-value\") public String readFromProtocol() { return value; }\n"
                        + "    @NodeProperty(\"wire-value\") public void setProtocolValue(String next) { value = next; }\n"
                        + "  }\n"
                        + "  public static class Parent<T> { public T inherited = null; }\n"
                        + "  public static class Child extends Parent<String> {}\n"
                        + "}\n",
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$.wire-value\") String protocol(Models.Protocol root);\n"
                        + "  @PutByPath(\"$.wire-value\") String protocol(Models.Protocol root, String value);\n"
                        + "  @GetByPath(\"$.inherited\") String inherited(Models.Child root);\n"
                        + "  @PutByPath(\"$.inherited\") String inherited(Models.Child root, String value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> protocolClass = Class.forName("testcase.Models$Protocol", true, loader);
            Class<?> childClass = Class.forName("testcase.Models$Child", true, loader);
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object paths = pathsClass.getConstructor().newInstance();
            Object protocol = protocolClass.getConstructor().newInstance();
            Object child = childClass.getConstructor().newInstance();

            assertEquals("old-protocol", pathsClass.getMethod("protocol", protocolClass).invoke(paths, protocol));
            assertEquals("old-protocol", pathsClass.getMethod("protocol", protocolClass, String.class)
                    .invoke(paths, protocol, "new-protocol"));
            assertEquals("new-protocol", pathsClass.getMethod("protocol", protocolClass).invoke(paths, protocol));

            assertNull(pathsClass.getMethod("inherited", childClass).invoke(paths, child));
            assertNull(pathsClass.getMethod("inherited", childClass, String.class)
                    .invoke(paths, child, "new-inherited"));
            assertEquals("new-inherited", pathsClass.getMethod("inherited", childClass).invoke(paths, child));
        }
    }
}
