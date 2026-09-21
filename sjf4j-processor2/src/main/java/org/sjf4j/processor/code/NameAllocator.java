package org.sjf4j.processor.code;

import javax.lang.model.SourceVersion;
import java.util.HashSet;
import java.util.Set;

/**
 * Allocates deterministic Java identifiers for generated source code.
 *
 * <p>An allocator is normally scoped to one generated method. Method
 * parameters and other externally defined names should be reserved before
 * temporary names are allocated.</p>
 */
public final class NameAllocator {

    private final Set<String> used = new HashSet<String>();


    /**
     * Reserves an existing Java identifier.
     */
    public NameAllocator reserve(String name) {
        if (name != null && !name.isEmpty()) {
            used.add(name);
        }
        return this;
    }

    /**
     * Returns whether the name has already been reserved or allocated.
     */
    public boolean isUsed(String name) {
        return used.contains(name);
    }

    /**
     * Allocates a unique Java identifier based on the supplied suggestion.
     *
     * <p>Examples:</p>
     * <pre>
     * node  -> node
     * node  -> node2
     * class -> class2
     * a-b   -> a_b
     * </pre>
     */
    public String newName(String suggestion) {
        String base = sanitize(suggestion);

        if (available(base)) {
            used.add(base);
            return base;
        }

        for (int i = 2; ; i++) {
            String candidate = base + i;
            if (available(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
    }


    private boolean available(String name) {
        return !used.contains(name)
                && !SourceVersion.isKeyword(name)
                && !"_".equals(name);
    }

    /**
     * Converts an arbitrary hint into a legal Java identifier.
     */
    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "value";
        }

        StringBuilder out = new StringBuilder(value.length() + 1);

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (out.length() == 0) {
                if (Character.isJavaIdentifierStart(c)) {
                    out.append(c);
                } else if (Character.isJavaIdentifierPart(c)) {
                    out.append('_').append(c);
                } else {
                    out.append('_');
                }
            } else {
                out.append(
                        Character.isJavaIdentifierPart(c)
                                ? c
                                : '_');
            }
        }

        if (out.length() == 0) {
            return "value";
        }

        return out.toString();
    }
}