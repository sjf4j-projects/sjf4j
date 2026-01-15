package org.sjf4j.schema;

import org.sjf4j.JsonException;
import org.sjf4j.JsonType;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.util.NodeUtil;

import javax.xml.validation.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SchemaUtil {

    public static Evaluator[] compile(JsonSchema schema) {
        Objects.requireNonNull(schema);
        List<Evaluator> evaluators = new ArrayList<>();

        // type
        Object type = schema.getNode("type");
        if (type != null) {
            evaluators.add(new Evaluator.TypeEvaluator(type));
        }

        // const
        if (schema.containsKey("const")) {
            evaluators.add(new Evaluator.ConstEvaluator(schema.getNode("const")));
        }

        // enum
        Object[] enumValues = schema.getArray("enum");
        if (enumValues != null) {
            evaluators.add(new Evaluator.EnumEvaluator(enumValues));
        }

        // minimum maximum exclusiveMinimum exclusiveMaximum
        Number minimum = schema.getNumber("minimum");
        Number maximum = schema.getNumber("maximum");
        Number exclusiveMinimum = schema.getNumber("exclusiveMinimum");
        Number exclusiveMaximum = schema.getNumber("exclusiveMaximum");
        if (minimum != null || maximum != null || exclusiveMinimum != null || exclusiveMaximum != null) {
            evaluators.add(new Evaluator.NumberEvaluator(minimum, maximum, exclusiveMinimum, exclusiveMaximum));
        }

        // multipleOf
        Number multipleOf = schema.getNumber("multipleOf");
        if (multipleOf != null) {
            evaluators.add(new Evaluator.MultipleOfEvaluator(multipleOf));
        }

        // minLength maxLength
        Integer minLength = schema.getInteger("minLength");
        Integer maxLength = schema.getInteger("maxLength");
        if (minLength != null || maxLength != null) {
            evaluators.add(new Evaluator.StringEvaluator(minLength, maxLength));
        }

        // pattern
        String pattern = schema.getString("pattern");
        if (pattern != null) {
            evaluators.add(new Evaluator.PatternEvaluator(pattern));
        }

        // format
        String format = schema.getString("format");
        if (format != null) {
            evaluators.add(new Evaluator.FormatEvaluator(format));
        }

        // minProperties / maxProperties
        Integer minProperties = schema.getInteger("minProperties");
        Integer maxProperties = schema.getInteger("maxProperties");
        if (minProperties != null || maxProperties != null) {
            evaluators.add(new Evaluator.ObjectEvaluator(minProperties, maxProperties));
        }

        // required / properties / patternProperties / additionalProperties
        String[] required = schema.asArray("required", String.class);
        Map<String, Object> properties = compileSchemaMap(schema.getNode("properties"));
        Map<String, Object> patternProperties = compileSchemaMap(schema.getNode("patternProperties"));
        Object additionalProperties = compileSchemaByKey(schema, "additionalProperties");
        if (required != null || properties != null || patternProperties != null ||
                additionalProperties != null) {
            evaluators.add(new Evaluator.PropertiesEvaluator(required, properties, patternProperties,
                    additionalProperties));
        }

        // dependentRequired
        Map<String, String[]> dependentRequired = schema.asMap("dependentRequired", String[].class);
        if (dependentRequired != null) {
            evaluators.add(new Evaluator.DependentRequiredEvaluator(dependentRequired));
        }

        // dependentSchemas
        Map<String, Object> dependentSchemas = compileSchemaMap(schema.getNode("dependentSchemas"));
        if (dependentSchemas != null) {
            evaluators.add(new Evaluator.DependentSchemasEvaluator(dependentSchemas));
        }

        // propertyNames
        Object propertyNames = compileSchemaByKey(schema, "propertyNames");
        if (propertyNames != null) {
            evaluators.add(new Evaluator.PropertyNamesEvaluator(propertyNames));
        }

        // minItems / maxItems / uniqueItems
        Integer minItems = schema.getInteger("minItems");
        Integer maxItems = schema.getInteger("maxItems");
        Boolean uniqueItems = schema.getBoolean("uniqueItems");
        if (minItems != null || maxItems != null || uniqueItems != null) {
            evaluators.add(new Evaluator.ArrayEvaluator(minItems, maxItems, uniqueItems));
        }

        // items / prefixItems
        Object items = compileSchemaByKey(schema, "items");
        Object[] prefixItems = compileSchemaArray(schema.getNode("prefixItems"));
        if (items != null || prefixItems != null) {
            evaluators.add(new Evaluator.ItemsEvaluator(items, prefixItems));
        }

        // contains / minContains / maxContains
        Object contains = compileSchemaByKey(schema, "contains");
        Integer minContains = schema.getInteger("minContains");
        Integer maxContains = schema.getInteger("maxContains");
        if (contains != null || minContains != null || maxContains != null) {
            evaluators.add(new Evaluator.ContainsEvaluator(contains, minContains, maxContains));
        }

        // if / then / else
        Object ifSchema = compileSchemaByKey(schema, "if");
        Object thenSchema = compileSchemaByKey(schema, "then");
        Object elseSchema = compileSchemaByKey(schema, "else");
        if (ifSchema != null || thenSchema != null || elseSchema != null) {
            evaluators.add(new Evaluator.IfThenElseEvaluator(ifSchema, thenSchema, elseSchema));
        }

        // allOf
        Object[] allOfSchemas = compileSchemaArray(schema.getNode("allOf"));
        if (allOfSchemas != null) {
            evaluators.add(new Evaluator.AllOfEvaluator(allOfSchemas));
        }

        // anyOf
        Object[] anyOfSchemas = compileSchemaArray(schema.getNode("anyOf"));
        if (anyOfSchemas != null) {
            evaluators.add(new Evaluator.AnyOfEvaluator(anyOfSchemas));
        }

        // oneOf
        Object[] oneOfSchemas = compileSchemaArray(schema.getNode("oneOf"));
        if (oneOfSchemas != null) {
            evaluators.add(new Evaluator.OneOfEvaluator(oneOfSchemas));
        }

        // not
        Object notSchema = compileSchemaByKey(schema, "not");
        if (notSchema != null) {
            evaluators.add(new Evaluator.NotEvaluator(notSchema));
        }

        // unevaluatedProperties / unevaluatedItems
        Object unevaluatedPropertiesSchema = compileSchemaByKey(schema, "unevaluatedProperties");
        Object unevaluatedItemsSchema = compileSchemaByKey(schema, "unevaluatedItems");
        if (unevaluatedPropertiesSchema != null || unevaluatedItemsSchema != null) {
            evaluators.add(new Evaluator.UnevaluatedEvaluator(unevaluatedPropertiesSchema, unevaluatedItemsSchema));
        }

        return evaluators.toArray(new Evaluator[0]);
    }


    private static Map<String, Object> compileSchemaMap(Object schemaMapNode) {
        if (schemaMapNode == null) return null;
        if (!NodeType.of(schemaMapNode).isObject()) throw new JsonException("schemaMapNode is not an JSON Object");

        for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(schemaMapNode)) {
            Object subNode = entry.getValue();
            Object subSchema = compileSchema(subNode);
            if (subSchema != subNode) entry.setValue(subSchema);
        }
        return NodeUtil.asMap(schemaMapNode);
    }

    private static Object[] compileSchemaArray(Object schemaArrayNode) {
        if (schemaArrayNode == null) return null;
        if (!NodeType.of(schemaArrayNode).isArray()) throw new JsonException("schemaArrayNode is not an JSON Array");

        int size = NodeWalker.sizeInArray(schemaArrayNode);
        for (int i = 0; i < size; i++) {
            Object subNode = NodeWalker.getInArray(schemaArrayNode, i);
            Object subSchema = compileSchema(subNode);
            if (subSchema != subNode) NodeWalker.setInArray(schemaArrayNode, i, subSchema);
        }
        return NodeUtil.asArray(schemaArrayNode);
    }

    private static Object compileSchemaByKey(JsonSchema schema, String key) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(key);
        Object subNode = schema.getNode(key);
        if (subNode == null) return null;

        Object subSchema = compileSchema(subNode);
        if (subSchema != subNode) schema.put(key, subSchema);
        return subSchema;
    }

    private static Object compileSchema(Object schemaNode) {
        JsonType jt = JsonType.of(schemaNode);
        switch (jt) {
            case NULL: return null;
            case BOOLEAN: return schemaNode;
            case OBJECT: {
                JsonSchema schema = new JsonSchema(schemaNode);
                schema.compile();
                return schema;
            }
            default: throw new JsonException("Failed to compile JSON Schema: invalid type " + jt);
        }
    }
}
