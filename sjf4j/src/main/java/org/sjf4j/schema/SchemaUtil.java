package org.sjf4j.schema;

import org.sjf4j.JsonException;
import org.sjf4j.JsonType;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathToken;
import org.sjf4j.util.NodeUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class SchemaUtil {

    public static void checkValidKeywords(JsonSchema schema, String path, Map<String, Boolean> vocabulary) {
        for (String property : schema.keySet()) {
            String vocabUri = VocabularyRegistry.getVocabUri(property);
            if (vocabUri == null)
                throw new SchemaException("Unrecognized schema keyword '" + property + "' at " + path +
                        " . No registered vocabulary claims support for it.\n");
            if (vocabulary != null) {
                Boolean allow = vocabulary.get(vocabUri);
                if (allow != null && !allow)
                    throw new SchemaException("Keyword '" + property + "' at " + path +
                            " is disallowed by declared vocabulary " + vocabUri);
            }
        }
    }

    public static Evaluator[] compile(JsonSchema schema, JsonPointer path, JsonSchema rootSchema) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(rootSchema);
        checkValidKeywords(schema, path.toExpr(), rootSchema.getVocabulary());

        // $defs
        compileSchemaMapByKey(schema, "$defs", path, rootSchema);
        compileSchemaMapByKey(schema, "definitions", path, rootSchema);

        // $anchor
        String anchor = schema.getString("$anchor");
        if (anchor != null) {
            rootSchema.putAnchor(anchor, schema);
        }

        // $ref
        String ref = schema.getString("$ref");
        if (ref != null) {
            URI uri = rootSchema.getUri().resolve(ref);
            String fragment = uri.getFragment();
            String refAnchor = fragment;
            JsonPointer refPath = null;
            if (fragment == null) refAnchor = "";
            else if (fragment.startsWith("/")) refPath = JsonPointer.compile(fragment);
            // Must ignore other evaluators
            return new Evaluator[]{
                    new Evaluator.RefEvaluator(URI.create(uri.getSchemeSpecificPart()), refPath, refAnchor)};
        }

        // $dynamicRef
        String dynamicRef = schema.getString("$dynamicRef");
        if (dynamicRef != null) {
            if (!dynamicRef.startsWith("#")) {
                throw new SchemaException("Invalid $dynamicRef '" + dynamicRef + "' at " + path +
                        " : must start with '#'");
            }
            String dynamicAnchor = dynamicRef.substring(1);
            // Must ignore other evaluators
            return new Evaluator[]{new Evaluator.DynamicRefEvaluator(dynamicAnchor)};
        }

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
        Map<String, Object> properties = compileSchemaMapByKey(schema, "properties", path, rootSchema);
        Map<String, Object> patternProperties = compileSchemaMapByKey(schema, "patternProperties", path, rootSchema);
        Object additionalProperties = compileSchemaByKey(schema, "additionalProperties", path, rootSchema);
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
        Map<String, Object> dependentSchemas = compileSchemaMapByKey(schema, "dependentSchemas", path, rootSchema);
        if (dependentSchemas != null) {
            evaluators.add(new Evaluator.DependentSchemasEvaluator(dependentSchemas));
        }

        // propertyNames
        Object propertyNames = compileSchemaByKey(schema, "propertyNames", path, rootSchema);
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
        Object items = compileSchemaByKey(schema, "items", path, rootSchema);
        Object[] prefixItems = compileSchemaArrayByKey(schema, "prefixItems", path, rootSchema);
        if (items != null || prefixItems != null) {
            evaluators.add(new Evaluator.ItemsEvaluator(items, prefixItems));
        }

        // contains / minContains / maxContains
        Object contains = compileSchemaByKey(schema, "contains", path, rootSchema);
        Integer minContains = schema.getInteger("minContains");
        Integer maxContains = schema.getInteger("maxContains");
        if (contains != null || minContains != null || maxContains != null) {
            evaluators.add(new Evaluator.ContainsEvaluator(contains, minContains, maxContains));
        }

        // if / then / else
        Object ifSchema = compileSchemaByKey(schema, "if", path, rootSchema);
        Object thenSchema = compileSchemaByKey(schema, "then", path, rootSchema);
        Object elseSchema = compileSchemaByKey(schema, "else", path, rootSchema);
        if (ifSchema != null || thenSchema != null || elseSchema != null) {
            evaluators.add(new Evaluator.IfThenElseEvaluator(ifSchema, thenSchema, elseSchema));
        }

        // allOf
        Object[] allOfSchemas = compileSchemaArrayByKey(schema, "allOf", path, rootSchema);
        if (allOfSchemas != null) {
            evaluators.add(new Evaluator.AllOfEvaluator(allOfSchemas));
        }

        // anyOf
        Object[] anyOfSchemas = compileSchemaArrayByKey(schema, "anyOf", path, rootSchema);
        if (anyOfSchemas != null) {
            evaluators.add(new Evaluator.AnyOfEvaluator(anyOfSchemas));
        }

        // oneOf
        Object[] oneOfSchemas = compileSchemaArrayByKey(schema, "oneOf", path, rootSchema);
        if (oneOfSchemas != null) {
            evaluators.add(new Evaluator.OneOfEvaluator(oneOfSchemas));
        }

        // not
        Object notSchema = compileSchemaByKey(schema, "not", path, rootSchema);
        if (notSchema != null) {
            evaluators.add(new Evaluator.NotEvaluator(notSchema));
        }

        // unevaluatedProperties / unevaluatedItems
        Object unevaluatedPropertiesSchema = compileSchemaByKey(schema, "unevaluatedProperties", path, rootSchema);
        Object unevaluatedItemsSchema = compileSchemaByKey(schema, "unevaluatedItems", path, rootSchema);
        if (unevaluatedPropertiesSchema != null || unevaluatedItemsSchema != null) {
            evaluators.add(new Evaluator.UnevaluatedEvaluator(unevaluatedPropertiesSchema, unevaluatedItemsSchema));
        }

        return evaluators.toArray(new Evaluator[0]);
    }


    private static Map<String, Object> compileSchemaMapByKey(
            JsonSchema schema, String key, JsonPointer path, JsonSchema rootSchema) {
        Object schemaMapNode = schema.getNode(key);
        if (schemaMapNode == null) return null;

        path.push(new PathToken.Name(key));
        if (!NodeType.of(schemaMapNode).isObject())
            throw new SchemaException("Node at " + path + " must be a JSON Object");
        for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(schemaMapNode)) {
            Object subNode = entry.getValue();
            path.push(new PathToken.Name(entry.getKey()));
            Object subSchema = compileSchema(subNode, path, rootSchema);
            path.pop();
            if (subSchema != subNode) entry.setValue(subSchema);
        }
        path.pop();
        return NodeUtil.asMap(schemaMapNode);
    }

    private static Object[] compileSchemaArrayByKey(JsonSchema schema, String key, JsonPointer path,
                                                    JsonSchema rootSchema) {
        Object schemaArrayNode = schema.getNode(key);
        if (schemaArrayNode == null) return null;

        path.push(new PathToken.Name(key));
        if (!NodeType.of(schemaArrayNode).isArray())
            throw new SchemaException("Node at " + path + " must be a JSON Array");
        int size = NodeWalker.sizeInArray(schemaArrayNode);
        for (int i = 0; i < size; i++) {
            Object subNode = NodeWalker.getInArray(schemaArrayNode, i);
            if (subNode == null) continue;
            path.push(new PathToken.Index(i));
            Object subSchema = compileSchema(subNode, path, rootSchema);
            path.pop();
            if (subSchema != subNode) NodeWalker.setInArray(schemaArrayNode, i, subSchema);
        }
        return NodeUtil.asArray(schemaArrayNode);
    }

    private static Object compileSchemaByKey(JsonSchema schema, String key,
                                             JsonPointer path, JsonSchema rootSchema) {
        Object subNode = schema.getNode(key);
        if (subNode == null) return null;
        path.push(new PathToken.Name(key));
        Object subSchema = compileSchema(subNode, path, rootSchema);
        path.pop();
        if (subSchema != subNode) schema.put(key, subSchema);
        return subSchema;
    }

    private static Object compileSchema(Object schemaNode, JsonPointer path, JsonSchema rootSchema) {
        JsonType jt = JsonType.of(schemaNode);
        switch (jt) {
            case NULL: return null;
            case BOOLEAN: return schemaNode;
            case OBJECT: {
                JsonSchema schema = new JsonSchema(schemaNode);
                schema.compile(path, rootSchema);
                return schema;
            }
            default: throw new SchemaException("Invalid schema at " + path + " : node type is " + jt);
        }
    }


}
