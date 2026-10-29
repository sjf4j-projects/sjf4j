package org.sjf4j.schema;

import org.sjf4j.annotation.schema.ValidJsonSchema;
import java.lang.reflect.Method;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.path.PathSegment;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves compiled validator fallbacks from the original annotations, rather
 * than embedding a second copy of the JSON Schema into generated Java source.
 */
public final class CompiledValidatorFallback {
    private CompiledValidatorFallback() {}

    public static SchemaPlan plan(Class<?> validator, String method, Class<?> argumentType, int ordinal) {
        try {
            Method declaration = validator.getMethod(method, argumentType);
            ValidJsonSchema schema = declaration.getAnnotation(ValidJsonSchema.class);
            if (schema == null) schema = declaration.getParameters()[0].getAnnotation(ValidJsonSchema.class);
            if (schema != null) {
                if (ordinal != 0) throw new IllegalArgumentException("invalid schema ordinal: " + ordinal);
                return resolve(schema, argumentType, validator.getName() + "/" + method);
            }

            List<Class<?>> hierarchy = new ArrayList<Class<?>>();
            for (Class<?> type = argumentType; type != null && type != Object.class; type = type.getSuperclass()) {
                if (type.isAnnotationPresent(ValidJsonSchema.class)) hierarchy.add(0, type);
            }
            Class<?> owner = hierarchy.get(ordinal);
            return resolve(owner.getAnnotation(ValidJsonSchema.class), owner, owner.getName());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("cannot resolve compiled validator schema", ex);
        }
    }

    private static SchemaPlan resolve(ValidJsonSchema annotation, Class<?> owner, String source) {
        String inline = annotation.value();
        if (!inline.isEmpty()) {
            return new SchemaRegistry().register(URI.create("sjf4j:/" + source + "/"),
                    JsonSchema.fromJson(inline));
        }

        String ref = annotation.ref().trim();
        if (!ref.isEmpty()) return fromReference(ref);
        String simple = owner.getSimpleName();
        String byName = simple + ".json";
        if (exists(byName)) return fromReference(byName);
        return fromReference(org.sjf4j.util.Strings.toSnakeCase(simple) + ".json");
    }

    private static boolean exists(String relative) {
        String path = "json-schemas/" + relative;
        return CompiledValidatorFallback.class.getClassLoader().getResource(path) != null;
    }

    private static SchemaPlan fromReference(String ref) {
        URI requested = URI.create("classpath:/json-schemas/").resolve(ref);
        URI document = stripFragment(requested);
        SchemaRegistry registry = new SchemaRegistry();
        Map<URI, JsonSchema> resources = new LinkedHashMap<URI, JsonSchema>();
        index(document, resources);
        for (Map.Entry<URI, JsonSchema> resource : resources.entrySet()) {
            URI uri = resource.getKey();
            JsonSchema parsed = resource.getValue();
            if (parsed instanceof BooleanSchema) {
                registry.putPlan(uri, SchemaPlan.of(uri, PathSegment.Root.INSTANCE, (BooleanSchema) parsed));
            } else {
                registry.index(uri, parsed);
            }
        }
        return require(registry.resolve(requested), ref);
    }

    private static void index(URI uri, Map<URI, JsonSchema> resources) {
        if (resources.containsKey(uri)) return;
        String text;
        try {
            if ("classpath".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath().replaceFirst("^/", "");
                try (InputStream stream = CompiledValidatorFallback.class.getClassLoader().getResourceAsStream(path)) {
                    if (stream == null) throw new IllegalStateException("schema not found: " + uri);
                    text = new String(readAll(stream), StandardCharsets.UTF_8);
                }
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                text = new String(Files.readAllBytes(Paths.get(uri)), StandardCharsets.UTF_8);
            } else {
                throw new IllegalStateException("unsupported schema URI: " + uri);
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("cannot read schema: " + uri, ex);
        }
        JsonSchema parsed = JsonSchema.fromJson(text);
        resources.put(uri, parsed);
        scan(parsed, uri, resources);
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static URI stripFragment(URI uri) {
        String text = uri.toString();
        int hash = text.indexOf('#');
        return hash < 0 ? uri : URI.create(text.substring(0, hash));
    }

    private static void scan(Object node, URI base, Map<URI, JsonSchema> resources) {
        JsonType kind = JsonType.of(node);
        if (kind == JsonType.ARRAY) {
            for (Object child : Nodes.toList(node)) scan(child, base, resources);
            return;
        }
        if (kind != JsonType.OBJECT) return;

        Object id = Nodes.getInObject(node, "$id");
        URI current = id instanceof String ? base.resolve((String) id) : base;
        for (String keyword : new String[]{"$ref", "$dynamicRef", "$recursiveRef"}) {
            Object reference = Nodes.getInObject(node, keyword);
            if (!(reference instanceof String)) continue;
            URI document = stripFragment(current.resolve((String) reference));
            if (document.equals(stripFragment(current))) continue;
            if ("classpath".equals(document.getScheme()) || "file".equals(document.getScheme())) {
                index(document, resources);
            }
        }
        for (String keyword : new String[]{"$defs", "definitions", "properties",
                "patternProperties", "dependentSchemas", "dependencies"}) {
            Object group = Nodes.getInObject(node, keyword);
            if (JsonType.of(group) != JsonType.OBJECT) continue;
            for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(group)) {
                scan(entry.getValue(), current, resources);
            }
        }
        for (String keyword : new String[]{"items", "additionalItems", "additionalProperties",
                "contains", "propertyNames", "not", "if", "then", "else",
                "unevaluatedItems", "unevaluatedProperties", "allOf", "anyOf",
                "oneOf", "prefixItems", "contentSchema"}) {
            Object child = Nodes.getInObject(node, keyword);
            if (child != null) scan(child, current, resources);
        }
    }

    private static SchemaPlan require(SchemaPlan plan, String ref) {
        if (plan == null) throw new IllegalStateException("schema not found: " + ref);
        return plan;
    }
}
