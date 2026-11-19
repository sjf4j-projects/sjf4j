package org.sjf4j.path;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeWalker;

/**
 * Direct evaluation of statically known filter member paths.
 *
 * <p>Generated navigators provide precomputed path segments, avoiding
 * runtime JSONPath parsing and FilterExpr interpretation. Object access
 * retains the same present-null versus absent semantics as JSONPath.</p>
 */
public final class PathSupport {
    private PathSupport() {
    }

    /** The same singleton used by runtime FilterExpr for an absent path. */
    public static Object missing() {
        return FilterExpr.PathExpr.NOTHING;
    }

    public static Object value(Object current, String[] members, NodeWalker.Access access) {
        Object node = current;
        for (String member : members) {
            if (node == null || !JsonType.of(node).isObject()) {
                return FilterExpr.PathExpr.NOTHING;
            }
            NodeWalker.getAccessInObject(node, member, access);
            if (!access.present) return FilterExpr.PathExpr.NOTHING;
            node = access.node;
        }
        return node;
    }

    /** A path predicate tests presence, including values null, false and zero. */
    public static boolean exists(Object current, String[] members, NodeWalker.Access access) {
        return members.length == 0 || value(current, members, access) != FilterExpr.PathExpr.NOTHING;
    }
}
