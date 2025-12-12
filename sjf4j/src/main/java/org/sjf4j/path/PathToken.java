package org.sjf4j.path;


import java.util.List;

/**
 * Abstract base class representing tokens in a JSON path expression.
 * This class provides the foundation for different types of path tokens used in
 * JSON path expressions, including root, name, index, wildcard, slice, union,
 * descendant, and function tokens.
 */
public abstract class PathToken {

    /**
     * Determines if a given key matches this path token.
     *
     * @param key the key to match against this token
     * @return true if the key matches, false otherwise
     */
    public abstract boolean match(Object key);

    /// Root, Name, Index, Wildcard, Slice, Union, Descendant, Function

    /**
     * Represents the root token ($) in a JSON path expression.
     */
    public static class Root extends PathToken {
        @Override public boolean match(Object key) { return false; }
        @Override public String toString() { return "$"; }
    }

    /**
     * Represents a named property token in a JSON path expression.
     */
    public static class Name extends PathToken {
        public final String name;
        public Name(String name) { this.name = name; }
        public boolean needQuoted() { return shouldArrayStyle(name); }
        public String toQuoted() { return quoteName(name); }
        @Override public boolean match(Object key) { return name.equals(key); }
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
    public static class Index extends PathToken {
        public final int index;
        public Index(int index) { this.index = index; }
        @Override public boolean match(Object key) { return key instanceof Integer && index == (int) key; }
        @Override public String toString() {
            return "[" + index + "]";
        }
    }

    /**
     * Represents a wildcard token (*) in a JSON path expression.
     */
    public static class Wildcard extends PathToken {
        public boolean arrayStyle = false;
        public Wildcard() {}
        public Wildcard(boolean arrayStyle) { this.arrayStyle = arrayStyle; }
        @Override public boolean match(Object key) { return true; }
        @Override public String toString() {
            if (arrayStyle) {
                return "[*]";
            } else {
                return ".*";
            }
        }
    }

    /**
     * Represents a slice token in a JSON path expression.
     */
    public static class Slice extends PathToken {
        public final Integer start; // null allowed
        public final Integer end;   // null allowed
        public final Integer step;  // null allowed
        public Slice(Integer s, Integer e, Integer st) {
            start = s; end = e; step = st;
        }
        @Override public boolean match(Object key) { // Must be positive
            if (!(key instanceof Integer)) return false;
            int idx = (int) key;
            if (start != null && idx < start) return false;
            if (end != null && idx >= end) return false;
            if (step != null) {
                int mod = start == null ? 0 : start;
                return (idx - mod) % step == 0;
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
    public static class Union extends PathToken {
        public final List<PathToken> union;
        public Union(List<PathToken> union) { this.union = union; }
        @Override public boolean match(Object key) {
            for (PathToken pt : union) {
                if (pt.match(key)) return true;
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

    public static class Descendant extends PathToken {
        @Override public boolean match(Object key) { return true; }
        @Override public String toString() {
            return "..";
        }
    }

    public static class Function extends PathToken {
        public final String name;
        public final List<String> args;
        public Function(String name, List<String> args) {
            this.name = name;
            this.args = args;
        }
        @Override public boolean match(Object key) { return false; }
        @Override public String toString() {
            if (args == null || args.isEmpty()) return "." + name + "()";
            return "." + name + "(" + String.join(", ", args) + ")";
        }
    }

    public static class Filter extends PathToken {
        public final String expr;
        public Filter(String expr) { this.expr = expr; }
        @Override public boolean match(Object key) { return false; }
        public String toString() { return "[?" + expr + "]"; }
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
