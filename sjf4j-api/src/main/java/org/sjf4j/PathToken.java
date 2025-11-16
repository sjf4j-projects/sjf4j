package org.sjf4j;

import lombok.NonNull;

public abstract class PathToken {

    public static class Root extends PathToken {
        @Override public String toString() { return "$"; }
    }

    public static class Name extends PathToken {
        public final String name;
        public Name(@NonNull String name) { this.name = name; }

        private String stringInPath;
        @Override public String toString() {
            if (stringInPath == null) {
                if (shouldArrayStyle(name)) {
                    stringInPath = quoteName(name);
                } else {
                    stringInPath = "." + name;
                }
            }
            return stringInPath;
        }
    }

    public static class Index extends PathToken {
        public final int index;
        public Index(int index) { this.index = index; }

        @Override public String toString() {
            return "[" + index + "]";
        }
    }

    public static class Wildcard extends PathToken {
        public boolean arrayStyle = false;
        public Wildcard() {}
        public Wildcard(boolean arrayStyle) { this.arrayStyle = arrayStyle; }

        @Override public String toString() {
            if (arrayStyle) {
                return "[*]";
            } else {
                return ".*";
            }
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
            return "['" + name + "']";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("['");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '\'') sb.append("\\'");
            else sb.append(c);
        }
        sb.append("']");
        return sb.toString();
    }

}
