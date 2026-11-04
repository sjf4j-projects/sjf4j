package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.processor.ProcessorContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles independent array keywords. prefixItems and items share only the
 * index scope required by JSON Schema: items applies after local prefixItems.
 */
final class ArrayCompiler {
    private final ProcessorContext context;
    private final SchemaCompiler schemaCompiler;

    ArrayCompiler(ProcessorContext context, SchemaCompiler schemaCompiler) {
        this.context = context;
        this.schemaCompiler = schemaCompiler;
    }

    SchemaCheck compile(SchemaResourceResolver.Snapshot snapshot, JsonObject schema,
                        SchemaValue value, String schemaUri, int depth) {
        if (!value.isArrayLike() && value.shape != SchemaValue.Shape.DYNAMIC) return null;
        for (String key : schema.keySet()) {
            if (!key.equals("items") && !key.equals("prefixItems")
                    && !key.equals("minItems") && !key.equals("maxItems")
                    && !key.equals("uniqueItems") && !SchemaCompiler.isAnnotation(key)) return null;
        }
        Integer min = schema.getInt("minItems");
        Integer max = schema.getInt("maxItems");
        if ((schema.containsKey("minItems") && (min == null || min < 0))
                || (schema.containsKey("maxItems") && (max == null || max < 0))) return null;
        boolean unique = false;
        if (schema.containsKey("uniqueItems")) {
            Object keyword = schema.getNode("uniqueItems");
            if (!(keyword instanceof Boolean)) return null;
            unique = Boolean.TRUE.equals(keyword);
        }
        SchemaValue itemValue = SchemaValue.of(context, value.itemType());
        List<SchemaCheck> prefixChecks = new ArrayList<SchemaCheck>();
        if (schema.containsKey("prefixItems")) {
            Object prefixRaw = schema.getNode("prefixItems");
            if (JsonType.of(prefixRaw) != JsonType.ARRAY) return null;
            String prefixUri = SchemaCompiler.keywordUri(schemaUri, "prefixItems");
            for (int i = 0, n = Nodes.sizeInArray(prefixRaw); i < n; i++) {
                String childUri = prefixUri + "/" + i;
                SchemaCheck child = schemaCompiler.compileValue(snapshot,
                        Nodes.getInArray(prefixRaw, i), itemValue, childUri, depth + 1);
                prefixChecks.add(child == null ? new SchemaCheck.Fallback(childUri) : child);
            }
        }
        SchemaCheck itemCheck = null;
        if (schema.containsKey("items")) {
            String itemUri = SchemaCompiler.keywordUri(schemaUri, "items");
            itemCheck = schemaCompiler.compileValue(snapshot, schema.getNode("items"),
                    itemValue, itemUri, depth + 1);
            if (itemCheck == null) itemCheck = new SchemaCheck.Fallback(itemUri);
        }
        return new SchemaCheck.ArrayCheck(min, max, prefixChecks, itemCheck, itemValue, unique,
                SchemaCompiler.diagnostic(schemaUri, "type"),
                min == null ? null : SchemaCompiler.diagnostic(schemaUri, "minItems"),
                max == null ? null : SchemaCompiler.diagnostic(schemaUri, "maxItems"),
                unique ? SchemaCompiler.diagnostic(schemaUri, "uniqueItems") : null);
    }
}
