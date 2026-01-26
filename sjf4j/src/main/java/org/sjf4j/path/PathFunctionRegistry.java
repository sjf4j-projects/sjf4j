package org.sjf4j.path;

import org.sjf4j.JsonException;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.NodeType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class PathFunctionRegistry {

    @FunctionalInterface
    public interface PathFunction {
        Object apply(Object[] args);
    }

    public static class FunctionDescriptor {
        private final String name;
        private final PathFunction func;
        public FunctionDescriptor(String name, PathFunction func) {
            if (name == null || name.isEmpty()) throw new JsonException("Name must not be empty");
            if (func == null) throw new JsonException("Func must not be null");
            this.name = name;
            this.func = func;
        }

        public Object invoke(Object[] args) {
            return func.apply(args);
        }

        public String getName() { return name; }
    }

    /// Register

    private static final Map<String, FunctionDescriptor> FUNCTION_CACHE = new ConcurrentHashMap<>();

    public static void register(FunctionDescriptor descriptor) {
        FUNCTION_CACHE.put(descriptor.getName(), descriptor);
    }

    public static boolean exists(String name) {
        return FUNCTION_CACHE.containsKey(name);
    }

    public static FunctionDescriptor get(String name) {
        return FUNCTION_CACHE.get(name);
    }

    public static Object invoke(String name, Object[] args) {
        FunctionDescriptor fd = get(name);
        if (fd == null) throw new JsonException("Function '" + name + "' not exist");
        try {
            return fd.invoke(args);
        } catch (Exception e) {
            throw new JsonException("Function '" + name + "' invoke error", e);
        }
    }

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
            switch (NodeType.of(node)) {
                case VALUE_STRING:
                    return ((String) node).length();
                case OBJECT_MAP:
                case OBJECT_JSON_OBJECT:
                case OBJECT_JOJO:
                case OBJECT_POJO:
                    return Nodes.sizeInObject(node);
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    return Nodes.sizeInArray(node);
                default:
                    return null;
            }
        }));

        // count
        PathFunctionRegistry.register(new FunctionDescriptor("count", args -> {
            if (args.length != 1) 
                throw new JsonException("count(): expects exactly 1 argument, but got: " + args.length);
            Object node = args[0];
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    return Nodes.sizeInArray(node);
                default:
                    return null;
            }
        }));

        // match
        // follows RFC i-regexp semantics and does not support full regular expressions.
        PathFunctionRegistry.register(new FunctionDescriptor("match", args -> {
            if (args.length != 2)
                throw new JsonException("match(): expects exactly 2 arguments, but got: " + args.length);
            Object node0 = args[0];
            Object node1 = args[1];
            if (node0 instanceof String && node1 instanceof String) {
                return IRegexpUtil.match((String) node1, (String) node0);
            }
            return false;
        }));

        // search
        PathFunctionRegistry.register(new FunctionDescriptor("search", args -> {
            if (args.length != 2)
                throw new JsonException("search(): expects exactly 2 arguments, but got: " + args.length);
            Object node0 = args[0];
            Object node1 = args[1];
            if (node0 instanceof String && node1 instanceof String) {
                return IRegexpUtil.search((String) node1, (String) node0);
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
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            sum[0] += ((Number) n).doubleValue();
                        }
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
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            double d = ((Number) n).doubleValue();
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
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            double d = ((Number) n).doubleValue();
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
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            sum[0] += ((Number) n).doubleValue();
                            cnt[0]++;
                        }
                    });
            }
            return sum[0] / cnt[0];
        }));

        // stddev
        PathFunctionRegistry.register(new FunctionDescriptor("stddev", args -> {
            if (args.length != 1)
                throw new JsonException("stddev(): expects exactly 1 arguments, but got: " + args.length);
            Object node = args[0];
            double[] sum = new double[1];
            int[] cnt = new int[1];
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            sum[0] += ((Number) n).doubleValue();
                            cnt[0]++;
                        }
                    });
            }

            double avg = sum[0] / cnt[0];
            double[] qe = new double[1];
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    Nodes.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            double d = ((Number) n).doubleValue();
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
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
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
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
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
            switch (NodeType.of(node)) {
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                case ARRAY_JAJO:
                case ARRAY_ARRAY:
                    int size = Nodes.sizeInArray(node);
                    if (size > 0) {
                        int index = ((Number) args[1]).intValue();
                        index = index >= 0 ? index : size + index;
                        if (index >= 0 && index < size) return Nodes.getInArray(node, index);
                    }
            }
            return null;
        }));

    }

}
