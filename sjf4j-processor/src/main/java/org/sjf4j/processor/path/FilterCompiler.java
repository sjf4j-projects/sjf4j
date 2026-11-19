package org.sjf4j.processor.path;

import org.sjf4j.path.FilterExpr;
import org.sjf4j.path.PathSegment;

import java.util.ArrayList;
import java.util.List;

/** Lowers the supported subset of FilterExpr into an immutable generation plan. */
final class FilterCompiler {
    FilterPlan compile(FilterExpr expression) {
        if (expression instanceof FilterExpr.LiteralExpr) {
            Object value = ((FilterExpr.LiteralExpr) expression).value();
            if (value == null || value instanceof String || value instanceof Boolean
                    || value instanceof Double) {
                return FilterPlan.literal(value);
            }
            throw unsupported("literal type " + value.getClass().getName());
        }
        if (expression instanceof FilterExpr.PathExpr) {
            PathSegment[] segments = ((FilterExpr.PathExpr) expression).path().segments();
            if (segments.length == 0) throw unsupported("empty path");
            boolean rooted = segments[0] instanceof PathSegment.Root;
            if (!rooted && !(segments[0] instanceof PathSegment.Current)) {
                throw unsupported("path root " + segments[0]);
            }
            List<String> names = new ArrayList<>();
            for (int i = 1; i < segments.length; i++) {
                if (!(segments[i] instanceof PathSegment.Name)) {
                    throw unsupported("path selector " + segments[i]);
                }
                names.add(((PathSegment.Name) segments[i]).name);
            }
            return FilterPlan.path(rooted, names.toArray(new String[0]));
        }
        if (expression instanceof FilterExpr.UnaryExpr) {
            FilterExpr.UnaryExpr unary = (FilterExpr.UnaryExpr) expression;
            FilterPlan value = compile(unary.operand());
            return unary.positive() ? value : FilterPlan.not(value);
        }
        if (expression instanceof FilterExpr.BinaryExpr) {
            FilterExpr.BinaryExpr binary = (FilterExpr.BinaryExpr) expression;
            FilterExpr.Op op = binary.operator();
            FilterPlan.Kind kind;
            switch (op) {
                case AND: kind = FilterPlan.Kind.AND; break;
                case OR: kind = FilterPlan.Kind.OR; break;
                case EQ: case NE: case GT: case GE: case LT: case LE:
                    kind = FilterPlan.Kind.COMPARE;
                    break;
                default: throw unsupported("operator " + op);
            }
            return FilterPlan.binary(kind, op, compile(binary.left()), compile(binary.right()));
        }
        throw unsupported("expression " + expression.getClass().getName());
    }

    private static UnsupportedOperationException unsupported(String message) {
        return new UnsupportedOperationException("cannot compile filter " + message);
    }
}
