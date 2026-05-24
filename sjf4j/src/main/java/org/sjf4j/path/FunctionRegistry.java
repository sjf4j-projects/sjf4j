package org.sjf4j.path;

import org.sjf4j.JsonType;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Types;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Registry for JSONPath functions used by {@link JsonPath#eval}.
 *
 * <p>Built-in functions are registered in the static initializer and cover
 * common JSONPath operations (length, sum, match, etc.). Custom functions
 * can be added at runtime via {@link #register}.
 */
public class FunctionRegistry {

    /**
     * Function signature for JSONPath functions.
     * Target is the function input value and args are additional literal or expression values.
     * Implementations must treat args as read-only because parsed paths may reuse the array.
     */
    @FunctionalInterface
    public interface PathFunction {
        Object apply(Object target, Object[] args);
    }

    public static class FunctionDescriptor {
        private final String name;
        private final PathFunction func;

        /**
         * Creates a function descriptor with name and implementation.
         */
        public FunctionDescriptor(String name, PathFunction func) {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty()) throw new JsonException("function name must not be empty");
            Objects.requireNonNull(func, "func");
            this.name = name;
            this.func = func;
        }

        /**
         * Invokes the function with target and additional raw argument values.
         * Implementations must not mutate args.
         */
        public Object invoke(Object target, Object[] args) {
            return func.apply(target, args);
        }

        /**
         * Returns the function name.
         */
        public String getName() { return name; }
    }

    /// Register

    private static final Map<String, FunctionDescriptor> FUNCTION_CACHE = new ConcurrentHashMap<>();

    /**
     * Registers or replaces a function descriptor by name.
     * <p>
     * Registration is global to the current JVM and is thread-safe.
     */
    public static void register(FunctionDescriptor descriptor) {
        FUNCTION_CACHE.put(descriptor.getName(), descriptor);
    }

    /**
     * Returns true if a function is registered.
     */
    public static boolean exists(String name) {
        return FUNCTION_CACHE.containsKey(name);
    }

    /**
     * Returns a function descriptor or null if absent.
     */
    public static FunctionDescriptor get(String name) {
        return FUNCTION_CACHE.get(name);
    }

    /**
     * Invokes a registered function by name.
     *
     * @throws JsonException when function is missing or invocation fails
     */
    public static Object invoke(String name, Object target, Object[] args) {
        FunctionDescriptor fd = get(name);
        if (fd == null) throw new JsonException("function '" + name + "' does not exist");
        try {
            return fd.invoke(target, args);
        } catch (Exception e) {
            throw new JsonException("function '" + name + "' invocation failed", e);
        }
    }

    /**
     * Returns all registered function names.
     */
    public static Set<String> getFunctionNames() {
        return FUNCTION_CACHE.keySet();
    }

    // Pre-register build-in functions
    static {
        // length
        FunctionRegistry.register(new FunctionDescriptor("length", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("length(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            switch (JsonType.of(node)) {
                case STRING:
                    return Nodes.toString(node).length();
                case OBJECT:
                    return Nodes.sizeInObject(node);
                case ARRAY:
                    return Nodes.sizeInArray(node);
            }
            return null;
        }));

        // count
        FunctionRegistry.register(new FunctionDescriptor("count", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("count(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            switch (JsonType.of(node)) {
                case ARRAY:
                    return Nodes.sizeInArray(node);
            }
            return null;
        }));

        // match
        // follows RFC i-regexp semantics and does not support full regular expressions.
        FunctionRegistry.register(new FunctionDescriptor("match", (target, args) -> {
            if (args.length != 1)
                throw new JsonException("match(): expected exactly 1 argument, but got " + args.length);
            Object node = target;
            String pattern;
            try {
                pattern = Nodes.toString(args[0]);
            } catch (Exception e) {
                throw new JsonException("match(): expected the second argument to be a string, but was " +
                        Types.name(args[0]), e);
            }
            if (pattern == null) {
                throw new JsonException("match(): expected the second argument to be a string, but was null");
            }
            switch (JsonType.of(node)) {
                case STRING:
                    return IRegexpUtil.match(pattern, Nodes.toString(node));
            }
            return false;
        }));

        // search
        FunctionRegistry.register(new FunctionDescriptor("search", (target, args) -> {
            if (args.length != 1)
                throw new JsonException("search(): expected exactly 1 argument, but got " + args.length);
            Object node = target;
            String pattern;
            try {
                pattern = Nodes.toString(args[0]);
            } catch (Exception e) {
                throw new JsonException("search(): expected the second argument to be a string, but was " +
                        Types.name(args[0]), e);
            }
            if (pattern == null) {
                throw new JsonException("search(): expected the second argument to be a string, but was null");
            }
            switch (JsonType.of(node)) {
                case STRING:
                    return IRegexpUtil.search(pattern, Nodes.toString(node));
            }
            return false;
        }));

        // value
        FunctionRegistry.register(new FunctionDescriptor("value", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("value(): expected exactly 0 arguments, but got " + args.length);
            return target;
        }));

        // sum
        FunctionRegistry.register(new FunctionDescriptor("sum", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("sum(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            double[] sum = new double[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.forEachArray(node, (i, v) -> {
                        Double d = Nodes.toDouble(v);
                        if (d != null) sum[0] += d;
                    });
            }
            return sum[0];
        }));

        // min
        FunctionRegistry.register(new FunctionDescriptor("min", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("min(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            Double[] min = new Double[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.forEachArray(node, (i, n) -> {
                        Double d = Nodes.toDouble(n);
                        if (d != null) {
                            if (min[0] == null || min[0] > d) min[0] = d;
                        }
                    });
            }
            return min[0];
        }));

        // max
        FunctionRegistry.register(new FunctionDescriptor("max", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("max(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            Double[] max = new Double[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.forEachArray(node, (i, n) -> {
                        Double d = Nodes.toDouble(n);
                        if (d != null) {
                            if (max[0] == null || max[0] < d) max[0] = d;
                        }
                    });
            }
            return max[0];
        }));

        // avg
        FunctionRegistry.register(new FunctionDescriptor("avg", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("avg(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            double[] sum = new double[1];
            int[] cnt = new int[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.forEachArray(node, (i, n) -> {
                        Double d = Nodes.toDouble(n);
                        if (d != null) {
                            sum[0] += d;
                            cnt[0]++;
                        }
                    });
            }

            return cnt[0] == 0 ? null : (sum[0] / cnt[0]);
        }));

        // stddev
        FunctionRegistry.register(new FunctionDescriptor("stddev", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("stddev(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            double[] sum = new double[1];
            int[] cnt = new int[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.forEachArray(node, (i, n) -> {
                        Double d = Nodes.toDouble(n);
                        if (d != null) {
                            sum[0] += d;
                            cnt[0]++;
                        }
                    });
            }

            if (cnt[0] == 0) return null;
            double avg = sum[0] / cnt[0];
            double[] qe = new double[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.forEachArray(node, (i, n) -> {
                        Double d = Nodes.toDouble(n);
                        if (d != null) {
                            qe[0] += (d - avg) * (d - avg);
                        }
                    });
            }
            return Math.sqrt(qe[0] / cnt[0]);
        }));

        // first
        FunctionRegistry.register(new FunctionDescriptor("first", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("first(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            switch (JsonType.of(node)) {
                case ARRAY:
                    if (Nodes.sizeInArray(node) > 0) {
                        return Nodes.getInArray(node, 0);
                    }
            }
            return null;
        }));

        // last
        FunctionRegistry.register(new FunctionDescriptor("last", (target, args) -> {
            if (args.length != 0)
                throw new JsonException("last(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            switch (JsonType.of(node)) {
                case ARRAY:
                    int size = Nodes.sizeInArray(node);
                    if (size > 0) {
                        return Nodes.getInArray(node, size - 1);
                    }
            }
            return null;
        }));

        // index
        FunctionRegistry.register(new FunctionDescriptor("index", (target, args) -> {
            if (args.length != 1)
                throw new JsonException("index(): expected exactly 1 argument, but got " + args.length);
            Object node = target;
            if (!JsonType.of(args[0]).isNumber()) {
                throw new JsonException("index(): expected the second argument to be a number, but was " + Types.name(args[0]));
            }
            int idx = Nodes.toInt(args[0]);

            switch (JsonType.of(node)) {
                case ARRAY:
                    return Nodes.getInArray(node, idx);
            }
            return null;
        }));

    }

}
