package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.Paths;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class CompileUtil {


    static void checkVocabulary(PathSegment ps, ObjectSchema schema, Map<String, Boolean> vocabulary) {
        for (String property : schema.keySet()) {
            String vocabUri = VocabularyRegistry.getVocabUri(property);
            if (vocabUri != null) {
                if (vocabulary != null) {
                    Boolean allow = vocabulary.get(vocabUri);
                    if (allow != null && !allow)
                        throw new SchemaException("Keyword '" + property + "' at " + JsonPointer.fromLast(ps) +
                                " is disallowed by declared vocabulary " + vocabUri);
                }
            } else {
                throw new SchemaException("Unrecognized schema keyword '" + property + "' at " +
                        JsonPointer.fromLast(ps) + " . No registered vocabulary claims support for it.\n");
            }
        }
    }

    static Evaluator[] compile(PathSegment ps,
                                      ObjectSchema schema,
                                      ObjectSchema idSchema,
                                      ObjectSchema rootSchema) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(idSchema);
        Objects.requireNonNull(rootSchema);

        checkVocabulary(ps, schema, idSchema.getVocabulary());

        // $defs
        compileSchemaMapByKey("$defs", ps, schema, idSchema, rootSchema);
        compileSchemaMapByKey("definitions", ps, schema, idSchema, rootSchema);

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

        // format
        String format = schema.getString("format");
        if (format != null && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT)) {
            evaluators.add(new Evaluator.FormatEvaluator(format));
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

        // minProperties / maxProperties
        Integer minProperties = schema.getInteger("minProperties");
        Integer maxProperties = schema.getInteger("maxProperties");
        if ((minProperties != null || maxProperties != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.ObjectEvaluator(minProperties, maxProperties));
        }

        // required / dependentRequired
        String[] required = schema.getArray("required", String.class);
        Map<String, String[]> dependentRequired = schema.getMap("dependentRequired", String[].class);
        if ((required != null || dependentRequired != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_VALIDATION)) {
            evaluators.add(new Evaluator.RequiredEvaluator(required, dependentRequired));
        }

        // properties / patternProperties / additionalProperties
        Map<String, JsonSchema> properties =
                compileSchemaMapByKey("properties", ps, schema, idSchema, rootSchema);
        Map<String, JsonSchema> patternProperties =
                compileSchemaMapByKey("patternProperties", ps, schema, idSchema, rootSchema);
        JsonSchema additionalProperties =
                compileSchemaByKey("additionalProperties", ps, schema, idSchema, rootSchema);
        if ((properties != null || patternProperties != null || additionalProperties != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.PropertiesEvaluator(properties, patternProperties,
                    additionalProperties));
        }

        // dependentSchemas
        Map<String, JsonSchema> dependentSchemas =
                compileSchemaMapByKey("dependentSchemas", ps, schema, idSchema, rootSchema);
        if (dependentSchemas != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.DependentSchemasEvaluator(dependentSchemas));
        }

        // propertyNames
        JsonSchema propertyNames = compileSchemaByKey("propertyNames", ps, schema, idSchema, rootSchema);
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
        JsonSchema items = compileSchemaByKey("items", ps, schema, idSchema, rootSchema);
        JsonSchema[] prefixItems = compileSchemaArrayByKey("prefixItems", ps, schema, idSchema, rootSchema);
        if ((items != null || prefixItems != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.ItemsEvaluator(items, prefixItems));
        }

        // contains / minContains / maxContains
        JsonSchema contains = compileSchemaByKey("contains", ps, schema, idSchema, rootSchema);
        Integer minContains = schema.getInteger("minContains");
        Integer maxContains = schema.getInteger("maxContains");
        if ((contains != null || minContains != null || maxContains != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.ContainsEvaluator(contains, minContains, maxContains));
        }

        // if / then / else
        JsonSchema ifSchema = compileSchemaByKey("if", ps, schema, idSchema, rootSchema);
        JsonSchema thenSchema = compileSchemaByKey("then", ps, schema, idSchema, rootSchema);
        JsonSchema elseSchema = compileSchemaByKey("else", ps, schema, idSchema, rootSchema);
        if ((ifSchema != null || thenSchema != null || elseSchema != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.IfThenElseEvaluator(ifSchema, thenSchema, elseSchema));
        }

        // allOf
        JsonSchema[] allOfSchemas = compileSchemaArrayByKey("allOf", ps, schema, idSchema, rootSchema);
        if (allOfSchemas != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.AllOfEvaluator(allOfSchemas));
        }

        // anyOf
        JsonSchema[] anyOfSchemas = compileSchemaArrayByKey("anyOf", ps, schema, idSchema, rootSchema);
        if (anyOfSchemas != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.AnyOfEvaluator(anyOfSchemas));
        }

        // oneOf
        JsonSchema[] oneOfSchemas = compileSchemaArrayByKey("oneOf", ps, schema, idSchema, rootSchema);
        if (oneOfSchemas != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.OneOfEvaluator(oneOfSchemas));
        }

        // not
        JsonSchema notSchema = compileSchemaByKey("not", ps, schema, idSchema, rootSchema);
        if (notSchema != null
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_APPLICATOR)) {
            evaluators.add(new Evaluator.NotEvaluator(notSchema));
        }

        // unevaluatedProperties / unevaluatedItems
        JsonSchema unevaluatedPropertiesSchema =
                compileSchemaByKey("unevaluatedProperties", ps, schema, idSchema, rootSchema);
        JsonSchema unevaluatedItemsSchema =
                compileSchemaByKey("unevaluatedItems", ps, schema, idSchema, rootSchema);
        if ((unevaluatedPropertiesSchema != null || unevaluatedItemsSchema != null)
                && rootSchema.vocabAllowed(VocabularyRegistry.DRAFT_2020_12_VOCAB_UNEVALUATED)) {
            evaluators.add(new Evaluator.UnevaluatedEvaluator(unevaluatedPropertiesSchema, unevaluatedItemsSchema));
        }

        return evaluators.toArray(new Evaluator[0]);
    }



    static Map<String, JsonSchema> compileSchemaMapByKey(String key, PathSegment ps,
                                                         ObjectSchema schema, ObjectSchema idSchema,
                                                         ObjectSchema rootSchema) {
        Object schemaMapNode = schema.getNode(key);
        if (schemaMapNode == null) return null;

        PathSegment cps = new PathSegment.Name(ps, null, key);
        if (!JsonType.of(schemaMapNode).isObject())
            throw new SchemaException("Schema node at " + JsonPointer.fromLast(cps) + " must be a JSON Object");
        Map<String, JsonSchema> schemaMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(schemaMapNode)) {
            Object subNode = entry.getValue();
            PathSegment ccps = new PathSegment.Name(cps, null, entry.getKey());
            if (subNode == null) throw new SchemaException("Invalid schema at '" + Paths.rootedPointerExpr(ccps) +
                    "': null is not allowed");
            JsonSchema subSchema = compileSchema(subNode, ccps, idSchema, rootSchema);
            if (subSchema != subNode) entry.setValue(subSchema);
            schemaMap.put(entry.getKey(), subSchema);
        }
        return schemaMap;
    }

    static JsonSchema[] compileSchemaArrayByKey(String key, PathSegment ps,
                                            ObjectSchema schema, ObjectSchema idSchema, ObjectSchema rootSchema) {
        Object schemaArrayNode = schema.getNode(key);
        if (schemaArrayNode == null) return null;

        PathSegment cps = new PathSegment.Name(ps, null, key);
        if (!JsonType.of(schemaArrayNode).isArray()) {
            throw new SchemaException("Schema node at " + Paths.rootedPointerExpr(cps) + " must be a JSON Array");
        }
        int size = Nodes.sizeInArray(schemaArrayNode);
        JsonSchema[] schemaArr = new JsonSchema[size];
        for (int i = 0; i < size; i++) {
            Object subNode = Nodes.getInArray(schemaArrayNode, i);
            PathSegment ccps = new PathSegment.Index(cps, null, i);
            if (subNode == null) throw new SchemaException("Invalid schema at '" + Paths.rootedPointerExpr(ccps) +
                    "': null is not allowed");
            JsonSchema subSchema = compileSchema(subNode, ccps, idSchema, rootSchema);
            if (subSchema != subNode) Nodes.setInArray(schemaArrayNode, i, subSchema);
            schemaArr[i] = subSchema;
        }
        return schemaArr;
    }

    static JsonSchema compileSchemaByKey(String key, PathSegment ps,
                                     ObjectSchema schema, ObjectSchema idSchema, ObjectSchema rootSchema) {
        Object subNode = schema.getNode(key);
        if (subNode == null) return null;
        PathSegment cps = new PathSegment.Name(ps, null, key);
        JsonSchema subSchema = compileSchema(subNode, cps, idSchema, rootSchema);
        if (subSchema != subNode) schema.put(key, subSchema);
        return subSchema;
    }

    static JsonSchema compileSchema(Object node, PathSegment ps,
                                ObjectSchema idSchema, ObjectSchema rootSchema) {
        if (node instanceof JsonSchema) {
            return  (JsonSchema) node;
        }

        JsonType jt = JsonType.of(node);
        switch (jt) {
            case NULL: return null;
            case BOOLEAN: return BooleanSchema.raw2Value(Nodes.toBoolean(node));
            case OBJECT: {
                ObjectSchema schema = new ObjectSchema(node);
                schema.compile(ps, idSchema, rootSchema);
                return schema;
            }
            default: throw new SchemaException("Invalid schema at '" + Paths.rootedPointerExpr(ps) +
                    "': node type is " + jt);
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
