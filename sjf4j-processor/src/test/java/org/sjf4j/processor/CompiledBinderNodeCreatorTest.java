package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledBinderNodeCreatorTest {

    private static final String[] BACKENDS = {
            "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"
    };

    @Test
    void readsExplicitConstructorAndFactoryWithMixedProperties() throws Exception {
        for (String backend : BACKENDS) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Address.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                            + "public class Address { "
                            + "private final String city; "
                            + "@NodeCreator public Address(@NodeProperty(aliases={\"town\"}) String city) { this.city=city; } "
                            + "public String getCity() { return city; } }");
            sources.put("fixture/Person.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                            + "public class Person { "
                            + "private final String name; private final Address address; "
                            + "public int age; public String note; "
                            + "public Person() { this.name=\"wrong\"; this.address=null; this.age=99; } "
                            + "@NodeCreator public Person(@NodeProperty(aliases={\"old_name\"}) String name, "
                            + "Address address) { this.name=name; this.address=address; this.age=17; } "
                            + "public String getName() { return name; } "
                            + "public Address getAddress() { return address; } }");
            sources.put("fixture/FactoryBean.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                            + "public class FactoryBean { "
                            + "private final long id; public String tag; public int priority; "
                            + "private FactoryBean(long id) { this.id=id; this.tag=\"factory\"; this.priority=8; } "
                            + "@NodeCreator public static FactoryBean create(@NodeProperty(aliases={\"legacyId\"}) long id) "
                            + "{ return new FactoryBean(id); } "
                            + "public long getId() { return id; } }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") "
                            + "public interface Binder { "
                            + "@ReadFrom Person person(String json) throws java.io.IOException; "
                            + "@ReadFrom FactoryBean factory(String json) throws java.io.IOException; "
                            + "@WriteTo String writePerson(Person value) throws java.io.IOException; }");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> iface = loader.loadClass("fixture.Binder");
                Object binder = CompiledInstances.create(iface);
                Class<?> person = loader.loadClass("fixture.Person");
                String input = "{\"note\":\"before\",\"age\":25,"
                        + "\"address\":{\"town\":\"Paris\"},\"old_name\":\"Ada\"}";
                Object value = iface.getMethod("person", String.class).invoke(binder, input);
                assertEquals("Ada", person.getMethod("getName").invoke(value), backend);
                Object address = person.getMethod("getAddress").invoke(value);
                assertEquals("Paris", address.getClass().getMethod("getCity").invoke(address), backend);
                assertEquals(25, person.getField("age").getInt(value), backend);
                assertEquals("before", person.getField("note").get(value), backend);

                Object missing = iface.getMethod("person", String.class)
                        .invoke(binder, "{\"name\":\"Bob\"}");
                assertEquals(17, person.getField("age").getInt(missing), backend);
                assertNull(person.getField("note").get(missing), backend);
                assertNull(person.getMethod("getAddress").invoke(missing), backend);
                assertNull(iface.getMethod("person", String.class).invoke(binder, "null"), backend);
                String json = (String) iface.getMethod("writePerson", person).invoke(binder, value);
                assertEquals("Ada", person.getMethod("getName")
                        .invoke(iface.getMethod("person", String.class).invoke(binder, json)), backend);

                Class<?> factoryClass = loader.loadClass("fixture.FactoryBean");
                Object factory = iface.getMethod("factory", String.class)
                        .invoke(binder, "{\"priority\":3,\"legacyId\":41}");
                assertEquals(41L, factoryClass.getMethod("getId").invoke(factory), backend);
                assertEquals("factory", factoryClass.getField("tag").get(factory), backend);
                assertEquals(3, factoryClass.getField("priority").getInt(factory), backend);
                Object emptyFactory = iface.getMethod("factory", String.class)
                        .invoke(binder, "{\"id\":6}");
                assertEquals(8, factoryClass.getField("priority").getInt(emptyFactory), backend);

                InvocationTargetException duplicate = assertThrows(InvocationTargetException.class,
                        () -> iface.getMethod("person", String.class)
                                .invoke(binder, "{\"name\":\"a\",\"old_name\":\"b\"}"), backend);
                assertTrue(duplicate.getCause().toString().contains("duplicate creator argument"), backend);
            }
        }
    }

    @Test
    void readsPackageAccessibleCreatorsAndExplicitRecordFactories() throws Exception {
        for (String backend : new String[]{"SIMPLE", "FASTJSON2"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/PackageBean.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                            + "public class PackageBean { "
                            + "private final String value; "
                            + "@NodeCreator PackageBean(String value) { this.value=value; } "
                            + "public String getValue() { return value; } }");
            sources.put("fixture/Normalized.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                            + "public record Normalized(String name) { "
                            + "@NodeCreator static Normalized of(String name) "
                            + "{ return new Normalized(name.trim().toUpperCase()); } }");
            sources.put("fixture/Alternate.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                            + "public record Alternate(String name, int count) { "
                            + "@NodeCreator public Alternate(String name) { this(name, 42); } }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder { "
                            + "@ReadFrom PackageBean bean(String json) throws java.io.IOException; "
                            + "@ReadFrom Normalized normalized(String json) throws java.io.IOException; "
                            + "@ReadFrom Alternate alternate(String json) throws java.io.IOException; }");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> iface = loader.loadClass("fixture.Binder");
                Object binder = CompiledInstances.create(iface);
                Object bean = iface.getMethod("bean", String.class)
                        .invoke(binder, "{\"value\":\"ok\"}");
                assertEquals("ok", bean.getClass().getMethod("getValue").invoke(bean), backend);
                Object normalized = iface.getMethod("normalized", String.class)
                        .invoke(binder, "{\"name\":\" alice \"}");
                assertEquals("ALICE", normalized.getClass().getMethod("name").invoke(normalized), backend);
                Object alternate = iface.getMethod("alternate", String.class)
                        .invoke(binder, "{\"name\":\"Bob\"}");
                assertEquals(42, alternate.getClass().getMethod("count").invoke(alternate), backend);
            }
        }
    }

    @Test
    void reportsDuplicateExplicitCreators() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Duplicate.java",
                "package fixture; import org.sjf4j.annotation.node.NodeCreator; "
                        + "public class Duplicate { "
                        + "@NodeCreator public Duplicate(String value) {} "
                        + "@NodeCreator public static Duplicate of(String value) { return new Duplicate(value); } }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend.SIMPLE) interface Binder { "
                        + "@ReadFrom Duplicate read(String json) throws java.io.IOException; }");
        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(compiled.success);
        assertTrue(compiled.diagnostics().contains("multiple @NodeCreator"), compiled.diagnostics());
    }
}
