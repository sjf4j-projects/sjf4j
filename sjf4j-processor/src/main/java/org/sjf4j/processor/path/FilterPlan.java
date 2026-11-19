package org.sjf4j.processor.path;

import org.sjf4j.path.FilterExpr;
import org.sjf4j.processor.access.NodeAccess;

/** Immutable, supported filter expression tree for direct Java generation. */
final class FilterPlan {
    enum Kind { LITERAL, PATH, NOT, AND, OR, COMPARE, FALLBACK }

    final Kind kind;
    final Object literal;
    final boolean rooted;
    final String[] members;
    /** Directly readable POJO prefix; remaining members use OBNT traversal. */
    final NodeAccess[] propertyPrefix;
    final FilterExpr.Op operator;
    final FilterPlan left;
    final FilterPlan right;

    private FilterPlan(Kind kind, Object literal, boolean rooted, String[] members,
                       NodeAccess[] propertyPrefix, FilterExpr.Op operator,
                       FilterPlan left, FilterPlan right) {
        this.kind = kind;
        this.literal = literal;
        this.rooted = rooted;
        this.members = members;
        this.propertyPrefix = propertyPrefix;
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    static FilterPlan literal(Object value) {
        return new FilterPlan(Kind.LITERAL, value, false, null, null, null, null, null);
    }

    static FilterPlan path(boolean rooted, String[] members, NodeAccess[] propertyPrefix) {
        return new FilterPlan(Kind.PATH, null, rooted, members, propertyPrefix, null, null, null);
    }

    static FilterPlan not(FilterPlan value) {
        return new FilterPlan(Kind.NOT, null, false, null, null, null, value, null);
    }

    static FilterPlan binary(Kind kind, FilterExpr.Op operator, FilterPlan left, FilterPlan right) {
        return new FilterPlan(kind, null, false, null, null, operator, left, right);
    }

    static FilterPlan fallback() {
        return new FilterPlan(Kind.FALLBACK, null, false, null, null, null, null, null);
    }
}
