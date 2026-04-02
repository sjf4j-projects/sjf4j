package org.sjf4j.path;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cache strategy used by {@link JsonPath#compileCached(String)}.
 */
@FunctionalInterface
public interface PathCache {

    /**
     * Returns cached path for expression or computes and stores it.
     */
    JsonPath getOrCompile(String expr, Function<String, JsonPath> compiler);


    /// Built-in PathCache

    PathCache ConcurrentMapPathCache = new PathCache() {
        private final Map<String, JsonPath> cache = new ConcurrentHashMap<>();

        @Override
        public JsonPath getOrCompile(String expr, Function<String, JsonPath> compiler) {
            Objects.requireNonNull(expr, "expr");
            Objects.requireNonNull(compiler, "compiler");
            return cache.computeIfAbsent(expr, compiler);
        }
    };

}
