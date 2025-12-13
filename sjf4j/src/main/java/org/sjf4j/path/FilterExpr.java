package org.sjf4j.path;

import org.sjf4j.JsonContainer;
import org.sjf4j.JsonException;
import org.sjf4j.util.ContainerUtil;

import java.util.ArrayList;
import java.util.List;

public interface FilterExpr {

    Object eval(Object rootNode, Object currentNode);

    default boolean evalTruth(Object rootNode, Object currentNode) {
        Object v = eval(rootNode, currentNode);
        return truth(v);
    }

    class LiteralExpr implements FilterExpr {
        private final Object value;

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

    class PathExpr implements FilterExpr {
        private final JsonPath path;

        public PathExpr(String path) {
            this.path = JsonPath.compile(path);
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) {
            if (path.head() instanceof PathToken.Current) {
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

    class UnaryExpr implements FilterExpr {
        private final boolean truth;
        private final FilterExpr unary;

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

    class BinaryExpr implements FilterExpr {
        private final FilterExpr left;
        private final FilterExpr right;
        private final Op op;

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
            }
            return false;
        }

        @Override
        public String toString() {
            return "(" + left + " " + op + " " + right + ")";
        }

    }

    class FunctionExpr implements FilterExpr {
        final String name;
        final List<FilterExpr> args;

        public FunctionExpr(String name, List<FilterExpr> args) {
            this.name = name;
            this.args = args;
        }

        @Override
        public Object eval(Object rootNode, Object currentNode) {
            List<Object> values = new ArrayList<>(args.size());
            for (FilterExpr arg : args) {
                values.add(arg.eval(rootNode, currentNode));
            }
            return FunctionRegistry.invoke(name, values);
        }
    }

    /// Default

    // Operation: ==, !=, >, <, >=, <=, &&, ||
    enum Op {
        EQ("=="), NE("!="), GT(">"), GE(">="),
        LT("<"), LE("<="), AND("&&"), OR("||");

        private final String symbol;
        Op(String symbol) { this.symbol = symbol; }
        @Override public String toString() { return symbol; }
    }

    static boolean eq(Object a, Object b) {
        return ContainerUtil.equals(a, b);
//        if (a == b) return true;
//        if (a == null || b == null) return false;
//        if (a instanceof Number && b instanceof Number)
//            return ((Number)a).doubleValue() == ((Number)b).doubleValue();
//        return a.equals(b);
    }

    static boolean gt(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) > 0;
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b) > 0;
        }
        return false;
    }

    static boolean ge(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) >= 0;
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b) >= 0;
        }
        return false;
    }

    static boolean lt(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) < 0;
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b) < 0;
        }
        return false;
    }

    static boolean le(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) <= 0;
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b) <= 0;
        }
        return false;
    }

    static boolean truth(Object x) {
        if (x == null) return false;
        if (x instanceof Boolean) return (Boolean) x;
        if (x instanceof Number) return ((Number) x).doubleValue() != 0;
        if (x instanceof String) return !((String) x).isEmpty();
        if (x instanceof List) return !((List<?>) x).isEmpty();
        return true;
    }

}
