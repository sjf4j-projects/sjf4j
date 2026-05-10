package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.PathSegment;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Compilation helpers that translate schema keywords into evaluator pipelines.
 * <p>
 * These utilities normalize subschema nodes, resolve references, and build the
 * ordered evaluator list executed at validation time.
 */
public final class SchemaPlanner {

    static final class PlanningContext {
        final SchemaRegistry registry;
        final List<Evaluator.RefEvaluator> refEvaluators;

        PlanningContext(SchemaRegistry registry) {
            this.registry = registry;
            this.refEvaluators = new ArrayList<>();
        }
    }


    static SchemaPlan createPlan(ObjectSchema schema, SchemaRegistry registry) {
        Objects.requireNonNull(schema, "schema");
        URI idUri = URI.create("sjf4j:/schema-" + schema.hashCode() + "/");
        idUri = SchemaUtil.resolveUri(idUri, schema.getCanonicalUri());
        SchemaPlan plan = registry.resolvePlan(idUri);
        if (plan != null) return plan;

        PlanningContext context = new PlanningContext(registry);
        Map<String, Boolean> vocabulary =  _resolveVocabulary(schema, null, registry);
        plan = _buildPlan(schema, idUri, PathSegment.Root.INSTANCE, new HashMap<>(), new HashMap<>(), context, vocabulary);
        context.registry.putPlan(idUri, plan);
        URI retrievalUri = schema.getRetrievalUri();
        if (retrievalUri != null && !retrievalUri.equals(idUri)) {
            context.registry.putPlan(retrievalUri, plan);
        }

        for (Evaluator.RefEvaluator refEvaluator : context.refEvaluators) {
            URI refUri = SchemaUtil.resolveUri(refEvaluator.idUri, URI.create(refEvaluator.ref));
            SchemaPlan refPlan = context.registry.resolve(refUri);
            if (refPlan == null) {
                throw new SchemaException("Not found schema by " + refUri + " with $ref '" + refEvaluator.ref + "' ('" +
                        refEvaluator.keywordPs.rootedPointerExpr() + "' in " + idUri + ")");
            }
            refEvaluator.plan = refPlan;
        }
        return plan;
    }

