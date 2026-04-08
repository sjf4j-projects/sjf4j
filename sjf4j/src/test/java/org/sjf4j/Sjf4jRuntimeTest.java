package org.sjf4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.TypeReference;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathCache;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class Sjf4jRuntimeTest {

    private Sjf4jConfig previousConfig;

    @BeforeEach
    void saveGlobalConfig() {
        previousConfig = Sjf4jConfig.global();
    }

    @AfterEach
    void restoreGlobalConfig() {
        if (previousConfig != null) {
            Sjf4jConfig.global(previousConfig);
        }
    }

    @Test
    void testBuilderCreatesUsableRuntime() {
        SimpleJsonFacade jsonFacade = new SimpleJsonFacade();
        Sjf4jRuntime runtime = Sjf4j.builder()
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
    void testRuntimeFactorySnapshotsGlobalConfig() {
        SimpleJsonFacade firstFacade = new SimpleJsonFacade();
        SimpleJsonFacade secondFacade = new SimpleJsonFacade();
        Sjf4jConfig firstConfig = new Sjf4jConfig.Builder()
                .jsonFacade(firstFacade)
                .build();
        Sjf4jConfig secondConfig = new Sjf4jConfig.Builder()
                .jsonFacade(secondFacade)
                .build();

        Sjf4jConfig.global(firstConfig);
        Sjf4jRuntime runtime = Sjf4j.runtime();
        Sjf4jConfig.global(secondConfig);

        assertSame(firstFacade, runtime.getJsonFacade());
        assertSame(secondFacade, Sjf4j.runtime().getJsonFacade());
    }

    @Test
    void testRuntimeBuilderCanExposeConfigSnapshot() {
        Sjf4jRuntime.Builder builder = Sjf4jRuntime.builder()
                .instantFormat(Sjf4jConfig.InstantFormat.EPOCH_MILLIS)
                .bindingPath(false);

        Sjf4jConfig config = builder.buildConfig();
        Sjf4jRuntime runtime = Sjf4j.runtime(config);

        assertEquals(Sjf4jConfig.InstantFormat.EPOCH_MILLIS, runtime.config().instantFormat);
        assertFalse(runtime.config().bindingPath);
    }

    @Test
    void testRuntimeCachedPathUsesSnapshotCache() {
        AtomicInteger compileCount = new AtomicInteger();
        PathCache pathCache = new PathCache() {
            private final Map<String, JsonPath> cache = new HashMap<>();

            @Override
            public JsonPath getOrCompile(String expr, java.util.function.Function<String, JsonPath> compiler) {
                JsonPath cached = cache.get(expr);
                if (cached != null) {
                    return cached;
                }
                compileCount.incrementAndGet();
                JsonPath compiled = compiler.apply(expr);
                cache.put(expr, compiled);
                return compiled;
            }
        };
        Sjf4jRuntime runtime = Sjf4jRuntime.builder()
                .pathCache(pathCache)
                .build();

        JsonPath p1 = runtime.cachedPath("$.a.b[0].c");
        JsonPath p2 = runtime.cachedPath("$.a.b[0].c");

        assertEquals("$.a.b[0].c", p1.toExpr());
        assertSame(p1, p2);
        assertEquals(1, compileCount.get());
    }
}
