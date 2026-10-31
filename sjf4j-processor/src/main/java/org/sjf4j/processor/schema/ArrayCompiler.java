package org.sjf4j.processor.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.processor.ProcessorContext;

/** Compiles array size and recursive item constraints with local fallback. */
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
            if (!key.equals("type") && !key.equals("items") && !key.equals("minItems")
                    && !key.equals("maxItems") && !SchemaCompiler.isAnnotation(key)) return null;
        }
        Integer min = schema.getInt("minItems");
        Integer max = schema.getInt("maxItems");
        if ((schema.containsKey("minItems") && (min == null || min < 0))
                || (schema.containsKey("maxItems") && (max == null || max < 0))) return null;
        SchemaCheck itemCheck = null;
        SchemaValue itemValue = null;
        if (schema.containsKey("items")) {
            String itemUri = SchemaCompiler.keywordUri(schemaUri, "items");
            itemValue = SchemaValue.of(context, value.itemType());
            itemCheck = schemaCompiler.compileValue(snapshot, schema.getNode("items"), itemValue, itemUri, depth + 1);
            if (itemCheck == null) itemCheck = new SchemaCheck.Fallback(itemUri);
        }
        return new SchemaCheck.ArrayCheck(min, max, itemCheck, itemValue);
    }
}
