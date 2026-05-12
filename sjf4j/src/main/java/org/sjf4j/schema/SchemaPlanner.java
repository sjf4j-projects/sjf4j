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
        final List<Evaluator.DynamicRefEvaluator> dynamicRefEvaluators;

        PlanningContext(SchemaRegistry registry) {
            this.registry = registry;
            this.refEvaluators = new ArrayList<>();
            this.dynamicRefEvaluators = new ArrayList<>();
        }
    }


    static SchemaPlan createPlan(ObjectSchema schema, SchemaRegistry registry) {
        Objects.requireNonNull(schema, "schema");
        URI retrievalUri = schema.getRetrievalUri();
        URI idUri = retrievalUri != null
                ? SchemaUtil.resolveUri(retrievalUri, schema.getCanonicalUri())
                : SchemaUtil.resolveUri(URI.create("sjf4j:/schema-" + schema.hashCode() + "/"), schema.getCanonicalUri());
        SchemaPlan plan = registry.resolvePlan(idUri);
        if (plan != null) return plan;

        PlanningContext context = new PlanningContext(registry);
        Map<String, Boolean> vocabulary =  _resolveVocabulary(schema, null, registry);
        plan = _buildPlan(schema, idUri, PathSegment.Root.INSTANCE,
                new HashMap<>(), new HashMap<>(), new HashMap<>(), context, vocabulary);
        context.registry.putPlan(idUri, plan);
        if (retrievalUri != null && !retrievalUri.equals(idUri)) {
            context.registry.putPlan(retrievalUri, plan);
        }

        for (Evaluator.RefEvaluator refEvaluator : context.refEvaluators) {
            URI refUri = SchemaUtil.resolveUri(refEvaluator.schemaUri, URI.create(refEvaluator.ref));
            SchemaPlan refPlan = context.registry.resolve(refUri);
            if (refPlan == null) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cannot resolve $ref '" + refEvaluator.ref + "' -> '" + refUri + "'",
                        refEvaluator.keywordPs, idUri));
            }
            refEvaluator.plan = refPlan;
        }
        for (Evaluator.DynamicRefEvaluator dynamicRefEvaluator : context.dynamicRefEvaluators) {
            URI refUri = SchemaUtil.resolveUri(dynamicRefEvaluator.schemaUri, URI.create(dynamicRefEvaluator.ref));
            SchemaPlan refPlan = context.registry.resolve(refUri);
            if (refPlan == null) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cannot resolve $dynamicRef '" + dynamicRefEvaluator.ref + "' -> '" + refUri + "'",
                        dynamicRefEvaluator.keywordPs, idUri));
            }
            dynamicRefEvaluator.initialPlan = refPlan;
            String fragment = refUri.getFragment();
            dynamicRefEvaluator.dynamicAnchorName = fragment != null && fragment.startsWith("/")
                    ? null : refPlan.dynamicAnchor;
        }
        return plan;
    }

    /**
     * Compiles one schema object into an ordered evaluator list.
     * <p>
     * Order matters because some keywords produce evaluated-location marks that
     * are consumed by later keywords (for example unevaluated*). All plans in
     * the same schema resource share the same anchor, dynamic-anchor, and
     * pointer lookup maps. Named anchors and dynamic anchors share one fragment
     * namespace, so duplicate names in the same resource are rejected.
     */
    private static SchemaPlan _buildPlan(ObjectSchema schema, URI idUri, PathSegment ps,
                                         Map<String, SchemaPlan> byAnchorPlans,
                                         Map<String, SchemaPlan> byDynamicAnchorPlans,
                                         Map<String, SchemaPlan> byPathPlans,
                                         PlanningContext context, Map<String, Boolean> vocabulary) {
        _checkVocabulary(schema, ps, vocabulary);

        // $defs / definitions
        if (_allowsKeyword(vocabulary, "$defs")) {
            _buildPlanMapByKey("$defs", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary);
        }
        _buildPlanMapByKey("definitions", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary);

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
            Evaluator.DynamicRefEvaluator evaluator = new Evaluator.DynamicRefEvaluator(
                    new PathSegment.Name(ps, "$dynamicRef"), idUri, dynamicRef);
            evaluators.add(evaluator);
            context.dynamicRefEvaluators.add(evaluator);
        }

        // format
        String format = schema.getString("format");
        if (format != null && _allowsFormatKeyword(vocabulary)) {
            evaluators.add(new Evaluator.FormatEvaluator(new PathSegment.Name(ps, "format"), idUri,
                    format, _isFormatAssertionEnabled(vocabulary)));
        }

        // type
        Object type = schema.getNode("type");
        if (type != null && _allowsKeyword(vocabulary, "type")) {
            evaluators.add(new Evaluator.TypeEvaluator(new PathSegment.Name(ps, "type"), idUri, type));
        }

        // const
        if (schema.containsKey("const") && _allowsKeyword(vocabulary, "const")) {      // Could be null
            evaluators.add(new Evaluator.ConstEvaluator(new PathSegment.Name(ps, "const"), idUri,
                    schema.getNode("const")));
        }

        // enum
        Object[] enumValues = schema.getArray("enum");
        if (enumValues != null && _allowsKeyword(vocabulary, "enum")) {
            evaluators.add(new Evaluator.EnumEvaluator(new PathSegment.Name(ps, "enum"), idUri, enumValues));
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
                    idUri, minimum, maximum, exclusiveMinimum, exclusiveMaximum));
        }

        // multipleOf
        Number multipleOf = schema.getNumber("multipleOf");
        if (multipleOf != null && _allowsKeyword(vocabulary, "multipleOf")) {
            evaluators.add(new Evaluator.MultipleOfEvaluator(new PathSegment.Name(ps, "multipleOf"), idUri,
                    multipleOf));
        }

        // minLength maxLength
        Integer minLength = schema.getInt("minLength");
        Integer maxLength = schema.getInt("maxLength");
        if (_allowsKeyword(vocabulary, "minLength") && (minLength != null || maxLength != null)) {
            evaluators.add(new Evaluator.StringEvaluator(new PathSegment.Name(ps, "minLength"),
                    new PathSegment.Name(ps, "maxLength"), idUri, minLength, maxLength));
        }

        // pattern
        String pattern = schema.getString("pattern");
        if (pattern != null && _allowsKeyword(vocabulary, "pattern")) {
            evaluators.add(new Evaluator.PatternEvaluator(new PathSegment.Name(ps, "pattern"), idUri, pattern));
        }

        // minProperties / maxProperties
        Integer minProperties = schema.getInt("minProperties");
        Integer maxProperties = schema.getInt("maxProperties");
        if (_allowsKeyword(vocabulary, "minProperties") && (minProperties != null || maxProperties != null)) {
            evaluators.add(new Evaluator.ObjectEvaluator(
                    new PathSegment.Name(ps, "minProperties"),
                    new PathSegment.Name(ps, "maxProperties"),
                    idUri, minProperties, maxProperties));
        }

        // required / dependentRequired
        String[] required = schema.getArray("required", String.class);
        Map<String, String[]> dependentRequired = schema.getMap("dependentRequired", String[].class);
        if ((_allowsKeyword(vocabulary, "required") || _allowsKeyword(vocabulary, "dependentRequired")) &&
                (required != null || dependentRequired != null)) {
            evaluators.add(new Evaluator.RequiredEvaluator(
                    new PathSegment.Name(ps, "required"),
                    new PathSegment.Name(ps, "dependentRequired"),
                    idUri, required, dependentRequired));
        }

        // properties / patternProperties / additionalProperties
        Map<String, SchemaPlan> properties = _allowsKeyword(vocabulary, "properties")
                ? _buildPlanMapByKey("properties", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        Map<String, SchemaPlan> patternProperties = _allowsKeyword(vocabulary, "patternProperties")
                ? _buildPlanMapByKey("patternProperties", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan additionalProperties = _allowsKeyword(vocabulary, "additionalProperties")
                ? _buildPlanByKey("additionalProperties", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (properties != null || patternProperties != null || additionalProperties != null) {
            evaluators.add(new Evaluator.PropertiesEvaluator(properties, patternProperties, additionalProperties));
        }

        // dependentSchemas
        Map<String, SchemaPlan> dependentPlans = _allowsKeyword(vocabulary, "dependentSchemas")
                ? _buildPlanMapByKey("dependentSchemas", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (dependentPlans != null) {
            evaluators.add(new Evaluator.DependentSchemasEvaluator(dependentPlans));
        }

        // propertyNames
        SchemaPlan propertyNamesPlan = _allowsKeyword(vocabulary, "propertyNames")
                ? _buildPlanByKey("propertyNames", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (propertyNamesPlan != null) {
            evaluators.add(new Evaluator.PropertyNamesEvaluator(new PathSegment.Name(ps, "propertyNames"), idUri,
                    propertyNamesPlan));
        }

        // minItems / maxItems / uniqueItems
        Integer minItems = schema.getInt("minItems");
        Integer maxItems = schema.getInt("maxItems");
        Boolean uniqueItems = schema.getBoolean("uniqueItems");
        if (_allowsKeyword(vocabulary, "minItems") && (minItems != null || maxItems != null || uniqueItems != null)) {
            evaluators.add(new Evaluator.ArrayEvaluator(
                    new PathSegment.Name(ps, "minItems"),
                    new PathSegment.Name(ps, "maxItems"),
                    new PathSegment.Name(ps, "uniqueItems"),
                    idUri, minItems, maxItems, uniqueItems));
        }

        // items / prefixItems
        SchemaPlan itemsPlan = _allowsKeyword(vocabulary, "items")
                ? _buildPlanByKey("items", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan[] prefixItemsPlans = _allowsKeyword(vocabulary, "prefixItems")
                ? _buildPlanArrayByKey("prefixItems", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (itemsPlan != null || prefixItemsPlans != null) {
            evaluators.add(new Evaluator.ItemsEvaluator(itemsPlan, prefixItemsPlans));
        }

        // contains / minContains / maxContains
        SchemaPlan containsPlan = _allowsKeyword(vocabulary, "contains")
                ? _buildPlanByKey("contains", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        Integer minContains = schema.getInt("minContains");
        Integer maxContains = schema.getInt("maxContains");
        if ((_allowsKeyword(vocabulary, "contains") || _allowsKeyword(vocabulary, "minContains")) &&
                (containsPlan != null || minContains != null || maxContains != null)) {
            evaluators.add(new Evaluator.ContainsEvaluator(
                    new PathSegment.Name(ps, "minContains"),
                    new PathSegment.Name(ps, "maxContains"),
                    idUri, containsPlan, minContains, maxContains));
        }

        // if / then / else
        SchemaPlan ifPlan = _allowsKeyword(vocabulary, "if")
                ? _buildPlanByKey("if", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan thenPlan = _allowsKeyword(vocabulary, "then")
                ? _buildPlanByKey("then", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan elsePlan = _allowsKeyword(vocabulary, "else")
                ? _buildPlanByKey("else", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (ifPlan != null || thenPlan != null || elsePlan != null) {
            evaluators.add(new Evaluator.IfThenElseEvaluator(ifPlan, thenPlan, elsePlan));
        }

        // allOf
        SchemaPlan[] allOfPlans = _allowsKeyword(vocabulary, "allOf")
                ? _buildPlanArrayByKey("allOf", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (allOfPlans != null) {
            evaluators.add(new Evaluator.AllOfEvaluator(allOfPlans));
        }

        // anyOf
        SchemaPlan[] anyOfPlans = _allowsKeyword(vocabulary, "anyOf")
                ? _buildPlanArrayByKey("anyOf", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (anyOfPlans != null) {
            evaluators.add(new Evaluator.AnyOfEvaluator(new PathSegment.Name(ps, "anyOf"), idUri, anyOfPlans));
        }

        // oneOf
        SchemaPlan[] oneOfPlans = _allowsKeyword(vocabulary, "oneOf")
                ? _buildPlanArrayByKey("oneOf", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (oneOfPlans != null) {
            evaluators.add(new Evaluator.OneOfEvaluator(new PathSegment.Name(ps, "oneOf"), idUri, oneOfPlans));
        }

        // not
        SchemaPlan notPlan = _allowsKeyword(vocabulary, "not")
                ? _buildPlanByKey("not", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (notPlan != null) {
            evaluators.add(new Evaluator.NotEvaluator(new PathSegment.Name(ps, "not"), idUri, notPlan));
        }

        // unevaluatedProperties / unevaluatedItems
        SchemaPlan unevaluatedPropertiesPlan = _allowsKeyword(vocabulary, "unevaluatedProperties")
                ? _buildPlanByKey("unevaluatedProperties", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        SchemaPlan unevaluatedItemsPlan = _allowsKeyword(vocabulary, "unevaluatedItems")
                ? _buildPlanByKey("unevaluatedItems", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary) : null;
        if (unevaluatedPropertiesPlan != null || unevaluatedItemsPlan != null) {
            evaluators.add(new Evaluator.UnevaluatedEvaluator(unevaluatedPropertiesPlan, unevaluatedItemsPlan));
        }

        // end up
        SchemaPlan plan = SchemaPlan.of(idUri, ps, evaluators, dynamicAnchor, byAnchorPlans, byDynamicAnchorPlans, byPathPlans);
        if (anchor != null) _putNamedFragment(byAnchorPlans, anchor, plan, "$anchor", idUri, ps);
        if (dynamicAnchor != null) {
            _putNamedFragment(byAnchorPlans, dynamicAnchor, plan, "$dynamicAnchor", idUri, ps);
            byDynamicAnchorPlans.put(dynamicAnchor, plan);
        }
        byPathPlans.put(ps.rootedPointerExpr(), plan);
        return plan;
    }

    private static void _putNamedFragment(Map<String, SchemaPlan> byAnchorPlans, String fragment,
                                          SchemaPlan plan, String keyword, URI idUri, PathSegment ps) {
        SchemaPlan old = byAnchorPlans.putIfAbsent(fragment, plan);
        if (old != null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_CONFLICT,
                    "duplicate named fragment '" + fragment + "' from " + keyword,
                    ps, idUri));
        }
    }

    /**
     * Compiles a map of subschemas from the given keyword.
     * <p>
     * Returned plans stay in the current schema resource unless a child schema
     * declares its own {@code $id}, in which case that child starts a new
     * resource with its own fragment space.
     */
    private static Map<String, SchemaPlan> _buildPlanMapByKey(
                String key, ObjectSchema schema, URI baseUri, PathSegment ps,
                Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                Map<String, SchemaPlan> byPathPlans, PlanningContext context,
                Map<String, Boolean> vocabulary) {
        Object objectNode = schema.getNode(key);
        if (objectNode == null) return null;

        PathSegment cps = new PathSegment.Name(ps, key);
        if (!JsonType.of(objectNode).isObject()) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_INVALID,
                    "invalid schema node: expected JSON object",
                    cps, baseUri));
        }

        Map<String, SchemaPlan> planMap = new HashMap<>();
        Nodes.forEachObject(objectNode, (k, subNode) -> {
            PathSegment ccps = new PathSegment.Name(cps, k);
            SchemaPlan plan = _buildPlanFromNode(subNode, baseUri, ccps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary);
            planMap.put(k, plan);
        });
        return planMap;
    }

    /**
     * Compiles an array of subschemas from the given keyword.
     * <p>
     * Returned plans stay in the current schema resource unless an item schema
     * declares its own {@code $id}, in which case that item starts a new
     * resource with its own fragment space.
     */
    private static SchemaPlan[] _buildPlanArrayByKey(String key, ObjectSchema schema, URI baseUri, PathSegment ps,
                Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                Map<String, SchemaPlan> byPathPlans, PlanningContext context,
                Map<String, Boolean> vocabulary) {
        Object arrayNode = schema.getNode(key);
        if (arrayNode == null) return null;

        PathSegment cps = new PathSegment.Name(ps, key);
        if (!JsonType.of(arrayNode).isArray()) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_INVALID,
                    "invalid schema node: expected JSON array",
                    cps, baseUri));
        }
        int size = Nodes.sizeInArray(arrayNode);
        SchemaPlan[] planArr = new SchemaPlan[size];
        for (int i = 0; i < size; i++) {
            Object subNode = Nodes.getInArray(arrayNode, i);
            PathSegment ccps = new PathSegment.Index(cps, i);
            planArr[i] = _buildPlanFromNode(subNode, baseUri, ccps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary);
        }
        return planArr;
    }

    /**
     * Compiles one subschema from the given keyword and writes back if needed.
     */
    private static SchemaPlan _buildPlanByKey(String key, ObjectSchema schema, URI baseUri, PathSegment ps,
                                               Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                                               Map<String, SchemaPlan> byPathPlans,
                                               PlanningContext context, Map<String, Boolean> vocabulary) {
        if (!schema.containsKey(key)) return null;
        Object subNode = schema.getNode(key);
        PathSegment cps = new PathSegment.Name(ps, key);
        return _buildPlanFromNode(subNode, baseUri, cps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, vocabulary);
    }

    /**
     * Compiles a schema node into a {@link SchemaPlan}.
     * <p>
     * Boolean nodes compile as inline boolean plans. Object nodes stay in the
     * current resource unless they declare {@code $id}; a nested {@code $id}
     * starts a new schema resource and resets fragment lookup to that resource
     * root. Null/other types are invalid for schema positions.
     */
    private static SchemaPlan _buildPlanFromNode(Object node, URI idUri, PathSegment ps,
                                                   Map<String, SchemaPlan> byAnchorPlans, Map<String, SchemaPlan> byDynamicAnchorPlans,
                                                   Map<String, SchemaPlan> byPathPlans,
                                                   PlanningContext context, Map<String, Boolean> inheritedVocabulary) {
        if (node == null) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_INVALID,
                    "invalid schema node: schema is null", ps, idUri));
        }

        JsonSchema schema;
        if (node instanceof JsonSchema) {
            schema = (JsonSchema) node;
        } else {
            try {
                schema = Nodes.to(node, JsonSchema.class);
            } catch (Exception e) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_INVALID,
                        "invalid schema node type '" + node.getClass().getName() + "'", ps, idUri), e);
            }
        }

        if (schema instanceof BooleanSchema) {
            SchemaPlan plan = SchemaPlan.of(idUri, ps, (BooleanSchema) schema);
            byPathPlans.put(ps.rootedPointerExpr(), plan);
            return plan;
        }

        ObjectSchema os = (ObjectSchema) schema;
        String id = os.getId();
        if (id == null) {
            return _buildPlan(os, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, inheritedVocabulary);
        } else {
            idUri = idUri.resolve(id);
            Map<String, Boolean> vocabulary = _resolveVocabulary(os, inheritedVocabulary, context.registry);
            SchemaPlan plan = _buildPlan(os, idUri, PathSegment.Root.INSTANCE,
                    new HashMap<>(), new HashMap<>(), new HashMap<>(), context, vocabulary);
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

    /**
     * Placeholder hook for vocabulary-specific schema-shape checks.
     * <p>
     * SJF4J treats declared vocabularies as active whether they are required or
     * optional. A missing vocabulary entry means the planner should not compile
     * known keywords from that vocabulary by default. The current implementation
     * needs no extra per-keyword rejection here, so the hook is intentionally a
     * no-op.
     */
    private static void _checkVocabulary(ObjectSchema schema, PathSegment ps, Map<String, Boolean> vocabulary) {
        // The current planner only needs this hook for vocabulary-aware compile
        // decisions. Declared vocabularies may be required or optional; both are
        // still valid dialect members, so there is nothing to reject here.
    }

    /**
     * Returns whether a keyword should produce runtime evaluators.
     * <p>
     * Known keywords stay active when their vocabulary is declared in the
     * current dialect, regardless of whether that declaration is required or
     * optional. When a vocabulary is not declared at all, its known keywords
     * are skipped during compilation.
     */
    private static boolean _allowsKeyword(Map<String, Boolean> vocabulary, String keyword) {
        if (vocabulary == null) return true;
        String vocabUri = VocabularyRegistry.getVocabUri(keyword);
        if (vocabUri == null) return true;
        return vocabulary.containsKey(vocabUri);
    }

    /**
     * Returns whether the {@code format} keyword should be compiled.
     * <p>
     * The keyword is active when either format vocabulary is declared. Default
     * assertion behavior is controlled separately by
     * {@link #_isFormatAssertionEnabled(Map)}.
     */
    private static boolean _allowsFormatKeyword(Map<String, Boolean> vocabulary) {
        if (vocabulary == null) return true;
        return vocabulary.containsKey(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT_ASSERTION) ||
                vocabulary.containsKey(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT);
    }

    /**
     * Returns whether {@code format} should behave as an assertion by default.
     * <p>
     * Optional format-assertion support keeps the keyword active, but only an
     * explicit {@code true} entry enables assertion behavior automatically.
     */
    private static boolean _isFormatAssertionEnabled(Map<String, Boolean> vocabulary) {
        return vocabulary != null &&
                Boolean.TRUE.equals(vocabulary.get(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT_ASSERTION));
    }


}
