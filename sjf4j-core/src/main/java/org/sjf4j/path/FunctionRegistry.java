package org.sjf4j.path;

import org.sjf4j.JsonType;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.exception.RegexRejectedException;
import org.sjf4j.regex.Regex;
import org.sjf4j.regex.RegexEngine;
import org.sjf4j.Nodes;
import org.sjf4j.util.Types;
import org.sjf4j.util.Asserts;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;


/**
 * Registry for JSONPath functions used by {@link JsonPath#eval}.
 *
 * <p>Built-in functions are registered in the static initializer and cover
 * common JSONPath operations (length, sum, match, etc.). Custom functions
 * can be added at runtime via {@link #register(FunctionDescriptor)}. A new
 * descriptor with the same name replaces the previous implementation globally.
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
            Asserts.notNull(name, "name");
            if (name.isEmpty()) throw new NodeException("function name must not be empty");
            Asserts.notNull(func, "func");
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
        public String getName() {
            return name;
        }
    }

    /*
     * --------------------------------------------------------------
     * Register
     * --------------------------------------------------------------
     */

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
     * @throws NodeException when function is missing or invocation fails
     */
    public static Object invoke(String name, Object target, Object[] args) {
        FunctionDescriptor fd = get(name);
        if (fd == null) throw new NodeException("function '" + name + "' does not exist");
        try {
            return fd.invoke(target, args);
        } catch (BindingException | RegexRejectedException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeException("function '" + name + "' invocation failed", e);
        }
    }

    /**
     * Returns all registered function names.
     */
    public static Set<String> getFunctionNames() {
        return FUNCTION_CACHE.keySet();
    }

    private static final FunctionDescriptor MATCH = new FunctionDescriptor("match",
            (target, args) -> regex("match", target, args, true));
    private static final FunctionDescriptor SEARCH = new FunctionDescriptor("search",
            (target, args) -> regex("search", target, args, false));

    // RFC 9535 sections 2.4.6 and 2.4.7: invalid I-Regexp patterns yield LogicalFalse.
    // Cache that result for invalid literals without a null check on the matching path.
    // RegexRejectedException is not a syntax error and must propagate.
    private static final Regex NEVER_MATCH = new Regex() {
        @Override public boolean matches(String input) { return false; }
        @Override public boolean find(String input) { return false; }
    };

    private static Regex compileRegex(RegexEngine engine, String expression) {
        try {
            return engine.create(expression);
        } catch (IllegalArgumentException invalidPattern) {
            // Invalid or unsupported expressions do not match; security rejections propagate.
            return NEVER_MATCH;
        }
    }

    private static boolean regex(String name, Object target, Object[] args, boolean match) {
        if (args.length != 1)
            throw new NodeException(name + "(): expected exactly 1 argument, but got " + args.length);
        return regex(name, target, args[0], RegexEngine.JDK_ENGINE, match);
    }

    private static boolean regex(String name, Object target, Object argument,
                                 RegexEngine engine, boolean match) {
        String pattern;
        try {
            pattern = Nodes.toString(argument);
        } catch (Exception e) {
            throw new NodeException(name + "(): expected a string pattern argument, but was " +
                    Types.name(argument), e);
        }
        if (pattern == null)
            throw new NodeException(name + "(): expected a string pattern argument, but was null");
        if (JsonType.of(target) != JsonType.STRING) return false;
        String text = Nodes.toString(target);
        if (text == null) return false;
        Regex regex = compileRegex(engine, pattern);
        return match ? regex.matches(text) : regex.find(text);
    }

    private static boolean regex(Object target, Regex compiled, boolean match) {
        if (JsonType.of(target) != JsonType.STRING) return false;
        String text = Nodes.toString(target);
        return text != null && (match ? compiled.matches(text) : compiled.find(text));
    }

    /**
     * Prepares built-in regex functions, or returns null for ordinary functions.
     * Constant calls retain only the compiled regex; dynamic calls retain only
     * the engine. Both still honor runtime registry replacement.
     */
    static BiFunction<Object, Object, Object> createRegexCall(String name, RegexEngine engine, Object literal) {
        final FunctionDescriptor builtin;
        if ("match".equals(name)) builtin = MATCH;
        else if ("search".equals(name)) builtin = SEARCH;
        else return null;

        boolean match = builtin == MATCH;
        if (literal instanceof String) {
            Regex compiled = compileRegex(engine, (String) literal);
            return (target, argument) -> {
                FunctionDescriptor descriptor = get(name);
                if (descriptor == null) throw new NodeException("function '" + name + "' does not exist");
                try {
                    if (descriptor == builtin) return regex(target, compiled, match);
                    return descriptor.invoke(target, new Object[]{argument});
                } catch (BindingException | RegexRejectedException e) {
                    throw e;
                } catch (Exception e) {
                    throw new NodeException("function '" + name + "' invocation failed", e);
                }
            };
        }

        return (target, argument) -> {
            FunctionDescriptor descriptor = get(name);
            if (descriptor == null) throw new NodeException("function '" + name + "' does not exist");
            try {
                if (descriptor == builtin) return regex(name, target, argument, engine, match);
                return descriptor.invoke(target, new Object[]{argument});
            } catch (BindingException | RegexRejectedException e) {
                throw e;
            } catch (Exception e) {
                throw new NodeException("function '" + name + "' invocation failed", e);
            }
        };
    }

    // Pre-register build-in functions
    static {
        // length
        FunctionRegistry.register(new FunctionDescriptor("length", (target, args) -> {
            if (args.length != 0)
                throw new NodeException("length(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            switch (JsonType.of(node)) {
                case STRING:
                    String text = Nodes.toString(node);
                    return text == null ? null : text.length();
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
                throw new NodeException("count(): expected exactly 0 arguments, but got " + args.length);
            Object node = target;
            switch (JsonType.of(node)) {
                case ARRAY:
                    return Nodes.sizeInArray(node);
            }
            return null;
        }));

        register(MATCH);
        register(SEARCH);

        // value
        FunctionRegistry.register(new FunctionDescriptor("value", (target, args) -> {
            if (args.length != 0)
                throw new NodeException("value(): expected exactly 0 arguments, but got " + args.length);
            return target;
        }));

        // sum
        FunctionRegistry.register(new FunctionDescriptor("sum", (target, args) -> {
            if (args.length != 0)
                throw new NodeException("sum(): expected exactly 0 arguments, but got " + args.length);
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
                throw new NodeException("min(): expected exactly 0 arguments, but got " + args.length);
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
                throw new NodeException("max(): expected exactly 0 arguments, but got " + args.length);
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
                throw new NodeException("avg(): expected exactly 0 arguments, but got " + args.length);
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
                throw new NodeException("stddev(): expected exactly 0 arguments, but got " + args.length);
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
                throw new NodeException("first(): expected exactly 0 arguments, but got " + args.length);
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
                throw new NodeException("last(): expected exactly 0 arguments, but got " + args.length);
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

    }

}
