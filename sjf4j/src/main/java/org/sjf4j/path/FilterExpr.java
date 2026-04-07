package org.sjf4j.path;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeKind;
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
        private final JsonPath path;

        /**
         * Creates a path expression from a JSONPath string.
         */
        public PathExpr(String path) {
            this.path = JsonPath.compile(path);
        }

        /**
         * Evaluates this path against root or current context.
         */
        @Override
        public Object eval(Object rootNode, Object currentNode) {
            if (path.head() instanceof PathSegment.Current) {
                return path.eval(currentNode);
            } else {
                return path.eval(rootNode);
            }
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
            switch (op) {
                case EQ: return eq(a, b);
                case NE: return !eq(a, b);
                case GT: return gt(a, b);
                case GE: return ge(a, b);
                case LT: return lt(a, b);
                case LE: return le(a, b);
                case MATCH: return match(a, b);
            }
            return false;
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
         * Evaluates function with evaluated argument values.
         */
        @Override
        public Object eval(Object rootNode, Object currentNode) {
            Object[] values = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                values[i] = args.get(i).eval(rootNode, currentNode);
            }
            return PathFunctionRegistry.invoke(name, values);
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
        LT("<"), LE("<="), AND("&&"), OR("||"), MATCH("=~");;

        private final String symbol;
        Op(String symbol) { this.symbol = symbol; }
        @Override public String toString() { return symbol; }
    }

    /**
     * Returns true when two values are node-equivalent.
     */
    static boolean eq(Object a, Object b) {
        return Nodes.equals(a, b);
    }

    /**
     * Returns true when a is greater than b.
     */
    static boolean gt(Object a, Object b) {
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
     * Converts a value to boolean using JSONPath truthiness rules.
     *
     * <p>Falsy values are: null, false, numeric zero, empty string,
     * and empty array. Objects are treated as truthy.</p>
     */
    static boolean truth(Object x) {
        if (x == null) return false;
        JsonType xjt = JsonType.of(x);
        if (xjt.isBoolean()) return Boolean.TRUE.equals(Nodes.toBoolean(x));
        if (xjt.isNumber()) return Nodes.toDouble(x) != 0;
        if (xjt.isString()) return !Nodes.toString(x).isEmpty();
        if (xjt.isArray()) return Nodes.sizeInArray(x) > 0;
        return true;
    }

}
