package org.sjf4j.testbench.schema.official;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit coverage inventory of the official Draft 2020-12 root test files.
 * Future upstream files are included as OTHER without requiring code changes.
 * The optional/ directory is intentionally excluded by the caller.
 */
final class OfficialCaseCategory {
    enum Category {
        BASIC, SCALAR, OBJECT, ARRAY, APPLICATOR, REFERENCE, OTHER
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
        return FILES.getOrDefault(file, Category.OTHER);
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

}
