package org.sjf4j.schema;

import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared schema resources, including the official meta-schemas.
 * Register custom resources during application initialization, not concurrently.
 * Indexed definitions must not be modified or replaced. Plans are compiled and
 * cached only in the registry that resolves them.
 */
public final class GlobalSchemas {

    private static final Map<String, SchemaResource> RESOURCES = new ConcurrentHashMap<>();

    private GlobalSchemas() {}

    /** Indexes a schema with an absolute declared $id. */
    public static void index(JsonSchema schema) {
        index(null, schema);
    }

    /** Indexes a schema with its retrieval URI, without compiling it. */
    public static void index(URI retrievalUri, JsonSchema schema) {
        SchemaResource resource = new SchemaResource(retrievalUri, schema);
        URI canonicalUri = resource.canonicalUri;
        if (canonicalUri == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_URI,
                    "missing root schema uri: specify $id or a retrieval URI",
                    PathSegment.Root.INSTANCE, null));
        }
        SchemaUtil.checkResourceUri(canonicalUri);
        String canonical = SchemaUtil.normalizeUriKey(canonicalUri);
        String retrieval = canonical;
        if (resource.retrievalUri != null) {
            SchemaUtil.checkResourceUri(resource.retrievalUri);
            retrieval = SchemaUtil.normalizeUriKey(resource.retrievalUri);
        }
        SchemaResource existing = RESOURCES.get(canonical);
        if (existing != null) {
            if (!existing.sameResource(resource)) throw _conflict(canonical);
            resource = existing;
        }
        existing = RESOURCES.get(retrieval);
        if (existing != null && !existing.sameResource(resource)) {
            throw _conflict(retrieval);
        }
        RESOURCES.putIfAbsent(canonical, resource);
        RESOURCES.putIfAbsent(retrieval, resource);
    }

    /** Finds a resource by its normalized URI key. */
    static SchemaResource find(String id) {
        return RESOURCES.get(id);
    }

    private static SchemaException _conflict(String id) {
        return new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                "duplicate schema uri '" + id + "'", PathSegment.Root.INSTANCE, URI.create(id)));
    }

    private static final SchemaLoader.Local BUILTIN_SCHEMA_LOADER =
            new SchemaLoader.Local(SchemaLoader.DEFAULT_JSON_SCHEMA_DIR);

    static {
        _indexBuiltin("draft07/schema.json", "https://json-schema.org/draft-07/schema");

        _indexBuiltin("draft2019-09/meta/core.json", null);
        _indexBuiltin("draft2019-09/meta/applicator.json", null);
        _indexBuiltin("draft2019-09/meta/validation.json", null);
        _indexBuiltin("draft2019-09/meta/meta-data.json", null);
        _indexBuiltin("draft2019-09/meta/format.json", null);
        _indexBuiltin("draft2019-09/meta/content.json", null);
        _indexBuiltin("draft2019-09/schema.json", null);

        _indexBuiltin("draft2020-12/meta/core.json", null);
        _indexBuiltin("draft2020-12/meta/applicator.json", null);
        _indexBuiltin("draft2020-12/meta/validation.json", null);
        _indexBuiltin("draft2020-12/meta/meta-data.json", null);
        _indexBuiltin("draft2020-12/meta/format-annotation.json", null);
        _indexBuiltin("draft2020-12/meta/format-assertion.json", null);
        _indexBuiltin("draft2020-12/meta/unevaluated.json", null);
        _indexBuiltin("draft2020-12/meta/content.json", null);
        _indexBuiltin("draft2020-12/schema.json", null);
    }


    private static void _indexBuiltin(String filePath, String alias) {
        URI uri = SchemaLoader.DEFAULT_JSON_SCHEMA_DIR.resolve(filePath);
        JsonSchema schema = BUILTIN_SCHEMA_LOADER.load(uri);
        if (schema == null) throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                "global schema not found", PathSegment.Root.INSTANCE, uri));
        if (!(schema instanceof ObjectSchema)) throw new SchemaException(SchemaUtil.formatSchemaLine(
                SchemaUtil.Code.SCHEMA_LOAD, "global schema must be object-shaped",
                PathSegment.Root.INSTANCE, uri));
        index(uri, schema);
        if (alias != null) index(URI.create(alias), schema);
    }
}
