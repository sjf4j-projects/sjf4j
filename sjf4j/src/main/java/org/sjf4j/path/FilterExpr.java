package org.sjf4j.path;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Numbers;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Interface representing a filter expression for JSON Path queries.
 * <p>
 * Filter expressions are used to evaluate conditions on JSON nodes during path traversal,
 * enabling conditional filtering of JSON elements based on dynamic evaluations.
 */
public interface FilterExpr {

    /**
     * Evaluates this expression against the given root and current nodes.
     * 
     * @param rootNode the root JSON node for absolute path evaluations
     * @param currentNode the current JSON node being evaluated
     * @return the result of the expression evaluation
     */
    Object eval(Object rootNode, Object currentNode);

    /**
     * Evaluates this expression and returns its boolean truth value.
     * <p>
     * This default method evaluates the expression and then converts the result
     * to a boolean using the {@link #truth(Object)} method.
     * 
     * @param rootNode the root JSON node for absolute path evaluations
     * @param currentNode the current JSON node being evaluated
     * @return the boolean truth value of the expression result
     */
    default boolean evalTruth(Object rootNode, Object currentNode) {
        Object v = eval(rootNode, currentNode);
        return truth(v);
    }

    /// Implements: LiteralExpr, PathExpr, UnaryExpr, FunctionExpr

    /**
     * A filter expression representing a literal value (constant).
     * <p>
     * Literal expressions evaluate to their fixed value regardless of the context nodes.
     */
    class LiteralExpr implements FilterExpr {
        private final Object value;

        /**
         * Creates a new literal expression with the given value.
         * 
         * @param value the literal value to represent
         */
        public LiteralExpr(Object value) {
            this.value = value;
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) { return value; }

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
     * A filter expression representing a JSON Path that evaluates against the context nodes.
     * <p>
     * Path expressions can evaluate either relative to the current node (for paths starting with @)
     * or absolute to the root node (for paths starting with $).
     */
    class PathExpr implements FilterExpr {
        private final JsonPath path;

        /**
         * Creates a new path expression from the given JSON Path string.
         * 
         * @param path the JSON Path string to compile
         */
        public PathExpr(String path) {
            this.path = JsonPath.compile(path);
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) {
            if (path.head() instanceof PathSegment.Current) {
                return path.eval(currentNode);
            } else {
                return path.eval(rootNode);
            }
        }

        @Override
        public String toString() {
            return path.toString();
        }
    }

    /**
     * A filter expression representing a unary boolean operation.
     * <p>
     * Used for negation operations where the truth value of another expression is inverted.
     */
    class UnaryExpr implements FilterExpr {
        private final boolean truth;
        private final FilterExpr unary;

        /**
         * Creates a new unary expression.
         * 
         * @param truth if true, the expression's truth value is preserved; if false, it's inverted
         * @param unary the expression to apply the unary operation to
         */
        public UnaryExpr(boolean truth, FilterExpr unary) {
            this.truth = truth;
            this.unary = unary;
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) {
            Object v = unary.eval(rootNode, currentNode);
            return truth == truth(v);
        }

        @Override
        public String toString() {
            if (truth) return unary.toString();
            else return "!" + unary.toString();
        }
    }

    /**
     * A filter expression representing a binary operation.
     * <p>
     * Binary expressions combine two sub-expressions using an operator, including:
     * <ul>
     *   <li>Comparison operators: ==, !=, &gt;, &lt;, &gt;=, &lt;=</li>
     *   <li>Logical operators: &amp;&amp;, ||</li>
     * </ul>
     */
    class BinaryExpr implements FilterExpr {
        private final FilterExpr left;
        private final FilterExpr right;
        private final Op op;

        /**
         * Creates a new binary expression.
         * 
         * @param l the left-hand side expression
         * @param r the right-hand side expression
         * @param o the operator to apply
         */
        public BinaryExpr(FilterExpr l, FilterExpr r, Op o) {
            this.left = l;
            this.right = r;
            this.op = o;
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) {
            Object a = left.eval(rootNode, currentNode);
            Object b = right.eval(rootNode, currentNode);
            switch (op) {
                case EQ: return eq(a, b);
                case NE: return !eq(a, b);
                case GT: return gt(a, b);
                case GE: return ge(a, b);
                case LT: return lt(a, b);
                case LE: return le(a, b);
                case AND: return truth(a) && truth(b);
                case OR:  return truth(a) || truth(b);
                case MATCH: return match(a, b);
            }
            return false;
        }

