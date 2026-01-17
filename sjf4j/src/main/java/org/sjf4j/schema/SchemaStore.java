package org.sjf4j.schema;

import org.sjf4j.JsonException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public final class SchemaStore {
    private final Map<URI, JsonSchema> store = new HashMap<>();

    public SchemaStore() {}
    public SchemaStore(JsonSchema... schemas) {
        for (JsonSchema schema : schemas) {
            addSchema(schema);
        }
    }

    public void addSchema(JsonSchema schema) {
        addSchema(schema.getUri(), schema);
    }
    public void addSchema(URI uri, JsonSchema schema) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(schema);
        if (store.containsKey(uri)) {
            throw new JsonException("Schema id '" + uri + "' already exists in SchemaStore");
        }
        store.put(uri, schema);
        schema.compileOrThrow();
    }

    public JsonSchema getSchema(URI uri) {
        return store.get(uri);
    }

    public boolean contains(URI uri) {
        return store.containsKey(uri);
    }

    public Set<URI> getUris() {
        return store.keySet();
    }

}