    /**
     * Compiles one schema object into an ordered evaluator list.
     * <p>
     * Order matters because some keywords produce evaluated-location marks that
     * are consumed by later keywords (for example unevaluated*).
     */
    private static SchemaPlan _buildPlan(ObjectSchema schema, URI idUri, PathSegment ps,
                                         Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byPathPlans,
                                         PlanningContext context, Map<String, Boolean> vocabulary) {
        _checkVocabulary(schema, ps, vocabulary);

        // $defs / definitions
        if (_allowsKeyword(vocabulary, "$defs")) {
            _buildPlanMapByKey("$defs", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary);
        }
        _buildPlanMapByKey("definitions", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary);

        // $anchor
        String anchor = schema.getString("$anchor");

        // $dynamicAnchor
        String dynamicAnchor = schema.getString("$dynamicAnchor");

        // Build evaluators
        List<Evaluator> evaluators = new ArrayList<>();

        // $ref
        String ref = schema.getString("$ref");
        if (ref != null && _allowsKeyword(vocabulary, "$ref")) {
            Evaluator.RefEvaluator evaluator = new Evaluator.RefEvaluator(new PathSegment.Name(ps, "$ref"), idUri, ref);
            evaluators.add(evaluator);
            context.refEvaluators.add(evaluator);
        }

        // $dynamicRef
        String dynamicRef = schema.getString("$dynamicRef");
        if (dynamicRef != null && _allowsKeyword(vocabulary, "$dynamicRef")) {
            evaluators.add(new Evaluator.DynamicRefEvaluator(new PathSegment.Name(ps, "$dynamicRef"), dynamicRef));
        }

        // format
        String format = schema.getString("format");
        if (format != null && _allowsFormatKeyword(vocabulary)) {
            evaluators.add(new Evaluator.FormatEvaluator(new PathSegment.Name(ps, "format"), format,
                    _isFormatAssertionEnabled(vocabulary)));
        }

        // type
        Object type = schema.getNode("type");
        if (type != null && _allowsKeyword(vocabulary, "type")) {
            evaluators.add(new Evaluator.TypeEvaluator(new PathSegment.Name(ps, "type"), type));
        }

        // const
        if (schema.containsKey("const") && _allowsKeyword(vocabulary, "const")) {      // Could be null
            evaluators.add(new Evaluator.ConstEvaluator(new PathSegment.Name(ps, "const"),
                    schema.getNode("const")));
        }

        // enum
        Object[] enumValues = schema.getArray("enum");
        if (enumValues != null && _allowsKeyword(vocabulary, "enum")) {
            evaluators.add(new Evaluator.EnumEvaluator(new PathSegment.Name(ps, "enum"), enumValues));
        }

        // minimum maximum exclusiveMinimum exclusiveMaximum
        Number minimum = schema.getNumber("minimum");
        Number maximum = schema.getNumber("maximum");
        Number exclusiveMinimum = schema.getNumber("exclusiveMinimum");
        Number exclusiveMaximum = schema.getNumber("exclusiveMaximum");
        if (_allowsKeyword(vocabulary, "minimum") &&
                (minimum != null || maximum != null || exclusiveMinimum != null || exclusiveMaximum != null)) {
            evaluators.add(new Evaluator.NumberEvaluator(
                    new PathSegment.Name(ps, "minimum"),
                    new PathSegment.Name(ps, "maximum"),
                    new PathSegment.Name(ps, "exclusiveMinimum"),
                    new PathSegment.Name(ps, "exclusiveMaximum"),
                    minimum, maximum, exclusiveMinimum, exclusiveMaximum));
        }

        // multipleOf
        Number multipleOf = schema.getNumber("multipleOf");
        if (multipleOf != null && _allowsKeyword(vocabulary, "multipleOf")) {
            evaluators.add(new Evaluator.MultipleOfEvaluator(new PathSegment.Name(ps, "multipleOf"), multipleOf));
        }

        // minLength maxLength
        Integer minLength = schema.getInt("minLength");
        Integer maxLength = schema.getInt("maxLength");
        if (_allowsKeyword(vocabulary, "minLength") && (minLength != null || maxLength != null)) {
            evaluators.add(new Evaluator.StringEvaluator(new PathSegment.Name(ps, "minLength"),
                    new PathSegment.Name(ps, "maxLength"), minLength, maxLength));
        }

        // pattern
        String pattern = schema.getString("pattern");
        if (pattern != null && _allowsKeyword(vocabulary, "pattern")) {
            evaluators.add(new Evaluator.PatternEvaluator(new PathSegment.Name(ps, "pattern"), pattern));
        }

        // minProperties / maxProperties
        Integer minProperties = schema.getInt("minProperties");
        Integer maxProperties = schema.getInt("maxProperties");
        if (_allowsKeyword(vocabulary, "minProperties") && (minProperties != null || maxProperties != null)) {
            evaluators.add(new Evaluator.ObjectEvaluator(new PathSegment.Name(ps, "minProperties"),
                    new PathSegment.Name(ps, "maxProperties"), minProperties, maxProperties));
        }

        // required / dependentRequired
        String[] required = schema.getArray("required", String.class);
        Map<String, String[]> dependentRequired = schema.getMap("dependentRequired", String[].class);
        if ((_allowsKeyword(vocabulary, "required") || _allowsKeyword(vocabulary, "dependentRequired")) &&
                (required != null || dependentRequired != null)) {
            evaluators.add(new Evaluator.RequiredEvaluator(new PathSegment.Name(ps, "required"),
                    new PathSegment.Name(ps, "dependentRequired"), required, dependentRequired));
        }

        // properties / patternProperties / additionalProperties
        Map<String, SchemaPlan> properties = _allowsKeyword(vocabulary, "properties")
                ? _buildPlanMapByKey("properties", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        Map<String, SchemaPlan> patternProperties = _allowsKeyword(vocabulary, "patternProperties")
                ? _buildPlanMapByKey("patternProperties", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan additionalProperties = _allowsKeyword(vocabulary, "additionalProperties")
                ? _buildPlanByKey("additionalProperties", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (properties != null || patternProperties != null || additionalProperties != null) {
            evaluators.add(new Evaluator.PropertiesEvaluator(properties, patternProperties, additionalProperties));
        }

        // dependentSchemas
        Map<String, SchemaPlan> dependentPlans = _allowsKeyword(vocabulary, "dependentSchemas")
                ? _buildPlanMapByKey("dependentSchemas", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (dependentPlans != null) {
            evaluators.add(new Evaluator.DependentSchemasEvaluator(dependentPlans));
        }

        // propertyNames
        SchemaPlan propertyNamesPlan = _allowsKeyword(vocabulary, "propertyNames")
                ? _buildPlanByKey("propertyNames", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (propertyNamesPlan != null) {
            evaluators.add(new Evaluator.PropertyNamesEvaluator(
                    new PathSegment.Name(ps, "propertyNames"), propertyNamesPlan));
        }

        // minItems / maxItems / uniqueItems
        Integer minItems = schema.getInt("minItems");
        Integer maxItems = schema.getInt("maxItems");
        Boolean uniqueItems = schema.getBoolean("uniqueItems");
        if (_allowsKeyword(vocabulary, "minItems") && (minItems != null || maxItems != null || uniqueItems != null)) {
            evaluators.add(new Evaluator.ArrayEvaluator(new PathSegment.Name(ps, "minItems"),
                    new PathSegment.Name(ps, "maxItems"), new PathSegment.Name(ps, "uniqueItems"),
                    minItems, maxItems, uniqueItems));
        }

        // items / prefixItems
        SchemaPlan itemsPlan = _allowsKeyword(vocabulary, "items")
                ? _buildPlanByKey("items", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan[] prefixItemsPlans = _allowsKeyword(vocabulary, "prefixItems")
                ? _buildPlanArrayByKey("prefixItems", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (itemsPlan != null || prefixItemsPlans != null) {
            evaluators.add(new Evaluator.ItemsEvaluator(itemsPlan, prefixItemsPlans));
        }

        // contains / minContains / maxContains
        SchemaPlan containsPlan = _allowsKeyword(vocabulary, "contains")
                ? _buildPlanByKey("contains", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        Integer minContains = schema.getInt("minContains");
        Integer maxContains = schema.getInt("maxContains");
        if ((_allowsKeyword(vocabulary, "contains") || _allowsKeyword(vocabulary, "minContains")) &&
                (containsPlan != null || minContains != null || maxContains != null)) {
            evaluators.add(new Evaluator.ContainsEvaluator(
                    new PathSegment.Name(ps, "minContains"),
                    new PathSegment.Name(ps, "maxContains"),
                    containsPlan, minContains, maxContains));
        }

        // if / then / else
        SchemaPlan ifPlan = _allowsKeyword(vocabulary, "if")
                ? _buildPlanByKey("if", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan thenPlan = _allowsKeyword(vocabulary, "then")
                ? _buildPlanByKey("then", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan elsePlan = _allowsKeyword(vocabulary, "else")
                ? _buildPlanByKey("else", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (ifPlan != null || thenPlan != null || elsePlan != null) {
            evaluators.add(new Evaluator.IfThenElseEvaluator(ifPlan, thenPlan, elsePlan));
        }

        // allOf
        SchemaPlan[] allOfPlans = _allowsKeyword(vocabulary, "allOf")
                ? _buildPlanArrayByKey("allOf", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (allOfPlans != null) {
            evaluators.add(new Evaluator.AllOfEvaluator(allOfPlans));
        }

        // anyOf
        SchemaPlan[] anyOfPlans = _allowsKeyword(vocabulary, "anyOf")
                ? _buildPlanArrayByKey("anyOf", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (anyOfPlans != null) {
            evaluators.add(new Evaluator.AnyOfEvaluator(new PathSegment.Name(ps, "anyOf"), anyOfPlans));
        }

        // oneOf
        SchemaPlan[] oneOfPlans = _allowsKeyword(vocabulary, "oneOf")
                ? _buildPlanArrayByKey("oneOf", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (oneOfPlans != null) {
            evaluators.add(new Evaluator.OneOfEvaluator(new PathSegment.Name(ps, "oneOf"), oneOfPlans));
        }

        // not
        SchemaPlan notPlan = _allowsKeyword(vocabulary, "not")
                ? _buildPlanByKey("not", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (notPlan != null) {
            evaluators.add(new Evaluator.NotEvaluator(new PathSegment.Name(ps, "not"), notPlan));
        }

        // unevaluatedProperties / unevaluatedItems
        SchemaPlan unevaluatedPropertiesPlan = _allowsKeyword(vocabulary, "unevaluatedProperties")
                ? _buildPlanByKey("unevaluatedProperties", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan unevaluatedItemsPlan = _allowsKeyword(vocabulary, "unevaluatedItems")
                ? _buildPlanByKey("unevaluatedItems", schema, idUri, ps, byAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (unevaluatedPropertiesPlan != null || unevaluatedItemsPlan != null) {
            evaluators.add(new Evaluator.UnevaluatedEvaluator(unevaluatedPropertiesPlan, unevaluatedItemsPlan));
        }

        // end up
        SchemaPlan plan = SchemaPlan.of(ps, evaluators, dynamicAnchor, byAnchorPlans, byPathPlans);
        if (anchor != null) byAnchorPlans.put(anchor, plan);
        if (dynamicAnchor != null) byAnchorPlans.put(dynamicAnchor, plan);
        byPathPlans.put(ps.rootedPointerExpr(), plan);
        return plan;
    }

    /**
     * Compiles a map of subschemas from the given keyword.
     * <p>
     * Source object is rewritten in-place so compiled JsonSchema instances can be
     * reused directly during evaluation.
     */
    private static Map<String, SchemaPlan> _buildPlanMapByKey(
                String key, ObjectSchema schema, URI baseUri, PathSegment ps,
                Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byPathPlans, PlanningContext context,
                Map<String, Boolean> vocabulary) {
        Object objectNode = schema.getNode(key);
        if (objectNode == null) return null;

        PathSegment cps = new PathSegment.Name(ps, key);
        if (!JsonType.of(objectNode).isObject()) {
            throw new SchemaException("Invalid schema at '" + cps.rootedPointerExpr() + "': must be a JSON Object");
        }

        Map<String, SchemaPlan> planMap = new HashMap<>();
        Nodes.forEachObject(objectNode, (k, subNode) -> {
            PathSegment ccps = new PathSegment.Name(cps, k);
            SchemaPlan plan = _buildPlanFromNode(subNode, baseUri, ccps, byAnchorPlans, byPathPlans, context, vocabulary);
            planMap.put(k, plan);
        });
        return planMap;
    }

    /**
     * Compiles an array of subschemas from the given keyword.
     * <p>
     * Source array is rewritten in-place with compiled JsonSchema values.
     */
    private static SchemaPlan[] _buildPlanArrayByKey(String key, ObjectSchema schema, URI baseUri, PathSegment ps,
                Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byPathPlans, PlanningContext context,
                Map<String, Boolean> vocabulary) {
        Object arrayNode = schema.getNode(key);
        if (arrayNode == null) return null;

        PathSegment cps = new PathSegment.Name(ps, key);
        if (!JsonType.of(arrayNode).isArray()) {
            throw new SchemaException("Invalid schema at '" + cps.rootedPointerExpr() + "': must be a JSON Array");
        }
        int size = Nodes.sizeInArray(arrayNode);
        SchemaPlan[] planArr = new SchemaPlan[size];
        for (int i = 0; i < size; i++) {
            Object subNode = Nodes.getInArray(arrayNode, i);
            PathSegment ccps = new PathSegment.Index(cps, i);
            planArr[i] = _buildPlanFromNode(subNode, baseUri, ccps, byAnchorPlans, byPathPlans, context, vocabulary);
        }
        return planArr;
    }

    /**
     * Compiles one subschema from the given keyword and writes back if needed.
     */
    private static SchemaPlan _buildPlanByKey(String key, ObjectSchema schema, URI baseUri, PathSegment ps,
                                               Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byPathPlans,
                                               PlanningContext context, Map<String, Boolean> vocabulary) {
        if (!schema.containsKey(key)) return null;
        Object subNode = schema.getNode(key);
        PathSegment cps = new PathSegment.Name(ps, key);
        return _buildPlanFromNode(subNode, baseUri, cps, byAnchorPlans, byPathPlans, context, vocabulary);
    }

    /**
     * Compiles a schema node into a JsonSchema implementation.
     * <p>
     * Boolean nodes become boolean schemas; object nodes become compiled
     * ObjectSchema instances; null/other types are invalid for schema positions.
     */
    private static SchemaPlan _buildPlanFromNode(Object node, URI idUri, PathSegment ps,
                                                  Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byPathPlans,
                                                  PlanningContext context, Map<String, Boolean> inheritedVocabulary) {
        if (node == null) {
            throw new SchemaException("Invalid schema ('" + ps.rootedPointerExpr() + "' in " + idUri + "): schema is null");
        }

        JsonSchema schema;
        if (node instanceof JsonSchema) {
            schema = (JsonSchema) node;
        } else {
            try {
                schema = Nodes.to(node, JsonSchema.class);
            } catch (Exception e) {
                throw new SchemaException("Invalid schema at '" + ps.rootedPointerExpr() +
                        "': node type is " + node.getClass().getName(), e);
            }
        }

        if (schema instanceof BooleanSchema) {
            return SchemaPlan.of(ps, (BooleanSchema) schema);
        }

        ObjectSchema os = (ObjectSchema) schema;
        String id = os.getId();
        if (id == null) {
            return _buildPlan(os, idUri, ps, byAnchorPlans, byPathPlans, context, inheritedVocabulary);
        } else {
            idUri = idUri.resolve(id);
            Map<String, Boolean> vocabulary = _resolveVocabulary(os, inheritedVocabulary, context.registry);
            SchemaPlan plan = _buildPlan(os, idUri, ps, new HashMap<>(), new HashMap<>(), context, vocabulary);
            context.registry.putPlan(idUri, plan);
            return plan;
        }
    }

    private static Map<String, Boolean> _resolveVocabulary(ObjectSchema schema,
                                                           Map<String, Boolean> inheritedVocabulary,
                                                           SchemaRegistry registry) {
        Map<String, Boolean> vocabulary = schema.getVocabulary();
        if (vocabulary != null) return vocabulary;

        String schemaUri = schema.getString("$schema");
        if (schemaUri != null) {
            ObjectSchema metaSchema = registry.resolveSchema(URI.create(schemaUri));
            if (metaSchema != null) {
                Map<String, Boolean> metaVocabulary = metaSchema.getVocabulary();
                if (metaVocabulary != null) return metaVocabulary;
            }
        }
        return inheritedVocabulary;
    }

    private static void _checkVocabulary(ObjectSchema schema, PathSegment ps, Map<String, Boolean> vocabulary) {
        if (vocabulary == null) return;
        for (String property : schema.keySet()) {
            String vocabUri = VocabularyRegistry.getVocabUri(property);
            if (vocabUri == null) continue;
            Boolean allow = vocabulary.get(vocabUri);
            if (allow != null && !allow) {
                throw new SchemaException("Keyword '" + property + "' at '" + ps.rootedPathExpr() +
                        "' is disallowed by declared vocabulary " + vocabUri);
            }
        }
    }

    private static boolean _allowsKeyword(Map<String, Boolean> vocabulary, String keyword) {
        if (vocabulary == null) return true;
        String vocabUri = VocabularyRegistry.getVocabUri(keyword);
        if (vocabUri == null) return true;
        return Boolean.TRUE.equals(vocabulary.get(vocabUri));
    }

    private static boolean _allowsFormatKeyword(Map<String, Boolean> vocabulary) {
        if (vocabulary == null) return true;
        return vocabulary.containsKey(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT_ASSERTION) ||
                Boolean.TRUE.equals(vocabulary.get(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT));
    }

    private static boolean _isFormatAssertionEnabled(Map<String, Boolean> vocabulary) {
        return vocabulary != null && vocabulary.containsKey(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT_ASSERTION);
    }


}
