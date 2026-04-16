package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;

class SchemaStoreTest {

    @Test
    void loadSchemaFromMissingResource_returnsNull() {
        assertNull(SchemaStore.loadSchemaFromResource("json-schemas/missing-schema.json"));
    }

    @Test
    void loadSchemaFromMissingFile_returnsNull(@TempDir Path tempDir) {
        assertNull(SchemaStore.loadSchemaFromFile(tempDir.resolve("missing.json").toString()));
    }

    @Test
    void loadSchemaFromMissingLocalUri_returnsNull() {
        assertNull(SchemaStore.loadSchemaFromLocalUri(URI.create("classpath:/json-schemas/missing-schema.json")));
    }
}
