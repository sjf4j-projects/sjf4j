package org.sjf4j.processor.path;

import org.sjf4j.path.FilterExpr;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.property.Property;

import javax.lang.model.type.TypeMirror;

import java.util.ArrayList;
import java.util.List;

/** Lowers the supported subset of FilterExpr into an immutable generation plan. */
final class FilterCompiler {
    private final ProcessorContext context;

    FilterCompiler(ProcessorContext context) {
        this.context = context;
    }

    FilterPlan compile(FilterExpr expression, TypeMirror rootType, TypeMirror currentType) {
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
            return FilterPlan.path(rooted, names.toArray(new String[0]),
                    propertyPrefix(rooted ? rootType : currentType, names));
        }
        if (expression instanceof FilterExpr.UnaryExpr) {
            FilterExpr.UnaryExpr unary = (FilterExpr.UnaryExpr) expression;
            FilterPlan value = compile(unary.operand(), rootType, currentType);
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
            return FilterPlan.binary(kind, op,
                    compile(binary.left(), rootType, currentType),
                    compile(binary.right(), rootType, currentType));
        }
        throw unsupported("expression " + expression.getClass().getName());
    }

    /**
     * Resolve the longest public, readable POJO/JOJO prefix. A dynamic suffix
     * is still traversed with OBNT; no runtime reflection is introduced for
     * the statically known properties.
     */
    private NodeAccess[] propertyPrefix(TypeMirror owner, List<String> members) {
        List<NodeAccess> resolved = new ArrayList<>();
        for (String member : members) {
            NodeAccess access = context.access.resolveName(owner, member);
            if (access == null || access.kind() != NodeAccess.Kind.PROPERTY
                    || !access.readable()) break;

            // Navigation's general resolver does not apply every runtime
            // POJO rule (@NodeIgnore, transient, field/bean precedence,
            // renamed members). Only specialize the exact accessor that
            // runtime structural access would select.
            Property runtime = context.properties.resolveForBinding(owner).get(member);
            if (runtime == null || runtime.read() == null ||
                    !runtime.read().member().equals(access.property().read().member())) {
                break;
            }

            resolved.add(access);
            owner = access.readType();
        }
        return resolved.toArray(new NodeAccess[0]);
    }

    private static UnsupportedOperationException unsupported(String message) {
        return new UnsupportedOperationException("cannot compile filter " + message);
    }
}
