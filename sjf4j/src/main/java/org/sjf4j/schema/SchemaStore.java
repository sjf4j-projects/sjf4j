package org.sjf4j.schema;

import org.sjf4j.JsonException;
import org.sjf4j.Sjf4j;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public class SchemaStore {
    private final Map<URI, ObjectSchema> schemas = new HashMap<>();

    public SchemaStore() {}
    public SchemaStore(SchemaStore other) {
        importFrom(other);
    }
    public SchemaStore(JsonSchema... initialSchemas) {
        for (JsonSchema schema : initialSchemas) {
            register(schema);
        }
    }

    public void register(JsonSchema schema) {
        if (schema instanceof ObjectSchema) {
            ObjectSchema os = (ObjectSchema) schema;
            URI uri = os.getResolvedUri();
            if (uri == null)
                throw new SchemaException("Schema has no effective URI and cannot be registered. " +
                        "Only schemas with an explicit or inherited absolute $id may be stored. ");
            register(uri, os);
        } else {
            throw new SchemaException("Unsupported schema type for registration: " + schema.getClass().getName());
        }
    }

    protected void register(URI uri, ObjectSchema schema) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(schema);
        if (uri.toString().isEmpty())
            throw new SchemaException("Invalid schema: uri should not be empty");
        if (!uri.isAbsolute())
            throw new SchemaException("Invalid schema: uri must be absolute (not relative)");
        schemas.put(uri, schema);
    }

    public void importFrom(SchemaStore other) {
        if (other != null) schemas.putAll(other.schemas);
    }

    public ObjectSchema resolve(URI uri) {
        return schemas.get(uri);
    }

    public boolean contains(URI uri) {
        return schemas.containsKey(uri);
    }

    public Set<URI> uris() {
        return schemas.keySet();
    }


    /// Global Schemas
    private static final SchemaStore GLOBAL_STORE = new SchemaStore();
    public static ObjectSchema globalResolve(URI uri) {
        return GLOBAL_STORE.resolve(uri);
    }

    static {
        loadSchema("json-schema/draft2020-12/meta/core.json");
        loadSchema("json-schema/draft2020-12/meta/applicator.json");
        loadSchema("json-schema/draft2020-12/meta/validation.json");
        loadSchema("json-schema/draft2020-12/meta/meta-data.json");
        loadSchema("json-schema/draft2020-12/meta/format-annotation.json");
        loadSchema("json-schema/draft2020-12/meta/unevaluated.json");
        loadSchema("json-schema/draft2020-12/meta/content.json");
        loadSchema("json-schema/draft2020-12/schema.json");
    }

    private static void loadSchema(String schemaPath) {
        if (schemaPath != null) {
            try (InputStream in = SchemaStore.class.getClassLoader().getResourceAsStream(schemaPath)) {
                if (in == null) {
                    throw new IllegalStateException("Schema resource file not found: " + schemaPath);
                }
                ObjectSchema schema = Sjf4j.fromJson(in, ObjectSchema.class);
                GLOBAL_STORE.register(schema);
            } catch (Exception e) {
                throw new JsonException("Failed to load schema: " + schemaPath, e);
            }
        }
    }

}
