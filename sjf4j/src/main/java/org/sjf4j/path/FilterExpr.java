package org.sjf4j.path;

import org.sjf4j.JsonType;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Numbers;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Filter expression AST used by JSONPath parsing.
 *
 * <p>Expressions are evaluated against a root node and a current node. The
 * result can be any value, and {@link #evalTruth} converts it to a boolean
 * using JSONPath truthiness rules.
 */
public interface FilterExpr {

    /**
     * Evaluates this expression against root and current nodes.
     */
    Object eval(Object rootNode, Object currentNode);

    /**
     * Evaluates this expression and converts the result to boolean.
     */
    default boolean evalTruth(Object rootNode, Object currentNode) {
        Object v = eval(rootNode, currentNode);
        return truth(v);
    }

    /// Implements: LiteralExpr, PathExpr, UnaryExpr, FunctionExpr

    /**
     * Filter expression for a constant literal value.
     */
    class LiteralExpr implements FilterExpr {
        private final Object value;

        /**
         * Creates a literal expression.
         */
        public LiteralExpr(Object value) {
            this.value = value;
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) { return value; }

        /**
         * Returns source-like literal rendering.
         */
        @Override
        public String toString() {
            if (value == null) return "null";
            else if (value instanceof String) return "\"" + value + "\"";
            else if (value instanceof Number) return "" + value;
            else if (value instanceof Boolean) return "" + value;
            else return value.toString();
        }
    }

    /**
     * Filter expression that evaluates a JSONPath.
     */
    class PathExpr implements FilterExpr {
        static final Object NOTHING = new Object();

        private final JsonPath path;

        /**
         * Creates a path expression from a JSONPath string.
         */
        public PathExpr(String path) {
            this.path = JsonPath.parse(path);
        }

        public PathExpr(JsonPath path) {
            this.path = Objects.requireNonNull(path, "path");
        }

        /**
         * Evaluates this path against root or current context.
         */
        @Override
        public Object eval(Object rootNode, Object currentNode) {
            Object container = path.rooted() ? rootNode : currentNode;
            if (!path.singleGet) return path.eval(container);
            Object value = path._findOne(container, 1, path.length());
            return value == JsonPath.MISSING ? NOTHING : value;
        }

        /**
         * Returns source-like path rendering.
         */
        @Override
        public String toString() {
            return path.toString();
        }
    }

    /**
     * Filter expression for unary boolean operations.
     */
    class UnaryExpr implements FilterExpr {
        private final boolean truth;
        private final FilterExpr unary;

        /**
         * Creates a unary expression.
         */
        public UnaryExpr(boolean truth, FilterExpr unary) {
            this.truth = truth;
            this.unary = unary;
        }

        /**
         * Evaluates unary truthiness operation.
         */
        @Override
        public Object eval(Object rootNode, Object currentNode) {
            Object v = unary.eval(rootNode, currentNode);
            return truth == truth(v);
        }

        /**
         * Returns source-like unary rendering.
         */
        @Override
        public String toString() {
            if (truth) return unary.toString();
            else return "!" + unary.toString();
        }
    }

    /**
     * Filter expression for binary comparison and logical operations.
     */
    class BinaryExpr implements FilterExpr {
        private final FilterExpr left;
        private final FilterExpr right;
        private final Op op;

        /**
         * Creates a binary expression.
         */
        public BinaryExpr(FilterExpr l, FilterExpr r, Op o) {
            this.left = l;
            this.right = r;
            this.op = o;
            PathExpr nonSingular = l instanceof PathExpr && !((PathExpr) l).path.singleGet ? (PathExpr) l :
                    r instanceof PathExpr && !((PathExpr) r).path.singleGet ? (PathExpr) r : null;
            if (o != Op.AND && o != Op.OR && nonSingular != null && !(nonSingular.path.tail() instanceof PathSegment.Function)) {
                throw new JsonException("filter comparison requires a singular path, but path '" + nonSingular.path + "' is non-singular");
            }
        }

        /**
         * Evaluates binary operator over both operands.
         */
        @Override
        public Object eval(Object rootNode, Object currentNode) {
            Object a = left.eval(rootNode, currentNode);
            switch (op) {
                case AND:
                    return truth(a) && truth(right.eval(rootNode, currentNode));
                case OR:
                    return truth(a) || truth(right.eval(rootNode, currentNode));
            }

            Object b = right.eval(rootNode, currentNode);
            _requireScalarFunctionResult(left, a);
            _requireScalarFunctionResult(right, b);
            if (a == PathExpr.NOTHING || b == PathExpr.NOTHING) return false;
            switch (op) {
                case EQ: return eq(a, b);
                case NE: return !eq(a, b);
                case GT: return gt(a, b);
                case GE: return ge(a, b);
                case LT: return lt(a, b);
                case LE: return le(a, b);
                case MATCH: return match(a, b);
                case IN: return in(a, b);
                case NIN: return !in(a, b);
            }
            return false;
        }

        private static void _requireScalarFunctionResult(FilterExpr expr, Object value) {
            if (expr instanceof PathExpr) {
                PathExpr pathExpr = (PathExpr) expr;
                if (!pathExpr.path.singleGet && pathExpr.path.tail() instanceof PathSegment.Function) {
                    JsonType type = JsonType.of(value);
                    if (type.isArray() || type.isObject()) {
                        throw new JsonException("filter comparison path '" + pathExpr.path + "' function returned " + type + "; comparison requires scalar output");
                    }
                }
            }
        }

        /**
         * Returns source-like binary rendering.
         */
        @Override
        public String toString() {
            return "(" + left + " " + op + " " + right + ")";
        }

    }

    /**
     * Filter expression for a function call.
     */
    class FunctionExpr implements FilterExpr {
        private static final Object[] NO_ARGS = new Object[0];

        final String name;
        final List<FilterExpr> args;

        /**
         * Creates a function expression.
         */
        public FunctionExpr(String name, List<FilterExpr> args) {
            this.name = name;
            this.args = args;
        }

        /**
         * Evaluates function with the first evaluated argument as target and remaining values as args.
         */
        @Override
        public Object eval(Object rootNode, Object currentNode) {
            int size = args.size();
            if (size == 0) throw new JsonException("function '" + name + "' requires a target argument");
            Object target = args.get(0).eval(rootNode, currentNode);
            Object[] values = size <= 1 ? NO_ARGS : new Object[size - 1];
            for (int i = 1; i < size; i++) {
                values[i - 1] = args.get(i).eval(rootNode, currentNode);
            }
            return FunctionRegistry.invoke(name, target, values);
        }
    }

    /**
     * Filter expression for an array literal.
     */
    class ArrayExpr implements FilterExpr {
        final List<FilterExpr> elements;
        private final List<Object> literalValues;

        public ArrayExpr(List<FilterExpr> elements) {
            this.elements = elements;
            List<Object> values = new java.util.ArrayList<>(elements.size());
            for (int i = 0, size = elements.size(); i < size; i++) {
                FilterExpr element = elements.get(i);
                if (!(element instanceof LiteralExpr)) {
                    values = null;
                    break;
                }
                values.add(element.eval(null, null));
            }
            this.literalValues = values == null ? null : java.util.Collections.unmodifiableList(values);
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) {
            if (literalValues != null) return literalValues;
            int size = elements.size();
            java.util.ArrayList<Object> values = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                values.add(elements.get(i).eval(rootNode, currentNode));
            }
            return values;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0, size = elements.size(); i < size; i++) {
                if (i > 0) sb.append(',').append(' ');
                sb.append(elements.get(i));
            }
            sb.append(']');
            return sb.toString();
        }
    }

    class RegexExpr implements FilterExpr {
        private final String source;
        private final Pattern pattern;

        /**
         * Creates a regex literal expression.
         */
        public RegexExpr(String source, Pattern pattern) {
            this.source = source;
            this.pattern = pattern;
        }

        /**
         * Evaluates to compiled regex pattern.
         */
        @Override
        public Object eval(Object rootNode, Object currentNode) {
            return pattern;
        }

        /**
         * Returns original regex literal source.
         */
        @Override
        public String toString() {
            return source;
        }

    }

    /// Default

    /**
     * Supported binary operators for filter expressions.
     */
    enum Op {
        EQ("=="), NE("!="), GT(">"), GE(">="),
        LT("<"), LE("<="), AND("&&"), OR("||"), MATCH("=~"), IN("in"), NIN("nin");

        private final String symbol;
        Op(String symbol) { this.symbol = symbol; }
        @Override public String toString() { return symbol; }
    }

    /**
     * Returns true when two values are node-equivalent.
     */
    static boolean eq(Object a, Object b) {
        if (a == PathExpr.NOTHING || b == PathExpr.NOTHING) return false;
        return Nodes.equals(a, b);
    }

    /**
     * Returns true when a is greater than b.
     */
    static boolean gt(Object a, Object b) {
        if (a == PathExpr.NOTHING || b == PathExpr.NOTHING) return false;
        JsonType ajt = JsonType.of(a);
        JsonType bjt = JsonType.of(b);
        if (ajt.isNumber() && bjt.isNumber()) {
            return Numbers.compare(Nodes.toNumber(a), Nodes.toNumber(b)) > 0;
        }
        if (ajt.isString() && bjt.isString()) {
            return Nodes.toString(a).compareTo(Nodes.toString(b)) > 0;
        }
        return false;
    }

    /**
     * Returns true when a is greater than or equal to b.
     */
    static boolean ge(Object a, Object b) {
        if (a == PathExpr.NOTHING || b == PathExpr.NOTHING) return false;
        JsonType ajt = JsonType.of(a);
        JsonType bjt = JsonType.of(b);
        if (ajt.isNumber() && bjt.isNumber()) {
            return Numbers.compare(Nodes.toNumber(a), Nodes.toNumber(b)) >= 0;
        }
        if (ajt.isString() && bjt.isString()) {
            return Nodes.toString(a).compareTo(Nodes.toString(b)) >= 0;
        }
        return false;
    }

    /**
     * Returns true when a is less than b.
     */
    static boolean lt(Object a, Object b) {
        if (a == PathExpr.NOTHING || b == PathExpr.NOTHING) return false;
        JsonType ajt = JsonType.of(a);
        JsonType bjt = JsonType.of(b);
        if (ajt.isNumber() && bjt.isNumber()) {
            return Numbers.compare(Nodes.toNumber(a), Nodes.toNumber(b)) < 0;
        }
        if (ajt.isString() && bjt.isString()) {
            return Nodes.toString(a).compareTo(Nodes.toString(b)) < 0;
        }
        return false;
    }

    /**
     * Returns true when a is less than or equal to b.
     */
    static boolean le(Object a, Object b) {
        if (a == PathExpr.NOTHING || b == PathExpr.NOTHING) return false;
        JsonType ajt = JsonType.of(a);
        JsonType bjt = JsonType.of(b);
        if (ajt.isNumber() && bjt.isNumber()) {
            return Numbers.compare(Nodes.toNumber(a), Nodes.toNumber(b)) <= 0;
        }
        if (ajt.isString() && bjt.isString()) {
            return Nodes.toString(a).compareTo(Nodes.toString(b)) <= 0;
        }
        return false;
    }

    /**
     * Returns true when value a matches regex b.
     */
    static boolean match(Object a, Object b) {
        if (a == PathExpr.NOTHING || b == PathExpr.NOTHING) return false;
        if (!(b instanceof Pattern)) return false;
        if (a == null) return false;
        Pattern p = (Pattern) b;

        JsonType ajt = JsonType.of(a);
        if (ajt.isString()) {
            return p.matcher(Nodes.toString(a)).find();
        }

        // Matches if at least one element in the array matches
        if (ajt.isArray()) {
            return Nodes.anyMatchInArray(a,
                    (i, v) -> JsonType.of(v).isString() && p.matcher(Nodes.toString(v)).find());
        }

        return false;
    }

    /**
     * Returns true when array-like b contains a.
     */
    static boolean in(Object a, Object b) {
        if (a == PathExpr.NOTHING || b == PathExpr.NOTHING) return false;
        if (!JsonType.of(b).isArray()) return false;
        return Nodes.anyMatchInArray(b, (i, v) -> eq(a, v));
    }

    /**
     * Converts a value to boolean using JSONPath truthiness rules.
     *
     * <p>Falsy values are: null, false, numeric zero, empty string,
     * and empty array. Objects are treated as truthy.</p>
     */
    static boolean truth(Object x) {
        if (x == null || x == PathExpr.NOTHING) return false;
        JsonType xjt = JsonType.of(x);
        if (xjt.isBoolean()) return Boolean.TRUE.equals(Nodes.toBoolean(x));
        if (xjt.isNumber()) return Nodes.toDouble(x) != 0;
        if (xjt.isString()) return !Nodes.toString(x).isEmpty();
        if (xjt.isArray()) return Nodes.sizeInArray(x) > 0;
        return true;
    }

}
