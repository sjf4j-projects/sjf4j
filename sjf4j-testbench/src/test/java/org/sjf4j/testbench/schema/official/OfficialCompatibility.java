package org.sjf4j.testbench.schema.official;

import org.sjf4j.schema.SchemaDialect;

/**
 * Dialect filtering for the unified official JSON Schema validation suite.
 * Comma-separated constraints describe ordered, potentially disjoint intervals.
 * See validation/README.md in JSON-Schema-Test-Suite.
 */
final class OfficialCompatibility {
    private OfficialCompatibility() {}

    static boolean matches(String compatibility, SchemaDialect dialect) {
        if (compatibility == null) return true;
        if (!compatibility.matches("^(?:<=|=)?[0-9]+(?:,(?:<=|=)?[0-9]+)*$")) {
            throw new IllegalArgumentException("Invalid official compatibility: " + compatibility);
        }
        int selected;
        switch (dialect) {
            case DRAFT_07: selected = 7; break;
            case DRAFT_2019_09: selected = 2019; break;
            case DRAFT_2020_12: selected = 2020; break;
            default: throw new IllegalArgumentException("Unsupported official dialect: " + dialect);
        }

        // Follow the upstream ordered-boundary algorithm: a lower bound is
        // superseded by its following upper bound, and later intervals may match.
        boolean match = false;
        for (String constraint : compatibility.split(",")) {
            boolean upper = constraint.startsWith("<=");
            boolean exact = constraint.startsWith("=");
            int start = upper ? 2 : exact ? 1 : 0;
            int version = Integer.parseInt(constraint.substring(start));
            match = upper ? selected <= version
                    : exact ? selected == version : selected >= version;
            if (selected <= version) break;
        }
        return match;
    }
}
