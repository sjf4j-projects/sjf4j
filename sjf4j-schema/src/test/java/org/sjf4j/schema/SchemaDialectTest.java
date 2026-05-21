package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaDialectTest {

    @Test
    void detect_recognizesKnownDraftSchemaUris() {
        assertEquals(SchemaDialect.DRAFT_2020_12, SchemaDialect.detect("https://json-schema.org/draft/2020-12/schema"));
        assertEquals(SchemaDialect.DRAFT_2019_09, SchemaDialect.detect("https://json-schema.org/draft/2019-09/schema#"));
        assertEquals(SchemaDialect.DRAFT_07, SchemaDialect.detect("http://json-schema.org/draft-07/schema#"));
        assertEquals(SchemaDialect.DRAFT_07, SchemaDialect.detect("https://json-schema.org/draft-07/schema"));
        assertNull(SchemaDialect.detect("https://example.com/schema"));
    }

    @Test
    void supportsKeyword_matchesDraftCapabilities() {
        assertFalse(SchemaDialect.DRAFT_07.supportsKeyword("$dynamicRef"));
        assertFalse(SchemaDialect.DRAFT_2019_09.supportsKeyword("$dynamicRef"));
        assertTrue(SchemaDialect.DRAFT_2020_12.supportsKeyword("$dynamicRef"));
        assertFalse(SchemaDialect.DRAFT_07.supportsKeyword("prefixItems"));
        assertFalse(SchemaDialect.DRAFT_2019_09.supportsKeyword("prefixItems"));
        assertTrue(SchemaDialect.DRAFT_2020_12.supportsKeyword("prefixItems"));
    }

}
