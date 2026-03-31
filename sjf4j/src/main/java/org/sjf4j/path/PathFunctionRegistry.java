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
public class PathFunctionRegistry {

    /**
     * Function signature for JSONPath functions.
     * Args are raw JSON nodes or literal values.
     */
    @FunctionalInterface
    public interface PathFunction {
        Object apply(Object[] args);
    }

    public static class FunctionDescriptor {
        private final String name;
        private final PathFunction func;

        /**
         * Creates a function descriptor with name and implementation.
         */
        public FunctionDescriptor(String name, PathFunction func) {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty()) throw new JsonException("Name must not be empty");
            Objects.requireNonNull(func, "func");
            this.name = name;
            this.func = func;
        }

        /**
         * Invokes the function with raw argument values.
         */
        public Object invoke(Object[] args) {
            return func.apply(args);
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
    public static Object invoke(String name, Object[] args) {
        FunctionDescriptor fd = get(name);
        if (fd == null) throw new JsonException("Function '" + name + "' not exist");
        try {
            return fd.invoke(args);
        } catch (Exception e) {
            throw new JsonException("Function '" + name + "' invoke error", e);
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
        PathFunctionRegistry.register(new FunctionDescriptor("length", args -> {
            if (args.length != 1) 
                throw new JsonException("length(): expects exactly 1 argument, but got: " + args.length);
            Object node = args[0];
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
        PathFunctionRegistry.register(new FunctionDescriptor("count", args -> {
            if (args.length != 1) 
                throw new JsonException("count(): expects exactly 1 argument, but got: " + args.length);
            Object node = args[0];
            switch (JsonType.of(node)) {
                case ARRAY:
                    return Nodes.sizeInArray(node);
            }
            return null;
        }));

        // match
        // follows RFC i-regexp semantics and does not support full regular expressions.
        PathFunctionRegistry.register(new FunctionDescriptor("match", args -> {
            if (args.length != 2)
                throw new JsonException("match(): expects exactly 2 arguments, but got: " + args.length);
            Object node = args[0];
            String pattern;
            try {
                pattern = Nodes.toString(args[1]);
            } catch (Exception e) {
                throw new JsonException("match(): the second argument must be a String but was " +
                        Types.name(args[1]), e);
            }
            if (pattern == null) {
                throw new JsonException("match(): the second argument must be a String but was null");
            }
            switch (JsonType.of(node)) {
                case STRING:
                    return IRegexpUtil.match(pattern, Nodes.toString(node));
            }
            return false;
        }));

        // search
        PathFunctionRegistry.register(new FunctionDescriptor("search", args -> {
            if (args.length != 2)
                throw new JsonException("search(): expects exactly 2 arguments, but got: " + args.length);
            Object node = args[0];
            String pattern;
            try {
                pattern = Nodes.toString(args[1]);
            } catch (Exception e) {
                throw new JsonException("search(): the second argument must be a String but was " +
                        Types.name(args[1]), e);
            }
            if (pattern == null) {
                throw new JsonException("search(): the second argument must be a String but was null");
            }
            switch (JsonType.of(node)) {
                case STRING:
                    return IRegexpUtil.search(pattern, Nodes.toString(node));
            }
            return false;
        }));

        // value
        PathFunctionRegistry.register(new FunctionDescriptor("value", args -> {
            if (args.length != 1)
                throw new JsonException("value(): expects exactly 1 arguments, but got: " + args.length);
            return args[0];
        }));

        // sum
        PathFunctionRegistry.register(new FunctionDescriptor("sum", args -> {
            if (args.length != 1)
                throw new JsonException("sum(): expects exactly 1 arguments, but got: " + args.length);
            Object node = args[0];
            double[] sum = new double[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.visitArray(node, (i, v) -> {
                        Double d = Nodes.toDouble(v);
                        if (d != null) sum[0] += d;
                    });
            }
            return sum[0];
        }));

        // min
        PathFunctionRegistry.register(new FunctionDescriptor("min", args -> {
            if (args.length != 1)
                throw new JsonException("min(): expects exactly 1 arguments, but got: " + args.length);
            Object node = args[0];
            Double[] min = new Double[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
                        Double d = Nodes.toDouble(n);
                        if (d != null) {
                            if (min[0] == null || min[0] > d) min[0] = d;
                        }
                    });
            }
            return min[0];
        }));

        // max
        PathFunctionRegistry.register(new FunctionDescriptor("max", args -> {
            if (args.length != 1)
                throw new JsonException("max(): expects exactly 1 arguments, but got: " + args.length);
            Object node = args[0];
            Double[] max = new Double[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
                        Double d = Nodes.toDouble(n);
                        if (d != null) {
                            if (max[0] == null || max[0] < d) max[0] = d;
                        }
                    });
            }
            return max[0];
        }));

        // avg
        PathFunctionRegistry.register(new FunctionDescriptor("avg", args -> {
            if (args.length != 1)
                throw new JsonException("avg(): expects exactly 1 arguments, but got: " + args.length);
            Object node = args[0];
            double[] sum = new double[1];
            int[] cnt = new int[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
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
        PathFunctionRegistry.register(new FunctionDescriptor("stddev", args -> {
            if (args.length != 1)
                throw new JsonException("stddev(): expects exactly 1 arguments, but got: " + args.length);
            Object node = args[0];
            double[] sum = new double[1];
            int[] cnt = new int[1];
            switch (JsonType.of(node)) {
                case ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
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
                    Nodes.visitArray(node, (i, n) -> {
                        Double d = Nodes.toDouble(n);
                        if (d != null) {
                            qe[0] += (d - avg) * (d - avg);
                        }
                    });
            }
            return qe[0] / cnt[0];
        }));

        // first
        PathFunctionRegistry.register(new FunctionDescriptor("first", args -> {
            if (args.length != 1)
                throw new JsonException("first(): expects exactly 1 arguments, but got: " + args.length);
            Object node = args[0];
            switch (JsonType.of(node)) {
                case ARRAY:
                    if (Nodes.sizeInArray(node) > 0) {
                        return Nodes.getInArray(node, 0);
                    }
            }
            return null;
        }));

        // last
        PathFunctionRegistry.register(new FunctionDescriptor("last", args -> {
            if (args.length != 1)
                throw new JsonException("last(): expects exactly 1 arguments, but got: " + args.length);
            Object node = args[0];
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
        PathFunctionRegistry.register(new FunctionDescriptor("index", args -> {
            if (args.length != 2)
                throw new JsonException("index(): expects exactly 2 arguments, but got: " + args.length);
            Object node = args[0];
            if (!JsonType.of(args[1]).isNumber()) {
                throw new JsonException("index(): the second argument must be a number but was " + Types.name(args[1]));
            }
            int idx = Nodes.toInt(args[1]);

            switch (JsonType.of(node)) {
                case ARRAY:
                    return Nodes.getInArray(node, idx);
            }
            return null;
        }));

    }

}
