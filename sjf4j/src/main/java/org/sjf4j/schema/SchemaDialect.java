package org.sjf4j.schema;

import java.net.URI;

/**
 * Lightweight JSON Schema draft detection for planning decisions.
 */
public final class SchemaDialect {

    public static final SchemaDialect DRAFT_2020_12 = new SchemaDialect("2020-12");
    public static final SchemaDialect DRAFT_2019_09 = new SchemaDialect("2019-09");
    public static final SchemaDialect DRAFT_07 = new SchemaDialect("draft-07");

    public final String name;

    private SchemaDialect(String name) {
        this.name = name;
    }

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

}
