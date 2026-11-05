package org.sjf4j.testbench.schema.official;

import org.sjf4j.schema.SchemaDialect;

import java.util.regex.Pattern;

/** Parses the official Annotation Test Suite's per-group compatibility metadata. */
final class OfficialAnnotationCompatibility {

    // annotations/test-case.schema.json in JSON-Schema-Test-Suite.
    private static final Pattern SYNTAX = Pattern.compile(
            "^(<=|=)?([123467]|2019|2020|9999)(,(<=|=)?([123467]|2019|2020|9999))*$");

    private OfficialAnnotationCompatibility() {
    }

    static boolean supports(String compatibility, SchemaDialect dialect) {
        // Missing compatibility means the case applies to every dialect.
        if (compatibility == null) return true;
        if (!SYNTAX.matcher(compatibility).matches()) {
            throw new IllegalArgumentException("Invalid annotation compatibility: " + compatibility);
        }

        int draft = switch (dialect) {
            case DRAFT_07 -> 7;
            case DRAFT_2019_09 -> 2019;
            case DRAFT_2020_12 -> 2020;
        };
        // Comma-separated constraints are intersected. A bare version is a minimum.
        for (String constraint : compatibility.split(",")) {
            if (constraint.startsWith("<=")) {
                if (draft > Integer.parseInt(constraint.substring(2))) return false;
            } else if (constraint.startsWith("=")) {
                if (draft != Integer.parseInt(constraint.substring(1))) return false;
            } else if (draft < Integer.parseInt(constraint)) {
                return false;
            }
        }
        return true;
    }
}
