package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SchemaDialectTest {

    @Test
    void detect_recognizesKnownDraftSchemaUris() {
        assertEquals(SchemaDialect.DRAFT_2020_12, SchemaDialect.detect("https://json-schema.org/draft/2020-12/schema"));
        assertEquals(SchemaDialect.DRAFT_2019_09, SchemaDialect.detect("https://json-schema.org/draft/2019-09/schema#"));
        assertEquals(SchemaDialect.DRAFT_07, SchemaDialect.detect("http://json-schema.org/draft-07/schema#"));
        assertEquals(SchemaDialect.DRAFT_07, SchemaDialect.detect("https://json-schema.org/draft-07/schema"));
        assertNull(SchemaDialect.detect("https://example.com/schema"));
    }

}
