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
        // For an independent schema with no contains, prefixItems evaluates
        // its prefix and unevaluatedItems covers exactly the remaining suffix.
        String itemKeyword = schema.containsKey("unevaluatedItems")
                ? "unevaluatedItems" : "items";
        for (String key : schema.keySet()) {
            if (!key.equals("items") && !key.equals("unevaluatedItems") && !key.equals("prefixItems")
                    && !key.equals("minItems") && !key.equals("maxItems")
                    && !key.equals("uniqueItems") && !key.equals("contains")
                    && !key.equals("minContains") && !key.equals("maxContains")
                    && !SchemaCompiler.isAnnotation(key)) return null;
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
        if (schema.containsKey(itemKeyword)) {
            String itemUri = SchemaCompiler.keywordUri(schemaUri, itemKeyword);
            itemCheck = schemaCompiler.compileValue(snapshot, schema.getNode(itemKeyword),
                    itemValue, itemUri, depth + 1);
            if (itemCheck == null) itemCheck = new SchemaCheck.Fallback(itemUri);
        }
        // minContains/maxContains are ignored in the absence of contains.
        // With contains, a missing minContains means at least one match.
        SchemaCheck containsCheck = null;
        int minContains = 1;
        Integer maxContains = null;
        if (schema.containsKey("contains")) {
            Object minRaw = schema.getNode("minContains");
            Object maxRaw = schema.getNode("maxContains");
            if (schema.containsKey("minContains")) {
                minContains = nonNegativeInteger(minRaw);
                if (minContains < 0) return null;
            }
            if (schema.containsKey("maxContains")) {
                int upper = nonNegativeInteger(maxRaw);
                if (upper < 0) return null;
                maxContains = upper;
            }
            String containsUri = SchemaCompiler.keywordUri(schemaUri, "contains");
            containsCheck = schemaCompiler.compileValue(snapshot, schema.getNode("contains"),
                    itemValue, containsUri, depth + 1);
            if (containsCheck == null) containsCheck = new SchemaCheck.Fallback(containsUri);
        }
        return new SchemaCheck.ArrayCheck(min, max, prefixChecks, itemCheck, itemValue, unique,
                containsCheck, minContains, maxContains,
                SchemaCompiler.diagnostic(schemaUri,
                        schema.containsKey("minContains") ? "minContains" : "contains"),
                maxContains == null ? null : SchemaCompiler.diagnostic(schemaUri, "maxContains"),
                SchemaCompiler.diagnostic(schemaUri, "type"),
                min == null ? null : SchemaCompiler.diagnostic(schemaUri, "minItems"),
                max == null ? null : SchemaCompiler.diagnostic(schemaUri, "maxItems"),
                unique ? SchemaCompiler.diagnostic(schemaUri, "uniqueItems") : null);
    }

    private static int nonNegativeInteger(Object value) {
        if (!(value instanceof Number)) return -1;
        try {
            java.math.BigDecimal decimal = new java.math.BigDecimal(value.toString());
            return decimal.scale() <= 0 || decimal.stripTrailingZeros().scale() <= 0
                    ? decimal.intValueExact() : -1;
        } catch (NumberFormatException | ArithmeticException ex) {
            return -1;
        }
    }
}
