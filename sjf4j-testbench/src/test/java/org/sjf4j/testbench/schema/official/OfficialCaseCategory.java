package org.sjf4j.testbench.schema.official;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Explicit coverage inventory of the official Draft 2020-12 root test files.
 * Unknown files fail discovery instead of disappearing from a phase's report.
 * The optional/ directory is intentionally excluded by the caller.
 */
final class OfficialCaseCategory {
    enum Category {
        BASIC, SCALAR, OBJECT, ARRAY, APPLICATOR, REFERENCE
    }

    private static final Map<String, Category> FILES = new LinkedHashMap<>();

    static {
        register(Category.BASIC, "boolean_schema", "type", "enum", "const",
                "default", "content", "format", "vocabulary", "defs");
        register(Category.SCALAR, "minLength", "maxLength", "pattern",
                "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf");
        register(Category.OBJECT, "properties", "required", "minProperties", "maxProperties",
                "additionalProperties", "dependentRequired", "dependentSchemas",
                "patternProperties", "propertyNames");
        register(Category.ARRAY, "items", "prefixItems", "minItems", "maxItems",
                "uniqueItems", "contains", "minContains", "maxContains", "unevaluatedItems");
        register(Category.APPLICATOR, "allOf", "anyOf", "oneOf", "not",
                "if-then-else", "unevaluatedProperties");
        register(Category.REFERENCE, "ref", "refRemote", "anchor",
                "dynamicRef", "infinite-loop-detection");
    }

    private OfficialCaseCategory() {}

    private static void register(Category category, String... stems) {
        for (String stem : stems) {
            String file = stem + ".json";
            if (FILES.putIfAbsent(file, category) != null) {
                throw new IllegalStateException("Duplicate official file classification: " + file);
            }
        }
    }

    static Category category(String file) {
        Category category = FILES.get(file);
        if (category == null) {
            throw new IllegalStateException("Unclassified official Draft 2020-12 file: " + file);
        }
        return category;
    }

    static void assertComplete(Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(FILES.keySet());
        missing.removeAll(actual);
        Set<String> unclassified = new LinkedHashSet<>(actual);
        unclassified.removeAll(FILES.keySet());
        if (!missing.isEmpty() || !unclassified.isEmpty()) {
            throw new IllegalStateException("Official Draft 2020-12 file inventory differs: "
                    + "missing=" + missing + ", unclassified=" + unclassified);
        }
    }

    static boolean selected(String selection, String file) {
        Category category = category(file);
        switch (selection) {
            case "all":
                return true;
            case "phase2":
                return category == Category.BASIC
                        || category == Category.SCALAR || category == Category.OBJECT;
            case "phase3":
                return category == Category.ARRAY || category == Category.APPLICATOR;
            case "unevaluated":
                return "unevaluatedItems.json".equals(file)
                        || "unevaluatedProperties.json".equals(file);
            case "basic":
            case "scalar":
            case "object":
            case "array":
            case "applicator":
            case "reference":
                return category.name().equalsIgnoreCase(selection);
            default:
                throw new IllegalArgumentException("Unknown official compiled selection: " + selection);
        }
    }

    static String selection(String value) {
        String selection = value == null ? "all" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Arrays.asList("all", "phase2", "phase3", "basic", "scalar", "object",
                "array", "applicator", "unevaluated", "reference").contains(selection)) {
            throw new IllegalArgumentException("Invalid official compiled category: " + value);
        }
        return selection;
    }

    static Map<String, Category> files() {
        return Collections.unmodifiableMap(FILES);
    }
}
