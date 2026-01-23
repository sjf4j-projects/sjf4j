package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathToken;
import org.sjf4j.util.NodeUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class SchemaUtil {


    static void checkVocabulary(JsonPointer path, ObjectSchema schema, Map<String, Boolean> vocabulary) {
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

    static Evaluator[] compile(JsonPointer path,
                                      ObjectSchema schema,
                                      ObjectSchema idSchema,
                                      ObjectSchema rootSchema) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(idSchema);
        Objects.requireNonNull(rootSchema);

        checkVocabulary(path, schema, idSchema.getVocabulary());

        // $defs
        compileSchemaMapByKey("$defs", path, schema, idSchema, rootSchema);
        compileSchemaMapByKey("definitions", path, schema, idSchema, rootSchema);

        // $anchor
        String anchor = schema.getString("$anchor");
        if (anchor != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_CORE)) {
            idSchema.putAnchor(anchor, schema);
        }

        // $dynamicAnchor
        String dynamicAnchor = schema.getString("$dynamicAnchor");
        if (dynamicAnchor != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_CORE)) {
            idSchema.putDynamicAnchor(dynamicAnchor, schema);
        }

        List<Evaluator> evaluators = new ArrayList<>();
        // $ref
        String ref = schema.getString("$ref");
        if (ref != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_CORE)) {
            URI uri = resolveUri(ref, idSchema.getUri());
            URI dropedUri = dropFragment(uri);
            if (!dropedUri.toString().isEmpty()) {
                ObjectSchema refSchema = rootSchema.importAndCompile(dropedUri);
                idSchema.importSchema(dropedUri, refSchema);
            }

            String fragment = URI.create(ref).getFragment();
            String refAnchor = fragment;
            JsonPointer refPath = null;
            if (fragment == null) refAnchor = "";
            else if (fragment.startsWith("/")) refPath = JsonPointer.compile(fragment);

            evaluators.add(new Evaluator.RefEvaluator(dropedUri, refPath, refAnchor));
        }

        // $dynamicRef
        String dynamicRef = schema.getString("$dynamicRef");
        if (dynamicRef != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_CORE)) {
            URI uri = resolveUri(dynamicRef, idSchema.getUri());
            URI dropedUri = dropFragment(uri);
            if (!dropedUri.toString().isEmpty()) {
                ObjectSchema refSchema = rootSchema.importAndCompile(dropedUri);
                idSchema.importSchema(dropedUri, refSchema);
            }

            String fragment = URI.create(dynamicRef).getFragment();
            String refDynamicAnchor = fragment;
            JsonPointer refPath = null;
            if (fragment == null) refDynamicAnchor = "";
            else if (fragment.startsWith("/")) refPath = JsonPointer.compile(fragment);
            evaluators.add(new Evaluator.DynamicRefEvaluator(dropedUri, refPath, refDynamicAnchor));
        }

        // type
        Object type = schema.getNode("type");
        if (type != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.TypeEvaluator(type));
        }

        // const
        if (schema.containsKey("const") && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.ConstEvaluator(schema.getNode("const")));
        }

        // enum
        Object[] enumValues = schema.getArray("enum");
        if (enumValues != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.EnumEvaluator(enumValues));
        }

        // minimum maximum exclusiveMinimum exclusiveMaximum
        Number minimum = schema.getNumber("minimum");
        Number maximum = schema.getNumber("maximum");
        Number exclusiveMinimum = schema.getNumber("exclusiveMinimum");
        Number exclusiveMaximum = schema.getNumber("exclusiveMaximum");
        if ((minimum != null || maximum != null || exclusiveMinimum != null || exclusiveMaximum != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.NumberEvaluator(minimum, maximum, exclusiveMinimum, exclusiveMaximum));
        }

        // multipleOf
        Number multipleOf = schema.getNumber("multipleOf");
        if (multipleOf != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.MultipleOfEvaluator(multipleOf));
        }

        // minLength maxLength
        Integer minLength = schema.getInteger("minLength");
        Integer maxLength = schema.getInteger("maxLength");
        if ((minLength != null || maxLength != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.StringEvaluator(minLength, maxLength));
        }

        // pattern
        String pattern = schema.getString("pattern");
        if (pattern != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.PatternEvaluator(pattern));
        }

        // format
        String format = schema.getString("format");
        if (format != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT)) {
            evaluators.add(new Evaluator.FormatEvaluator(format));
        }

        // minProperties / maxProperties
        Integer minProperties = schema.getInteger("minProperties");
        Integer maxProperties = schema.getInteger("maxProperties");
        if ((minProperties != null || maxProperties != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.ObjectEvaluator(minProperties, maxProperties));
        }

        // required / properties / patternProperties / additionalProperties
        String[] required = schema.asArray("required", String.class);
        Map<String, Object> properties =
                compileSchemaMapByKey("properties", path, schema, idSchema, rootSchema);
        Map<String, Object> patternProperties =
                compileSchemaMapByKey("patternProperties", path, schema, idSchema, rootSchema);
        Object additionalProperties =
                compileSchemaByKey("additionalProperties", path, schema, idSchema, rootSchema);
        if ((required != null || properties != null || patternProperties != null || additionalProperties != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.PropertiesEvaluator(required, properties, patternProperties,
                    additionalProperties));
        }

        // dependentRequired
        Map<String, String[]> dependentRequired = schema.asMap("dependentRequired", String[].class);
        if (dependentRequired != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.DependentRequiredEvaluator(dependentRequired));
        }

        // dependentSchemas
        Map<String, Object> dependentSchemas =
                compileSchemaMapByKey("dependentSchemas", path, schema, idSchema, rootSchema);
        if (dependentSchemas != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.DependentSchemasEvaluator(dependentSchemas));
        }

        // propertyNames
        Object propertyNames = compileSchemaByKey("propertyNames", path, schema, idSchema, rootSchema);
        if (propertyNames != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.PropertyNamesEvaluator(propertyNames));
        }

        // minItems / maxItems / uniqueItems
        Integer minItems = schema.getInteger("minItems");
        Integer maxItems = schema.getInteger("maxItems");
        Boolean uniqueItems = schema.getBoolean("uniqueItems");
        if ((minItems != null || maxItems != null || uniqueItems != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.ArrayEvaluator(minItems, maxItems, uniqueItems));
        }

        // items / prefixItems
        Object items = compileSchemaByKey("items", path, schema, idSchema, rootSchema);
        Object[] prefixItems = compileSchemaArrayByKey("prefixItems", path, schema, idSchema, rootSchema);
        if ((items != null || prefixItems != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.ItemsEvaluator(items, prefixItems));
        }

        // contains / minContains / maxContains
        Object contains = compileSchemaByKey("contains", path, schema, idSchema, rootSchema);
        Integer minContains = schema.getInteger("minContains");
        Integer maxContains = schema.getInteger("maxContains");
        if ((contains != null || minContains != null || maxContains != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.ContainsEvaluator(contains, minContains, maxContains));
        }

        // if / then / else
        Object ifSchema = compileSchemaByKey("if", path, schema, idSchema, rootSchema);
        Object thenSchema = compileSchemaByKey("then", path, schema, idSchema, rootSchema);
        Object elseSchema = compileSchemaByKey("else", path, schema, idSchema, rootSchema);
        if ((ifSchema != null || thenSchema != null || elseSchema != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.IfThenElseEvaluator(ifSchema, thenSchema, elseSchema));
        }

        // allOf
        Object[] allOfSchemas = compileSchemaArrayByKey("allOf", path, schema, idSchema, rootSchema);
        if (allOfSchemas != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.AllOfEvaluator(allOfSchemas));
        }

        // anyOf
        Object[] anyOfSchemas = compileSchemaArrayByKey("anyOf", path, schema, idSchema, rootSchema);
        if (anyOfSchemas != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.AnyOfEvaluator(anyOfSchemas));
        }

        // oneOf
        Object[] oneOfSchemas = compileSchemaArrayByKey("oneOf", path, schema, idSchema, rootSchema);
        if (oneOfSchemas != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.OneOfEvaluator(oneOfSchemas));
        }

        // not
        Object notSchema = compileSchemaByKey("not", path, schema, idSchema, rootSchema);
        if (notSchema != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.NotEvaluator(notSchema));
        }

        // unevaluatedProperties / unevaluatedItems
        Object unevaluatedPropertiesSchema =
                compileSchemaByKey("unevaluatedProperties", path, schema, idSchema, rootSchema);
        Object unevaluatedItemsSchema =
                compileSchemaByKey("unevaluatedItems", path, schema, idSchema, rootSchema);
        if ((unevaluatedPropertiesSchema != null || unevaluatedItemsSchema != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_UNEVALUATED)) {
            evaluators.add(new Evaluator.UnevaluatedEvaluator(unevaluatedPropertiesSchema, unevaluatedItemsSchema));
        }

        return evaluators.toArray(new Evaluator[0]);
    }


    static Map<String, Object> compileSchemaMapByKey(String key, JsonPointer path,
                                                     ObjectSchema schema, ObjectSchema idSchema, ObjectSchema rootSchema) {
        Object schemaMapNode = schema.getNode(key);
        if (schemaMapNode == null) return null;

        path.push(new PathToken.Name(key));
        if (!NodeType.of(schemaMapNode).isObject())
            throw new SchemaException("Schema node at " + path + " must be a JSON Object");
        for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(schemaMapNode)) {
            Object subNode = entry.getValue();
            path.push(new PathToken.Name(entry.getKey()));
            Object subSchema = compileSchema(subNode, path, idSchema, rootSchema);
            path.pop();
            if (subSchema != subNode) entry.setValue(subSchema);
        }
        path.pop();
        return NodeUtil.asMap(schemaMapNode);
    }

    static Object[] compileSchemaArrayByKey(String key, JsonPointer path,
                                            ObjectSchema schema, ObjectSchema idSchema, ObjectSchema rootSchema) {
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
            Object subSchema = compileSchema(subNode, path, idSchema, rootSchema);
            path.pop();
            if (subSchema != subNode) NodeWalker.setInArray(schemaArrayNode, i, subSchema);
        }
        return NodeUtil.asArray(schemaArrayNode);
    }

    static Object compileSchemaByKey(String key, JsonPointer path,
                                     ObjectSchema schema, ObjectSchema idSchema, ObjectSchema rootSchema) {
        Object subNode = schema.getNode(key);
        if (subNode == null) return null;
        path.push(new PathToken.Name(key));
        Object subSchema = compileSchema(subNode, path, idSchema, rootSchema);
        path.pop();
        if (subSchema != subNode) schema.put(key, subSchema);
        return subSchema;
    }

    static Object compileSchema(Object schemaNode, JsonPointer path,
                                ObjectSchema idSchema, ObjectSchema rootSchema) {
        JsonType jt = JsonType.of(schemaNode);
        switch (jt) {
            case NULL: return null;
            case BOOLEAN: return schemaNode;
            case OBJECT: {
                ObjectSchema schema = new ObjectSchema(schemaNode);
                schema.compile(path, idSchema, rootSchema);
                return schema;
            }
            default: throw new SchemaException("Invalid schema at " + path + " : node type is " + jt);
        }
    }

    static URI dropFragment(URI uri) {
        try {
            if (uri.getFragment() != null) {
                if (uri.isOpaque()) {
                    return URI.create(uri.getScheme() + ":" + uri.getSchemeSpecificPart());
                } else {
                    return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
                }
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }


    static URI resolveUri(String idOrRef, URI baseUri) {
        if (idOrRef == null) return null;

        URI uri = URI.create(idOrRef);
        if (baseUri == null) return uri;
        if (uri.isAbsolute()) return uri;
        if (baseUri.isOpaque()) {
            if (idOrRef.startsWith("#")) return baseUri;
            else throw new SchemaException("Invalid $id or $ref '" + idOrRef + "' for base URI " + baseUri);
        }
        return baseUri.resolve(idOrRef);
    }


}
