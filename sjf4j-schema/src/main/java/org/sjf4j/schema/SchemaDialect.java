package org.sjf4j.schema;

import java.net.URI;

/**
 * Lightweight JSON Schema draft detection for planning decisions.
 */
public enum SchemaDialect {
    DRAFT_07,
    DRAFT_2019_09,
    DRAFT_2020_12;


    public static SchemaDialect detect(String schemaUri) {
        if (schemaUri == null || schemaUri.isEmpty()) return null;
        switch (SchemaUtil.normalizeUriKey(schemaUri)) {
            case "https://json-schema.org/draft/2020-12/schema":
                return DRAFT_2020_12;
            case "https://json-schema.org/draft/2019-09/schema":
                return DRAFT_2019_09;
            case "http://json-schema.org/draft-07/schema":
            case "https://json-schema.org/draft-07/schema":
                return DRAFT_07;
            default:
                return null;
        }
    }

    public static SchemaDialect detect(URI schemaUri) {
        return schemaUri == null ? null : detect(schemaUri.toString());
    }

    boolean supportsKeyword(String keyword) {
        switch (this) {
            case DRAFT_07:
                switch (keyword) {
                    case "$defs":
                    case "$anchor":
                    case "$dynamicAnchor":
                    case "$dynamicRef":
                    case "$recursiveAnchor":
                    case "$recursiveRef":
                    case "$vocabulary":
                    case "dependentRequired":
                    case "dependentSchemas":
                    case "unevaluatedItems":
                    case "unevaluatedProperties":
                    case "prefixItems":
                        return false;
                }
                return true;
            case DRAFT_2019_09:
                switch (keyword) {
                    case "$dynamicRef":
                    case "$dynamicAnchor":
                    case "prefixItems":
                        return false;
                }
                return true;
            default:
                return true;
        }
    }

}
