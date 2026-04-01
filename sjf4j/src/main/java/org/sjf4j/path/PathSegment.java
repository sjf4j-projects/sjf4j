package org.sjf4j.path;


import java.util.List;


/**
 * Immutable segment in a JSONPath/JSON Pointer token chain.
 *
 * <p>Each segment points to its parent to allow reconstructing the full path
 * when formatting errors or converting to expressions.
 */
public abstract class PathSegment {
    protected final PathSegment parent;
    protected final Class<?> clazz;

    /**
     * Creates a path segment with parent and container type.
     */
    public PathSegment(PathSegment parent, Class<?> clazz) {
        this.parent = parent;
        this.clazz = clazz;
    }
    /**
     * Returns the parent segment in the chain.
     */
    public PathSegment parent() {return parent;}
    /**
     * Returns the declared container type for this segment, if any.
     */
    public Class<?> clazz() {return clazz;}

    /**
     * Returns true if this segment matches the given object key.
     */
    public boolean matchKey(String key) { return false; }
    /**
     * Returns true if this segment matches the given array index.
     */
    public boolean matchIndex(int idx, int size) { return false; }

    /**
     * Returns a human-readable inspection string rooted at this segment.
     */
    public String rootedInspect() {
        return Paths.rootedInspect(this);
    }
    /**
     * Returns a JSON Pointer expression rooted at this segment.
     */
    public String rootedPointerExpr() {
        return Paths.rootedPointerExpr(this);
    }
    /**
     * Returns a JSONPath expression rooted at this segment.
     */
    public String rootedPathExpr() {
        return Paths.rootedPathExpr(this);
    }

    /// Subclasses: Root, Name, Index, Wildcard, Slice, Union, Descendant, Function, Filter, Append

    /**
     * Represents the root token ($) in a JSON path expression.
     */
    public static final class Root extends PathSegment {
        private Root() {super(null, null);}
        public Root(PathSegment parent, Class<?> clazz) {super(parent, clazz);}
        public static final Root INSTANCE = new Root();
        @Override public String toString() { return "$"; }
    }


    /**
     * Represents the current token (@) in a JSON path expression.
     * This token is used only inside filter expressions.
     */
    public static final class Current extends PathSegment {
        private Current() {super(null, null);}
        public static final Current INSTANCE = new Current();
        @Override public String toString() { return "@"; }
    }

    /**
     * Represents a named property token in a JSON path expression.
     */
    public static final class Name extends PathSegment {
        public final String name;

        /**
         * Creates a property-name segment.
         */
        public Name(PathSegment parent, Class<?> clazz, String name) {
            super(parent, clazz);
            this.name = name;
        }
        @Override public boolean matchKey(String key) { return name.equals(key); }
        public boolean needQuoted() { return shouldArrayStyle(name); }
        public String toQuoted() { return quoteName(name); }
        @Override public String toString() {
            if (shouldArrayStyle(name)) {
                return "[" + quoteName(name) + "]";
            } else {
                return "." + name;
            }
        }
    }

    /**
     * Represents an index token in a JSON path expression.
     */
    public static final class Index extends PathSegment {
        public final int index;

        /**
         * Creates an array-index segment.
         */
        public Index(PathSegment parent, Class<?> clazz, int index) {
            super(parent, clazz);
            this.index = index;
        }

        /**
         * Matches index with support for negative offsets.
         */
        @Override
        public boolean matchIndex(int idx, int size) {
            int pindex = index < 0 ? size + index : index;
            return pindex == idx;
        }
        @Override public String toString() {
            return "[" + index + "]";
        }
    }

    /**
     * Represents a wildcard token (*) in a JSON path expression.
     */
    public static final class Wildcard extends PathSegment {
        /**
         * Creates a wildcard segment.
         */
        public Wildcard(PathSegment parent, Class<?> clazz) {
            super(parent, clazz);
        }
        @Override public boolean matchKey(String key) { return true; }
        @Override public boolean matchIndex(int index, int size) { return true; }
        @Override public String toString() { return "[*]"; }
    }

    /**
     * Represents a slice token in a JSON path expression.
     */
    public static final class Slice extends PathSegment {
        public final Integer start; // null allowed
        public final Integer end;   // null allowed
        public final Integer step;  // null allowed
        /**
         * Creates an array-slice segment.
         */
        public Slice(PathSegment parent, Class<?> clazz, Integer s, Integer e, Integer st) {
            super(parent, clazz);
            start = s; end = e; step = st;
        }

