package org.sjf4j.schema;

import org.sjf4j.Sjf4j;
import org.sjf4j.exception.SchemaException;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


public class SchemaStore {
    private final Map<URI, ObjectSchema> mixedUriSchemas = new HashMap<>();
//    private final ArrayList<ObjectSchema> schemas = new ArrayList<>();

    public SchemaStore() {}
    public SchemaStore(SchemaStore other) {
        importFrom(other);
    }
    public SchemaStore(JsonSchema... initialSchemas) {
        for (JsonSchema schema : initialSchemas) {
            register(schema);
        }
    }

    public boolean register(JsonSchema schema) {
        if (!(schema instanceof ObjectSchema)) return false;
        ObjectSchema os = (ObjectSchema) schema;
        URI uri = os.getResolvedUri();
        if (uri != null && !uri.toString().isEmpty()) {
            _register(uri, os);
            return true;
        }
        return false;
        // Nothing to register
//        throw new SchemaException("Cannot register schema: no available uri");
    }

    public boolean register(URI uri, JsonSchema schema) {
        if (!(schema instanceof ObjectSchema)) return false;
        ObjectSchema os = (ObjectSchema) schema;
        URI canonicalUri = os.getResolvedUri();
        if (uri != null) {
            if (canonicalUri != null && !uri.equals(canonicalUri)) {
                _register(uri, os);
                _register(canonicalUri, os);
            } else {
                _register(uri, os);
            }
            return true;
        }
        if (canonicalUri != null) {
            _register(canonicalUri, os);
            return true;
        }
        // Nothing to register
        return false;
    }

    private void _register(URI uri, JsonSchema schema) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(schema);
        if (uri.toString().isEmpty())
            throw new SchemaException("Invalid schema: uri should not be empty");
        if (!uri.isAbsolute())
            throw new SchemaException("Invalid schema: uri must be absolute (not relative): " + uri);
        if (!(schema instanceof ObjectSchema))
            throw new SchemaException("Invalid schema: schema must be object (not true or false)");
        ObjectSchema os = (ObjectSchema) schema;
        URI canonicalUri = os.getResolvedUri();
        ObjectSchema oldOs = mixedUriSchemas.put(uri, os);
        if (oldOs != null) {
            if (uri.equals(canonicalUri)) {
                throw new SchemaException("Duplicate schema uri: " + canonicalUri);
            } else {
                throw new SchemaException("Alias conflict, schema uri: " + canonicalUri + ", alias uri: " + uri);
            }
        }
    }

    public void importFrom(SchemaStore other) {
        if (other != null) {
            for (Map.Entry<URI, ObjectSchema> entry : other.mixedUriSchemas.entrySet()) {
                ObjectSchema os = entry.getValue();
                ObjectSchema oldOs = mixedUriSchemas.put(entry.getKey(), os);
                if (oldOs != null && !oldOs.getResolvedUri().equals(os.getResolvedUri())) {
                    throw new SchemaException("Duplicate schema uri: " + os.getResolvedUri());
                }
            }
        }
    }

    public ObjectSchema resolve(URI uri) {
        return mixedUriSchemas.get(uri);
    }

    public boolean contains(URI uri) {
        return mixedUriSchemas.containsKey(uri);
    }

    public Set<URI> uris() {
        return mixedUriSchemas.keySet();
    }


    /// Global Schemas
    private static final SchemaStore GLOBAL_STORE = new SchemaStore();
    public static ObjectSchema globalResolve(URI uri) {
        return GLOBAL_STORE.resolve(uri);
    }

    private static final String JSON_SCHEMAS_DIR = "json-schemas/";
    static {
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/core.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/applicator.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/validation.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/meta-data.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/format-annotation.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/unevaluated.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/meta/content.json");
        registerGlobalSchema(JSON_SCHEMAS_DIR + "draft2020-12/schema.json");
    }

    private static void registerGlobalSchema(String resourcePath) {
        ObjectSchema schema = loadSchemaFromResource(resourcePath);
        if (schema == null) throw new SchemaException("Not found global schema: " + resourcePath);
        if (!GLOBAL_STORE.register(schema)) {
            throw new SchemaException("Failed to register global schema: " + resourcePath);
        }
    }

//    public static ObjectSchema loadSchemaByPath(String path) {
//        if (path == null || path.isEmpty()) return null;
//        URI uri = URI.create(path);
//        if (uri.isAbsolute()) {
//            if ("file".equalsIgnoreCase(uri.getScheme())) {
//                try (InputStream in = Files.newInputStream(Paths.get(uri))) {
//                    return Sjf4j.fromJson(in, ObjectSchema.class);
//                } catch (Exception e) {
//                    throw new SchemaException("Failed to load schema file: " + uri, e);
//                }
//            }
//            if ("classpath".equalsIgnoreCase(uri.getScheme())) {
//                try (InputStream in = SchemaStore.class.getClassLoader().getResourceAsStream(uri.getPath())) {
//                    if (in == null) {
//                        throw new SchemaException("Schema resource file not found: " + uri);
//                    }
//                    return Sjf4j.fromJson(in, ObjectSchema.class);
//                } catch (Exception e) {
//                    throw new SchemaException("Failed to load schema file: " + uri, e);
//                }
//            }
//            throw new SchemaException("Unsupported schema path: " + uri);
//        }
//
//        String resourcePath = JSON_SCHEMAS_DIR + uri.getPath();
//        try (InputStream in = SchemaStore.class.getClassLoader().getResourceAsStream(resourcePath)) {
//            if (in == null) {
//                throw new SchemaException("Schema resource file not found: " + resourcePath);
//            }
//            return Sjf4j.fromJson(in, ObjectSchema.class);
//        } catch (Exception e) {
//            throw new SchemaException("Failed to load schema file: " + resourcePath, e);
//        }
//    }


    public static ObjectSchema loadSchemaFromLocalUri(URI uri) {
        Objects.requireNonNull(uri, "uri is null");
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return loadSchemaFromFile(uri.getPath());
        }
        if ("classpath".equalsIgnoreCase(uri.getScheme())) {
            return loadSchemaFromResource(uri.getPath());
        }
        throw new SchemaException("Unsupported local schema uri: " + uri);
    }

    public static ObjectSchema loadSchemaFromFile(String filePath) {
        try (InputStream in = Files.newInputStream(Paths.get(filePath))) {
            return Sjf4j.fromJson(in, ObjectSchema.class);
        } catch (Exception e) {
            throw new SchemaException("Failed to load schema file: " + filePath, e);
        }
    }

    public static ObjectSchema loadSchemaFromResource(String resourcePath) {
        if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);
        try (InputStream in = SchemaStore.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new SchemaException("Not found schema resource: " + resourcePath);
            return Sjf4j.fromJson(in, ObjectSchema.class);
        } catch (Exception e) {
            throw new SchemaException("Failed to load schema resource: " + resourcePath, e);
        }
    }




}
