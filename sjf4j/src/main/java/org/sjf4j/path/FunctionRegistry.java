package org.sjf4j.path;

import org.sjf4j.JsonException;
import org.sjf4j.JsonWalker;
import org.sjf4j.NodeType;
import org.sjf4j.util.IRegexpUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FunctionRegistry {

    public interface JsonFunction {
        Object apply(List<Object> args);
    }

    public static class FunctionDescriptor {
        private final String name;
        private final JsonFunction func;
        public FunctionDescriptor(String name, JsonFunction func) {
            if (name == null || name.isEmpty()) throw new JsonException("Name must not be empty");
            if (func == null) throw new JsonException("Func must not be null");
            this.name = name;
            this.func = func;
        }

        public Object invoke(List<Object> args) {
            return func.apply(args);
        }

        public String getName() { return name; }
    }

    /// Register

    private static final Map<String, FunctionDescriptor> FUNCTIONS = new ConcurrentHashMap<>();

    public static void register(FunctionDescriptor descriptor) {
        FUNCTIONS.put(descriptor.getName(), descriptor);
    }

    public static boolean exists(String name) {
        return FUNCTIONS.containsKey(name);
    }

    public static FunctionDescriptor get(String name) {
        return FUNCTIONS.get(name);
    }

    public static Object invoke(String name, List<Object> args) {
        FunctionDescriptor fd = get(name);
        if (fd == null) throw new JsonException("Function '" + name + "' not exist");
        try {
            return fd.invoke(args);
        } catch (Exception e) {
            throw new JsonException("Function '" + name + "' invoke error", e);
        }
    }

    public static Set<String> getFunctionNames() {
        return FUNCTIONS.keySet();
    }

    static {
        // length
        FunctionRegistry.register(new FunctionDescriptor("length", args -> {
            if (args.size() != 1) throw new JsonException("length(): expects exactly 1 argument, but got: " + args.size());
            Object node = args.get(0);
            switch (NodeType.of(node)) {
                case VALUE_STRING:
                    return ((String) node).length();
                case OBJECT_JSON_OBJECT:
                case OBJECT_MAP:
                case OBJECT_POJO:
                case OBJECT_JOJO:
                    return JsonWalker.sizeInObject(node);
                case ARRAY_ARRAY:
                case ARRAY_JSON_ARRAY:
                case ARRAY_LIST:
                    return JsonWalker.sizeInArray(node);
                default:
                    return null;
            }
        }));

        // count
        FunctionRegistry.register(new FunctionDescriptor("count", args -> {
            if (args.size() != 1) throw new JsonException("count(): expects exactly 1 argument, but got: " + args.size());
            Object node = args.get(0);
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_JSON_ARRAY:
                case ARRAY_LIST:
                    return JsonWalker.sizeInArray(node);
                default:
                    return null;
            }
        }));

        // match
        // follows RFC i-regexp semantics and does not support full regular expressions.
        FunctionRegistry.register(new FunctionDescriptor("match", args -> {
            if (args.size() != 2) throw new JsonException("match(): expects exactly 2 arguments, but got: " + args.size());
            Object node0 = args.get(0);
            Object node1 = args.get(1);
            if (node0 instanceof String && node1 instanceof String) {
                return IRegexpUtil.match((String) node1, (String) node0);
            }
            return false;
        }));

        // search
        FunctionRegistry.register(new FunctionDescriptor("search", args -> {
            if (args.size() != 2) throw new JsonException("search(): expects exactly 2 arguments, but got: " + args.size());
            Object node0 = args.get(0);
            Object node1 = args.get(1);
            if (node0 instanceof String && node1 instanceof String) {
                return IRegexpUtil.search((String) node1, (String) node0);
            }
            return false;
        }));

        // value
        FunctionRegistry.register(new FunctionDescriptor("value", args -> {
            if (args.size() != 1) throw new JsonException("value(): expects exactly 1 arguments, but got: " + args.size());
            return args.get(0);
        }));

        // sum
        FunctionRegistry.register(new FunctionDescriptor("sum", args -> {
            if (args.size() != 1) throw new JsonException("sum(): expects exactly 1 arguments, but got: " + args.size());
            Object node = args.get(0);
            AtomicReference<Double> sum = new AtomicReference<>((double) 0);
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    JsonWalker.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            sum.set(sum.get() + ((Number) n).doubleValue());
                        }
                    });
            }
            return sum.get();
        }));

        // min
        FunctionRegistry.register(new FunctionDescriptor("min", args -> {
            if (args.size() != 1) throw new JsonException("min(): expects exactly 1 arguments, but got: " + args.size());
            Object node = args.get(0);
            AtomicReference<Double> min = new AtomicReference<>();
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    JsonWalker.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            double d = ((Number) n).doubleValue();
                            if (min.get() == null || min.get() > d) min.set(d);
                        }
                    });
            }
            return min.get();
        }));

        // max
        FunctionRegistry.register(new FunctionDescriptor("max", args -> {
            if (args.size() != 1) throw new JsonException("max(): expects exactly 1 arguments, but got: " + args.size());
            Object node = args.get(0);
            AtomicReference<Double> max = new AtomicReference<>();
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    JsonWalker.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            double d = ((Number) n).doubleValue();
                            if (max.get() == null || max.get() < d) max.set(d);
                        }
                    });
            }
            return max.get();
        }));

        // avg
        FunctionRegistry.register(new FunctionDescriptor("avg", args -> {
            if (args.size() != 1) throw new JsonException("avg(): expects exactly 1 arguments, but got: " + args.size());
            Object node = args.get(0);
            AtomicReference<Double> sum = new AtomicReference<>((double) 0);
            AtomicInteger cnt = new AtomicInteger();
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    JsonWalker.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            sum.set(sum.get() + ((Number) n).doubleValue());
                            cnt.getAndIncrement();
                        }
                    });
            }
            return sum.get() / cnt.get();
        }));

        // stddev
        FunctionRegistry.register(new FunctionDescriptor("stddev", args -> {
            if (args.size() != 1) throw new JsonException("stddev(): expects exactly 1 arguments, but got: " + args.size());
            Object node = args.get(0);
            AtomicReference<Double> sum = new AtomicReference<>((double) 0);
            AtomicInteger cnt = new AtomicInteger();
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    JsonWalker.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            sum.set(sum.get() + ((Number) n).doubleValue());
                            cnt.getAndIncrement();
                        }
                    });
            }

            double avg = sum.get() / cnt.get();
            AtomicReference<Double> qe = new AtomicReference<>((double) 0);
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    JsonWalker.visitArray(node, (i, n) -> {
                        if (n instanceof Number) {
                            double d = ((Number) n).doubleValue();
                            qe.updateAndGet(v -> v + (d - avg) * (d - avg));
                        }
                    });
            }
            return qe.get() / cnt.get();
        }));

        // first
        FunctionRegistry.register(new FunctionDescriptor("first", args -> {
            if (args.size() != 1) throw new JsonException("first(): expects exactly 1 arguments, but got: " + args.size());
            Object node = args.get(0);
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    if (JsonWalker.sizeInArray(node) > 0) {
                        return JsonWalker.getInArray(node, 0);
                    }
            }
            return null;
        }));

        // last
        FunctionRegistry.register(new FunctionDescriptor("last", args -> {
            if (args.size() != 1) throw new JsonException("last(): expects exactly 1 arguments, but got: " + args.size());
            Object node = args.get(0);
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    int size = JsonWalker.sizeInArray(node);
                    if (size > 0) {
                        return JsonWalker.getInArray(node, size - 1);
                    }
            }
            return null;
        }));

        // index
        FunctionRegistry.register(new FunctionDescriptor("index", args -> {
            if (args.size() != 2) throw new JsonException("index(): expects exactly 2 arguments, but got: " + args.size());
            Object node = args.get(0);
            switch (NodeType.of(node)) {
                case ARRAY_ARRAY:
                case ARRAY_LIST:
                case ARRAY_JSON_ARRAY:
                    int size = JsonWalker.sizeInArray(node);
                    if (size > 0) {
                        int index = ((Number) args.get(1)).intValue();
                        index = index >= 0 ? index : size + index;
                        if (index >= 0 && index < size) return JsonWalker.getInArray(node, index);
                    }
            }
            return null;
        }));

    }


}
