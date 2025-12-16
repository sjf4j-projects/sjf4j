package org.sjf4j.path;


import java.util.List;

/**
 * Abstract base class representing tokens in a JSON path expression.
 * This class provides the foundation for different types of path tokens used in
 * JSON path expressions, including root, name, index, wildcard, slice, union,
 * descendant, and function tokens.
 */
public abstract class PathToken {

    public boolean matchKey(String key) { return false; }
    public boolean matchIndex(int idx) { return false; }


    /// Subclasses: Root, Name, Index, Wildcard, Slice, Union, Descendant, Function

    /**
     * Represents the root token ($) in a JSON path expression.
     */
    public static final class Root extends PathToken {
        private Root() {}
        public static final Root INSTANCE = new Root();
        @Override public String toString() { return "$"; }
    }


    /**
     * Represents the current token (@) in a JSON path expression.
     * This token is used only inside filter expressions.
     */
    public static final class Current extends PathToken {
        private Current() {}
        public static final Current INSTANCE = new Current();
        @Override public String toString() { return "@"; }
    }

    /**
     * Represents a named property token in a JSON path expression.
     */
    public static final class Name extends PathToken {
        public final String name;
        public Name(String name) { this.name = name; }
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
    public static final class Index extends PathToken {
        public final int index;
        public Index(int index) { this.index = index; }
        public boolean matchIndex(int idx) { return index == idx; }
        @Override public String toString() {
            return "[" + index + "]";
        }
    }

    /**
     * Represents a wildcard token (*) in a JSON path expression.
     */
    public static final class Wildcard extends PathToken {
        private Wildcard() {}
        public static final Wildcard INSTANCE = new Wildcard();
        @Override public boolean matchKey(String key) { return true; }
        @Override public boolean matchIndex(int index) { return true; }
        @Override public String toString() { return "[*]"; }
    }

    /**
     * Represents a slice token in a JSON path expression.
     */
    public static final class Slice extends PathToken {
        public final Integer start; // null allowed
        public final Integer end;   // null allowed
        public final Integer step;  // null allowed
        public Slice(Integer s, Integer e, Integer st) {
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
    public static final class Union extends PathToken {
        public final List<PathToken> union;
        public Union(List<PathToken> union) { this.union = union; }
        @Override public boolean matchKey(String key) {
            for (PathToken pt : union) {
                if (pt.matchKey(key)) return true;
            }
            return false;
        }
        @Override public boolean matchIndex(int index) {
            for (PathToken pt : union) {
                if (pt.matchIndex(index)) return true;
            }
            return false;
        }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < union.size(); i++) {
                if (i > 0) sb.append(",");
                PathToken pt = union.get(i);
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

    public static final class Descendant extends PathToken {
        private Descendant() {}
        public static final Descendant INSTANCE = new Descendant();
        @Override public String toString() {
            return "..";
        }
    }

    public static final class Function extends PathToken {
        public final String name;
        public final List<String> args;
        public Function(String name, List<String> args) {
            this.name = name;
            this.args = args;
        }
        @Override public String toString() {
            if (args == null || args.isEmpty()) return "." + name + "()";
            return "." + name + "(" + String.join(", ", args) + ")";
        }
    }

    public static final class Filter extends PathToken {
        public final FilterExpr filterExpr;
        public Filter(FilterExpr filterExpr) {
            this.filterExpr = filterExpr;
        }
        public String toString() { return "[?" + filterExpr + "]"; }
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
