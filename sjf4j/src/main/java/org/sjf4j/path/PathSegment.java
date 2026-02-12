package org.sjf4j.path;


import java.util.List;


public abstract class PathSegment {
    protected final PathSegment parent;
    protected final Class<?> clazz;
    public PathSegment(PathSegment parent, Class<?> clazz) {
        this.parent = parent;
        this.clazz = clazz;
    }
    public PathSegment parent() {return parent;}
    public Class<?> clazz() {return clazz;}

    public boolean matchKey(String key) { return false; }
    public boolean matchIndex(int idx) { return false; }

    public String rootedInspect() {
        return Paths.rootedInspect(this);
    }
    public String rootedPointerExpr() {
        return Paths.rootedPointerExpr(this);
    }
    public String rootedPathExpr() {
        return Paths.rootedPathExpr(this);
    }

    /// Subclasses: Root, Name, Index, Wildcard, Slice, Union, Descendant, Function, Filter, APPEND

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
        public Index(PathSegment parent, Class<?> clazz, int index) {
            super(parent, clazz);
            this.index = index;
        }
        public boolean matchIndex(int idx) { return index == idx; }
        @Override public String toString() {
            return "[" + index + "]";
        }
    }

    /**
     * Represents a wildcard token (*) in a JSON path expression.
     */
    public static final class Wildcard extends PathSegment {
        public Wildcard(PathSegment parent, Class<?> clazz) {
            super(parent, clazz);
        }
        @Override public boolean matchKey(String key) { return true; }
        @Override public boolean matchIndex(int index) { return true; }
        @Override public String toString() { return "[*]"; }
    }

    /**
     * Represents a slice token in a JSON path expression.
     */
    public static final class Slice extends PathSegment {
        public final Integer start; // null allowed
        public final Integer end;   // null allowed
        public final Integer step;  // null allowed
        public Slice(PathSegment parent, Class<?> clazz, Integer s, Integer e, Integer st) {
            super(parent, clazz);
            start = s; end = e; step = st;
        }
        @Override public boolean matchIndex(int index) { // Must be positive
            if (start != null && index < start) return false;
            if (end != null && index >= end) return false;
            if (step != null) {
                int mod = start == null ? 0 : start;
                return (index - mod) % step == 0;
            }
            return true;
        }
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
        @Override public boolean matchIndex(int index) {
            for (PathSegment pt : union) {
                if (pt.matchIndex(index)) return true;
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
        public Descendant(PathSegment parent, Class<?> clazz) {
            super(parent, clazz);
        }
        @Override public String toString() {
            return "..";
        }
    }

    public static final class Function extends PathSegment {
        public final String name;
        public final List<String> args;
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

    public static final class Filter extends PathSegment {
        public final FilterExpr filterExpr;
        public Filter(PathSegment parent, Class<?> clazz, FilterExpr filterExpr) {
            super(parent, clazz);
            this.filterExpr = filterExpr;
        }
        public String toString() { return "[?" + filterExpr + "]"; }
    }

    public static final class Append extends PathSegment {
        public Append(PathSegment parent, Class<?> clazz) {
            super(parent, clazz);
        }
        @Override public String toString() {
            return "-";
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
