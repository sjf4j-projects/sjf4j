package org.sjf4j;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.facade.simple.SimpleNodeFacade;
import org.sjf4j.mapper.NodeMapper;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Sjf4jTest {

    // --------- Sample POJO ------------
    @ToString
    public static class Person {
        public String name;
        public int age;
        public Info info;
        public List<Baby> babies;
    }

    @ToString
    @EqualsAndHashCode
    public static class Info {
        public String email;
        public String city;
    }

    @ToString
    public static class Baby {
        public String name;
        public int age;
    }

    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";

    @Test
    public void testJson2Pojo1() {
        Person p1 = Sjf4j.global().fromJson(JSON_DATA, Person.class);
        log.info("p1={}", p1);

        JsonObject jo1 = JsonObject.fromNode(p1);
        log.info("jo1={}", jo1);

        JsonObject jo2 = JsonObject.fromNode(p1);
        log.info("jo2={}", jo2);
        assertEquals(jo1, jo2);

        JsonObject jo3 = Sjf4j.global().fromJson(JSON_DATA, JsonObject.class);
        log.info("jo3={}", jo3);

        Person p2 = Sjf4j.global().fromNode(jo3, Person.class);
        log.info("p2={}", p2);
        assertNotEquals(p1, p2);

        assertEquals(Sjf4j.global().toRaw(p1), Sjf4j.global().toRaw(p2));
    }

    @Test
    public void testJson2Pojo2() {
        Person p1 = Sjf4j.global().fromJson(JSON_DATA, Person.class);
        log.info("p1={}", p1);

        JsonObject jo2 = JsonObject.fromNode(p1);
        log.info("jo2={}", jo2);

        Object n3 = Sjf4j.global().toRaw(p1);
        log.info("n3={}", n3);

        assertTrue(Nodes.equals(p1, n3));
        assertTrue(Nodes.equals(jo2, n3));
    }


    @AnyOf(value = {
            @AnyOf.Mapping(value = RuntimeCat.class, when = "cat"),
            @AnyOf.Mapping(value = RuntimeDog.class, when = "dog")
    }, key = "kind")
    interface RuntimePet {}

    static class RuntimeCat implements RuntimePet {
        public String kind;
        public String name;
        public int lives;
    }

    static class RuntimeDog implements RuntimePet {
        public String kind;
        public String name;
        public int bark;
    }

    static class RuntimeOuter {
        public RuntimeInner inner;
    }

    static class RuntimeInner {
        public Integer count;
    }

    static class RuntimeMapperSource {
        public String name;
    }

    static class RuntimeMapperTarget {
        public String name;
    }

    @Test
    void testBuilderCreatesUsableRuntime() {
        SimpleJsonFacade jsonFacade = new SimpleJsonFacade();
        Sjf4j runtime = Sjf4j.builder()
                .jsonFacade(jsonFacade)
                .build();

        JsonObject user = runtime.fromJson("{\"name\":\"han\",\"age\":18}", JsonObject.class);
        assertEquals("han", user.getString("name"));
        assertEquals(18, user.getInt("age"));
        assertEquals("{\"name\":\"han\",\"age\":18}", runtime.toJsonString(user));
        assertSame(jsonFacade, runtime.getJsonFacade());

        Map<String, Object> map = runtime.fromNode(user, new TypeReference<Map<String, Object>>() {});
        assertEquals("han", map.get("name"));

        Properties properties = runtime.toProperties(JsonObject.of("appName", "sjf4j"));
        assertEquals("sjf4j", properties.getProperty("appName"));
        JsonObject propertiesNode = runtime.fromProperties(properties, JsonObject.class);
        assertEquals("sjf4j", propertiesNode.getString("appName"));
    }

    @Test
    void testGlobalIsStableSingleton() {
        Sjf4j global = Sjf4j.global();

        assertSame(global, Sjf4j.global());
        assertSame(global.getJsonFacade(), Sjf4j.global().getJsonFacade());
    }

    @Test
    void testRuntimeFromJsonDoesNotUseRuntimeNodeFacadeInsideStreaming() {
        AtomicInteger nodeFacadeReads = new AtomicInteger();
        NodeFacade trackingNodeFacade = new NodeFacade() {
            private final NodeFacade delegate = new SimpleNodeFacade();

            @Override
            public Object readNode(Object node, java.lang.reflect.Type type, boolean deepCopy) {
                nodeFacadeReads.incrementAndGet();
                return delegate.readNode(node, type, deepCopy);
            }

            @Override
            public Object writeNode(Object node) {
                return delegate.writeNode(node);
            }
        };
        Sjf4j runtime = Sjf4j.builder()
                .jsonFacade(new SimpleJsonFacade())
                .nodeFacade(trackingNodeFacade)
                .build();

        RuntimePet pet = runtime.fromJson("{\"kind\":\"cat\",\"name\":\"Mimi\",\"lives\":9}", RuntimePet.class);

        assertInstanceOf(RuntimeCat.class, pet);
        assertEquals(0, nodeFacadeReads.get());
    }

    @Test
    void testRuntimeFromNodeAlwaysIncludesBindingPath() {
        JsonObject invalidNode = JsonObject.of("inner", JsonObject.of("count", JsonObject.of("bad", true)));
        Sjf4j runtime = Sjf4j.builder()
                .nodeFacade(new SimpleNodeFacade())
                .build();

        BindingException error = findBindingException(assertThrows(JsonException.class,
                () -> runtime.fromNode(invalidNode, RuntimeOuter.class)));

        assertNotNull(error);
        assertTrue(error.hasPathSegment());
        assertTrue(error.getMessage().contains("at path"));
    }

    @Test
    void testDefaultNodeFacadeIsGlobalSingleton() {
        Sjf4j first = Sjf4j.builder().build();
        Sjf4j second = Sjf4j.builder().build();

        assertSame(first.getNodeFacade(), second.getNodeFacade());
        assertSame(first.getNodeFacade(), Sjf4j.global().getNodeFacade());
    }

    @Test
    void testRuntimeToPojoKeepsShallowNodeReferences() {
        JsonObject profile = JsonObject.of("city", "Shanghai");
        JsonObject root = JsonObject.of("profile", profile);

        RuntimePojo user = Sjf4j.builder().build().toPojo(root, RuntimePojo.class);

        profile.put("city", "Beijing");
        assertSame(profile, user.profile);
        assertEquals("Beijing", user.profile.getString("city"));
    }

    @Test
    void testRuntimeMapperBuilderUsesDefaultNodeFacade() {
        AtomicInteger nodeFacadeReads = new AtomicInteger();
        NodeFacade trackingNodeFacade = new NodeFacade() {
            private final NodeFacade delegate = new SimpleNodeFacade();

            @Override
            public Object readNode(Object node, java.lang.reflect.Type type, boolean deepCopy) {
                nodeFacadeReads.incrementAndGet();
                return delegate.readNode(node, type, deepCopy);
            }

            @Override
            public Object writeNode(Object node) {
                return delegate.writeNode(node);
            }
        };
        Sjf4j runtime = Sjf4j.builder()
                .nodeFacade(trackingNodeFacade)
                .build();
        RuntimeMapperSource source = new RuntimeMapperSource();
        source.name = "han";

        NodeMapper<RuntimeMapperSource, RuntimeMapperTarget> mapper = runtime
                .mapperBuilder(RuntimeMapperSource.class, RuntimeMapperTarget.class)
                .build();
        RuntimeMapperTarget target = mapper.map(source);

        assertEquals("han", target.name);
        assertEquals(0, nodeFacadeReads.get());
    }

    static class RuntimePojo {
        public JsonObject profile;
    }

    private static BindingException findBindingException(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof BindingException) {
                return (BindingException) throwable;
            }
            throwable = throwable.getCause();
        }
        return null;
    }
    
}
