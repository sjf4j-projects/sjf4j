package org.sjf4j.processor;

import java.util.HashSet;
import java.util.Set;

/**
 * Simple deterministic Java local/helper name allocator for generated code.
 *
 * <p>The allocator is scoped to one generated method or helper group.  It
 * reserves Java keywords up front, filters invalid identifier characters from
 * caller-provided hints, and appends numeric suffixes only when needed.  This
 * keeps generated source readable while avoiding accidental collisions with
 * user parameter names or earlier temporary variables.</p>
 */
public final class NameAllocator {
    private final Set<String> used = new HashSet<String>();

    public NameAllocator() {
        String[] keywords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally",
                "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
                "native", "new", "null", "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "true",
                "try", "void", "volatile", "while"
        };
        for (String keyword : keywords) used.add(keyword);
    }

    /**
     * Marks a caller-supplied name as unavailable, usually a method parameter or
     * an already emitted local variable.
     */
    public void reserve(String name) {
        if (name != null && name.length() != 0) used.add(name);
    }

    /**
     * Allocates a local variable name from a preferred human-readable hint.
     */
    public String local(String preferred) { return allocate(clean(preferred, "v")); }

    /**
     * Allocates a local variable name with a stable prefix and sanitized hint.
     */
    public String prefixed(String prefix, String hint) { return allocate(prefix + "_" + clean(hint, "value")); }

    /**
     * Allocates a private helper method name used by generated mapper code.
     */
    public String helper(String hint) { return allocate("_map" + upper(clean(hint, "Value"))); }

    private String allocate(String base) {
        if (used.add(base)) return base;
        for (int i = 2; ; i++) {
            String candidate = base + i;
            if (used.add(candidate)) return candidate;
        }
    }

    private String clean(String s, String fallback) {
        StringBuilder b = new StringBuilder();
        if (s != null) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((b.length() == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c))) {
                    b.append(c);
                }
            }
        }
        return b.length() == 0 ? fallback : b.toString();
    }

    private String upper(String s) {
        if (s.length() == 0) return "Value";
        StringBuilder b = new StringBuilder(s.length());
        boolean up = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') {
                up = true;
                continue;
            }
            b.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return b.length() == 0 ? "Value" : b.toString();
    }
}