        /**
         * Matches index against slice bounds and step.
         */
        @Override
        public boolean matchIndex(int idx, int size) {
            if (start != null) {
                int pstart = start < 0 ? size + start : start;
                if (idx < pstart) return false;
            }
            if (end != null) {
                int pend = end < 0 ? size + end : end;
                if (idx >= pend) return false;
            }
            if (step != null) {
                int mod = start == null ? 0 : start;
                return (idx - mod) % step == 0;
            }
            return true;
        }

        /**
         * Returns slice expression without brackets.
         */
        public String toExpr() {
            if (start == null) {
                if (end == null) {
                    return "::" + step;
                } else if (step == null){
                    return ":" + end;
                } else {
                    return ":" + end + ":" + step;
                }
            } else if (end == null) {
                if (step == null) {
                    return start + ":";
                } else {
                    return start + "::" + step;
                }
            } else if (step == null) {
                return start + ":" + end;
            } else {
                return start + ":" + end + ":" + step;
            }
        }
        @Override public String toString() {
            return "[" + toExpr() + "]";
        }
    }

    /**
     * Represents a union token in a JSON path expression.
     */
    public static final class Union extends PathSegment {
        public final PathSegment[] union;

        /**
         * Creates a union segment.
         */
        public Union(PathSegment parent, Class<?> clazz, PathSegment[] union) {
            super(parent, clazz);
            this.union = union;
        }
        @Override public boolean matchKey(String key) {
            for (PathSegment pt : union) {
                if (pt.matchKey(key)) return true;
            }
            return false;
        }
        @Override public boolean matchIndex(int index, int size) {
            for (PathSegment pt : union) {
                if (pt.matchIndex(index, size)) return true;
            }
            return false;
        }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < union.length; i++) {
                if (i > 0) sb.append(",");
                PathSegment pt = union[i];
                if (pt instanceof Name) {
                    sb.append(quoteName(((Name) pt).name));
                } else if (pt instanceof Index) {
                    sb.append(((Index) pt).index);
                } else if (pt instanceof Slice) {
                    sb.append(((Slice) pt).toExpr());
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }

    public static final class Descendant extends PathSegment {
        /**
         * Creates a descendant segment.
         */
        public Descendant(PathSegment parent, Class<?> clazz) {
            super(parent, clazz);
        }
        @Override public String toString() {
            return "..";
        }
    }

    /**
     * JSONPath function call token, e.g. ".length()".
     */
    public static final class Function extends PathSegment {
        public final String name;
        public final List<String> args;

        /**
         * Creates a function-call segment.
         */
        public Function(PathSegment parent, Class<?> clazz, String name, List<String> args) {
            super(parent, clazz);
            this.name = name;
            this.args = args;
        }
        @Override public String toString() {
            if (args == null || args.isEmpty()) return "." + name + "()";
            return "." + name + "(" + String.join(", ", args) + ")";
        }
    }

    /**
     * Filter token, e.g. "[?(@.a > 1)]".
     */
    public static final class Filter extends PathSegment {
        public final FilterExpr filterExpr;

        /**
         * Creates a filter-expression segment.
         */
        public Filter(PathSegment parent, Class<?> clazz, FilterExpr filterExpr) {
            super(parent, clazz);
            this.filterExpr = filterExpr;
        }
        public String toString() { return "[?" + filterExpr + "]"; }
    }

    /**
     * Append token used by JSON Pointer ("-") and JSONPath ("[+]").
     */
    public static final class Append extends PathSegment {
        /**
         * Creates an append segment.
         */
        public Append(PathSegment parent, Class<?> clazz) {
            super(parent, clazz);
        }
        @Override public String toString() {
            return "[+]";
        }
    }

    /// protected

    protected boolean shouldArrayStyle(String name) {
        if (name.isEmpty()) {
            return true;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return true;
            }
        }
        return false;
    }

    protected String quoteName(String name) {
        boolean needsEscape = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\\' || c == '\'') {
                needsEscape = true;
                break;
            }
        }
        if (!needsEscape) {
            return "'" + name + "'";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("'");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '\'') sb.append("\\'");
            else sb.append(c);
        }
        sb.append("'");
        return sb.toString();
    }

}