        @Override
        public String toString() {
            return "(" + left + " " + op + " " + right + ")";
        }

    }

    /**
     * A filter expression representing a function call.
     * <p>
     * Function expressions invoke registered functions with the given arguments,
     * allowing for custom operations in filter expressions.
     */
    class FunctionExpr implements FilterExpr {
        final String name;
        final List<FilterExpr> args;

        /**
         * Creates a new function expression.
         * 
         * @param name the name of the function to call
         * @param args the arguments to pass to the function
         */
        public FunctionExpr(String name, List<FilterExpr> args) {
            this.name = name;
            this.args = args;
        }

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

        public RegexExpr(String source, Pattern pattern) {
            this.source = source;
            this.pattern = pattern;
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) {
            return pattern;
        }

        @Override
        public String toString() {
            return source;
        }

    }

    /// Default

    /**
     * Enum defining all supported binary operators for filter expressions.
     * <p>
     * Operators are categorized into comparison operators and logical operators.
     */
    enum Op {
        EQ("=="), NE("!="), GT(">"), GE(">="),
        LT("<"), LE("<="), AND("&&"), OR("||"), MATCH("=~");;

        private final String symbol;
        Op(String symbol) { this.symbol = symbol; }
        @Override public String toString() { return symbol; }
    }

    /**
     * Compares two objects for equality according to JSON comparison rules.
     * <p>
     * Numbers are compared by their numeric value, not their type. All other types
     * are compared using their natural equality semantics.
     * 
     * @param a the first object to compare
     * @param b the second object to compare
     * @return true if the objects are equal according to JSON rules
     */
    static boolean eq(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /**
     * Compares if the first object is greater than the second according to JSON rules.
     * <p>
     * Numbers are compared numerically, strings lexicographically. Returns false for other types.
     * 
     * @param a the first object to compare
     * @param b the second object to compare
     * @return true if a &gt; b according to JSON comparison rules
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
     * Compares if the first object is greater than or equal to the second according to JSON rules.
     * <p>
     * Numbers are compared numerically, strings lexicographically. Returns false for other types.
     * 
     * @param a the first object to compare
     * @param b the second object to compare
     * @return true if a &gt;= b according to JSON comparison rules
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
     * Compares if the first object is less than the second according to JSON rules.
     * <p>
     * Numbers are compared numerically, strings lexicographically. Returns false for other types.
     * 
     * @param a the first object to compare
     * @param b the second object to compare
     * @return true if a &lt; b according to JSON comparison rules
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
     * Compares if the first object is less than or equal to the second according to JSON rules.
     * <p>
     * Numbers are compared numerically, strings lexicographically. Returns false for other types.
     * 
     * @param a the first object to compare
     * @param b the second object to compare
     * @return true if a &lt;= b according to JSON comparison rules
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
     * Converts an object to its boolean truth value according to JSON rules.
     * <p>
     * The truthiness follows these rules:
     * <ul>
     *   <li>null → false</li>
     *   <li>false → false</li>
     *   <li>true → true</li>
     *   <li>0 → false (numeric zero)</li>
     *   <li>other numbers → true</li>
     *   <li>empty string → false</li>
     *   <li>other strings → true</li>
     *   <li>empty list → false</li>
     *   <li>other lists → true</li>
     *   <li>empty objects → true</li>
     *   <li>other objects → true</li>
     * </ul>
     * 
     * @param x the object to evaluate for truthiness
     * @return the boolean truth value according to JSON rules
     */
    static boolean truth(Object x) {
        if (x == null) return false;
        JsonType xjt = JsonType.of(x);
        if (xjt.isBoolean()) return (Boolean) x;
        if (xjt.isNumber()) return ((Number) x).doubleValue() != 0;
        if (xjt.isString()) return !Nodes.toString(x).isEmpty();
        if (xjt.isArray()) return Nodes.sizeInArray(x) > 0;
        return true;
    }

}
