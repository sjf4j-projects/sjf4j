package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPath;
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


    static SchemaPlan buildAndPutPlan(ObjectSchema schema, SchemaRegistry registry) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(registry, "registry");

        URI retrievalUri = schema.getRetrievalUri();
        URI idUri = retrievalUri != null
                ? SchemaUtil.resolveUri(retrievalUri, schema.getCanonicalUri())
                : SchemaUtil.resolveUri(URI.create("sjf4j:/schema-" + schema.hashCode() + "/"), schema.getCanonicalUri());
        SchemaPlan plan = registry.resolveBuilt(idUri);
        if (plan != null) return plan;

        PlanningContext context = new PlanningContext(registry);
        SchemaDialect dialect = _resolveDialect(schema, registry.getDefaultDialect());
        Map<String, Boolean> vocabulary = _resolveVocabulary(schema, null, registry,
                idUri, PathSegment.Root.INSTANCE);
        plan = _buildPlan(schema, idUri, PathSegment.Root.INSTANCE,
                new HashMap<>(), new HashMap<>(), new HashMap<>(), context, dialect, vocabulary);
        context.registry.putPlan(idUri, plan);

        if (retrievalUri != null && !retrievalUri.equals(idUri)) {
            context.registry.putPlan(retrievalUri, plan);
        }
        _bindDeferredRefs(context, idUri);
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
                                         PlanningContext context, SchemaDialect dialect,
                                         Map<String, Boolean> vocabulary) {
        _checkVocabulary(schema, ps, vocabulary);

        // $defs / definitions
        if (_allowsKeyword(dialect, vocabulary, "$defs")) {
            _buildPlanMapByKey("$defs", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
        }
        _buildPlanMapByKey("definitions", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);

        // $anchor
        String anchor = schema.getString("$anchor");
        if (anchor == null) {
            String id = schema.getId();
            if (id != null && id.contains("#")) {
                anchor = URI.create(id).getFragment();
            }
        }

        // $dynamicAnchor / $recursiveAnchor
        String dynamicAnchor = schema.getString("$dynamicAnchor");
        if (dynamicAnchor == null && dialect == SchemaDialect.DRAFT_2019_09 &&
                _allowsKeyword(dialect, vocabulary, "$recursiveAnchor") && Boolean.TRUE.equals(schema.getBoolean("$recursiveAnchor"))) {
            dynamicAnchor = "";
        }

        // Build evaluators
        List<Evaluator> evaluators = new ArrayList<>();

        // $ref
        String ref = schema.getString("$ref");
        if (ref != null && _allowsKeyword(dialect, vocabulary, "$ref")) {
            Evaluator.RefEvaluator evaluator = new Evaluator.RefEvaluator(new PathSegment.Name(ps, "$ref"), idUri, ref);
            evaluators.add(evaluator);
            context.refEvaluators.add(evaluator);

            if (dialect == SchemaDialect.DRAFT_07) {
                // end up
                SchemaPlan plan = SchemaPlan.of(idUri, ps, evaluators, dynamicAnchor,
                        byAnchorPlans, byDynamicAnchorPlans, byPathPlans,
                        schema, dialect, vocabulary);
                byPathPlans.put(ps.rootedPointerExpr(), plan);
                return plan;
            }
        }

        // $dynamicRef / $recursiveRef
        String dynamicRef = schema.getString("$dynamicRef");
        if (dynamicRef == null && dialect == SchemaDialect.DRAFT_2019_09
                && _allowsKeyword(dialect, vocabulary, "$recursiveRef")) {
            dynamicRef = schema.getString("$recursiveRef");
        }
        if (dynamicRef != null && _allowsKeyword(dialect, vocabulary, "$dynamicRef")) {
            Evaluator.DynamicRefEvaluator evaluator = new Evaluator.DynamicRefEvaluator(
                    new PathSegment.Name(ps, "$dynamicRef"), idUri, dynamicRef);
            evaluators.add(evaluator);
            context.dynamicRefEvaluators.add(evaluator);
        } else if (dynamicRef != null && dialect == SchemaDialect.DRAFT_2019_09
                && _allowsKeyword(dialect, vocabulary, "$recursiveRef")) {
            Evaluator.DynamicRefEvaluator evaluator = new Evaluator.DynamicRefEvaluator(
                    new PathSegment.Name(ps, "$recursiveRef"), idUri, dynamicRef);
            evaluators.add(evaluator);
            context.dynamicRefEvaluators.add(evaluator);
        }

        // format
        String format = schema.getString("format");
        if (format != null && _allowsFormatKeyword(vocabulary)) {
            evaluators.add(new Evaluator.FormatEvaluator(new PathSegment.Name(ps, "format"), idUri,
                    format, _isFormatAssertionEnabled(vocabulary)));
        }

        // contentEncoding / contentMediaType (draft7 optional support)
        if (dialect == SchemaDialect.DRAFT_07) {
            String contentEncoding = _allowsKeyword(dialect, vocabulary, "contentEncoding")
                    ? schema.getString("contentEncoding")
                    : null;
            String contentMediaType = _allowsKeyword(dialect, vocabulary, "contentMediaType")
                    ? schema.getString("contentMediaType")
                    : null;
            if (contentEncoding != null || contentMediaType != null) {
                evaluators.add(new Evaluator.ContentEvaluator(
                        new PathSegment.Name(ps, "contentEncoding"),
                        new PathSegment.Name(ps, "contentMediaType"),
                        idUri, contentEncoding, contentMediaType));
            }
        }

        // type
        Object type = schema.getNode("type");
        if (type != null && _allowsKeyword(dialect, vocabulary, "type")) {
            evaluators.add(new Evaluator.TypeEvaluator(new PathSegment.Name(ps, "type"), idUri, type));
        }

        // const
        if (schema.containsKey("const") && _allowsKeyword(dialect, vocabulary, "const")) {
            evaluators.add(new Evaluator.ConstEvaluator(new PathSegment.Name(ps, "const"), idUri,
                    schema.getNode("const")));
        }

        // enum
        Object[] enumValues = schema.getArray("enum");
        if (enumValues != null && _allowsKeyword(dialect, vocabulary, "enum")) {
            evaluators.add(new Evaluator.EnumEvaluator(new PathSegment.Name(ps, "enum"), idUri, enumValues));
        }

        // minimum maximum exclusiveMinimum exclusiveMaximum
        Number minimum = schema.getNumber("minimum");
        Number maximum = schema.getNumber("maximum");
        Number exclusiveMinimum = schema.getNumber("exclusiveMinimum");
        Number exclusiveMaximum = schema.getNumber("exclusiveMaximum");
        if (_allowsKeyword(dialect, vocabulary, "minimum") &&
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
        if (multipleOf != null && _allowsKeyword(dialect, vocabulary, "multipleOf")) {
            evaluators.add(new Evaluator.MultipleOfEvaluator(new PathSegment.Name(ps, "multipleOf"), idUri,
                    multipleOf));
        }

        // minLength maxLength
        Integer minLength = schema.getInt("minLength");
        Integer maxLength = schema.getInt("maxLength");
        if (_allowsKeyword(dialect, vocabulary, "minLength") && (minLength != null || maxLength != null)) {
            evaluators.add(new Evaluator.StringEvaluator(new PathSegment.Name(ps, "minLength"),
                    new PathSegment.Name(ps, "maxLength"), idUri, minLength, maxLength));
        }

        // pattern
        String pattern = schema.getString("pattern");
        if (pattern != null && _allowsKeyword(dialect, vocabulary, "pattern")) {
            evaluators.add(new Evaluator.PatternEvaluator(new PathSegment.Name(ps, "pattern"), idUri, pattern));
        }

        // minProperties / maxProperties
        Integer minProperties = schema.getInt("minProperties");
        Integer maxProperties = schema.getInt("maxProperties");
        if (_allowsKeyword(dialect, vocabulary, "minProperties") && (minProperties != null || maxProperties != null)) {
            evaluators.add(new Evaluator.ObjectEvaluator(
                    new PathSegment.Name(ps, "minProperties"),
                    new PathSegment.Name(ps, "maxProperties"),
                    idUri, minProperties, maxProperties));
        }

        // required / dependentRequired
        String[] required = _allowsKeyword(dialect, vocabulary, "required")
                ? schema.getArray("required", String.class)
                : null;
        Map<String, String[]> dependentRequired = _allowsKeyword(dialect, vocabulary, "dependentRequired")
                ? schema.getMap("dependentRequired", String[].class)
                : null;
        if (required != null || dependentRequired != null) {
            evaluators.add(new Evaluator.RequiredEvaluator(
                    new PathSegment.Name(ps, "required"),
                    new PathSegment.Name(ps, "dependentRequired"),
                    idUri, required, dependentRequired));
        }

        // properties / patternProperties / additionalProperties
        Map<String, SchemaPlan> properties = _allowsKeyword(dialect, vocabulary, "properties")
                ? _buildPlanMapByKey("properties", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        Map<String, SchemaPlan> patternProperties = _allowsKeyword(dialect, vocabulary, "patternProperties")
                ? _buildPlanMapByKey("patternProperties", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        SchemaPlan additionalProperties = _allowsKeyword(dialect, vocabulary, "additionalProperties")
                ? _buildPlanByKey("additionalProperties", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if (properties != null || patternProperties != null || additionalProperties != null) {
            evaluators.add(new Evaluator.PropertiesEvaluator(properties, patternProperties, additionalProperties));
        }

        // dependentSchemas
        Map<String, SchemaPlan> dependentPlans = _allowsKeyword(dialect, vocabulary, "dependentSchemas")
                ? _buildPlanMapByKey("dependentSchemas", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if (dependentPlans != null) {
            evaluators.add(new Evaluator.DependentSchemasEvaluator(dependentPlans));
        }

        // dependencies
        Object dependenciesNode = schema.getNode("dependencies");
        Map<String, String[]> dependenciesRequired = null;
        Map<String, SchemaPlan> dependenciesPlans = null;
        if (dependenciesNode != null && JsonType.of(dependenciesNode).isObject()) {
            PathSegment cps = new PathSegment.Name(ps, "dependencies");
            for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(dependenciesNode)) {
                Object value = entry.getValue();
                JsonType valueType = JsonType.of(value);
                if (valueType.isArray()) {
                    if (dependenciesRequired == null) dependenciesRequired = new HashMap<>();
                    dependenciesRequired.put(entry.getKey(), Nodes.toArray(value, String.class));
                } else if (valueType.isObject() || valueType.isBoolean()) {
                    if (dependenciesPlans == null) dependenciesPlans = new HashMap<>();
                    PathSegment ccps = new PathSegment.Name(cps, entry.getKey());
                    SchemaPlan childPlan = _buildPlanFromNode(value, idUri, ccps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
                    dependenciesPlans.put(entry.getKey(), childPlan);
                }
            }
        }
        if (dependenciesRequired != null || dependenciesPlans != null) {
            evaluators.add(new Evaluator.DependenciesEvaluator(new PathSegment.Name(ps, "dependencies"), idUri,
                    dependenciesRequired, dependenciesPlans));
        }

        // propertyNames
        SchemaPlan propertyNamesPlan = _allowsKeyword(dialect, vocabulary, "propertyNames")
                ? _buildPlanByKey("propertyNames", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if (propertyNamesPlan != null) {
            evaluators.add(new Evaluator.PropertyNamesEvaluator(new PathSegment.Name(ps, "propertyNames"), idUri,
                    propertyNamesPlan));
        }

        // minItems / maxItems / uniqueItems
        Integer minItems = schema.getInt("minItems");
        Integer maxItems = schema.getInt("maxItems");
        Boolean uniqueItems = schema.getBoolean("uniqueItems");
        if (_allowsKeyword(dialect, vocabulary, "minItems")
                && (minItems != null || maxItems != null || uniqueItems != null)) {
            evaluators.add(new Evaluator.ArrayEvaluator(
                    new PathSegment.Name(ps, "minItems"),
                    new PathSegment.Name(ps, "maxItems"),
                    new PathSegment.Name(ps, "uniqueItems"),
                    idUri, minItems, maxItems, uniqueItems));
        }

        // items / prefixItems
        SchemaPlan itemsPlan = null;
        Object itemsNode = schema.getNode("items");
        if (_allowsKeyword(dialect, vocabulary, "items") && schema.containsKey("items")) {
            if (!((dialect == SchemaDialect.DRAFT_2019_09 || dialect == SchemaDialect.DRAFT_07)
                    && itemsNode != null && JsonType.of(itemsNode).isArray())) {
                itemsPlan = _buildPlanFromNode(itemsNode, idUri, new PathSegment.Name(ps, "items"),
                        byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
            }
        }
        SchemaPlan[] prefixItemsPlans = _allowsKeyword(dialect, vocabulary, "prefixItems")
                ? _buildPlanArrayByKey("prefixItems", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if ((dialect == SchemaDialect.DRAFT_2019_09 || dialect == SchemaDialect.DRAFT_07)
                && prefixItemsPlans == null && _allowsKeyword(dialect, vocabulary, "items")) {
            if (itemsNode != null && JsonType.of(itemsNode).isArray()) {
                prefixItemsPlans = _buildPlanArrayByKey("items", schema, idUri, ps,
                        byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
            }
            if (prefixItemsPlans != null && itemsPlan == null && _allowsKeyword(dialect, vocabulary, "items")) {
                itemsPlan = _buildPlanByKey("additionalItems", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
            }
        }
        if (itemsPlan != null || prefixItemsPlans != null) {
            evaluators.add(new Evaluator.ItemsEvaluator(itemsPlan, prefixItemsPlans));
        }

        // contains / minContains / maxContains
        SchemaPlan containsPlan = _allowsKeyword(dialect, vocabulary, "contains")
                ? _buildPlanByKey("contains", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        Integer minContains = schema.getInt("minContains");
        Integer maxContains = schema.getInt("maxContains");
        if ((_allowsKeyword(dialect, vocabulary, "contains") || _allowsKeyword(dialect, vocabulary, "minContains")) &&
                (containsPlan != null || minContains != null || maxContains != null)) {
            evaluators.add(new Evaluator.ContainsEvaluator(
                    new PathSegment.Name(ps, "minContains"),
                    new PathSegment.Name(ps, "maxContains"),
                    idUri, containsPlan, minContains, maxContains));
        }

        // if / then / else
        SchemaPlan ifPlan = _allowsKeyword(dialect, vocabulary, "if")
                ? _buildPlanByKey("if", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        SchemaPlan thenPlan = _allowsKeyword(dialect, vocabulary, "then")
                ? _buildPlanByKey("then", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        SchemaPlan elsePlan = _allowsKeyword(dialect, vocabulary, "else")
                ? _buildPlanByKey("else", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if (ifPlan != null || thenPlan != null || elsePlan != null) {
            evaluators.add(new Evaluator.IfThenElseEvaluator(ifPlan, thenPlan, elsePlan));
        }

        // allOf
        SchemaPlan[] allOfPlans = _allowsKeyword(dialect, vocabulary, "allOf")
                ? _buildPlanArrayByKey("allOf", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if (allOfPlans != null) {
            evaluators.add(new Evaluator.AllOfEvaluator(allOfPlans));
        }

        // anyOf
        SchemaPlan[] anyOfPlans = _allowsKeyword(dialect, vocabulary, "anyOf")
                ? _buildPlanArrayByKey("anyOf", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if (anyOfPlans != null) {
            evaluators.add(new Evaluator.AnyOfEvaluator(new PathSegment.Name(ps, "anyOf"), idUri, anyOfPlans));
        }

        // oneOf
        SchemaPlan[] oneOfPlans = _allowsKeyword(dialect, vocabulary, "oneOf")
                ? _buildPlanArrayByKey("oneOf", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if (oneOfPlans != null) {
            evaluators.add(new Evaluator.OneOfEvaluator(new PathSegment.Name(ps, "oneOf"), idUri, oneOfPlans));
        }

        // not
        SchemaPlan notPlan = _allowsKeyword(dialect, vocabulary, "not")
                ? _buildPlanByKey("not", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary) : null;
        if (notPlan != null) {
            evaluators.add(new Evaluator.NotEvaluator(new PathSegment.Name(ps, "not"), idUri, notPlan));
        }

        // unevaluatedProperties / unevaluatedItems
        SchemaPlan unevaluatedPropertiesPlan = _allowsKeyword(dialect, vocabulary, "unevaluatedProperties")
                ? _buildPlanByKey("unevaluatedProperties", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        SchemaPlan unevaluatedItemsPlan = _allowsKeyword(dialect, vocabulary, "unevaluatedItems")
                ? _buildPlanByKey("unevaluatedItems", schema, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary)
                : null;
        if (unevaluatedPropertiesPlan != null || unevaluatedItemsPlan != null) {
            evaluators.add(new Evaluator.UnevaluatedEvaluator(unevaluatedPropertiesPlan, unevaluatedItemsPlan));
        }

        // end up
        SchemaPlan plan = SchemaPlan.of(idUri, ps, evaluators, dynamicAnchor,
                byAnchorPlans, byDynamicAnchorPlans, byPathPlans,
                schema, dialect, vocabulary);
        if (anchor != null) _putNamedFragment(byAnchorPlans, anchor, plan, "$anchor", idUri, ps);
        if (dynamicAnchor != null) {
            _putNamedFragment(byAnchorPlans, dynamicAnchor, plan, "$dynamicAnchor", idUri, ps);
            byDynamicAnchorPlans.put(dynamicAnchor, plan);
        }
        byPathPlans.put(ps.rootedPointerExpr(), plan);
        return plan;
    }

    static SchemaPlan lazyBuildPlanByPath(SchemaPlan resourcePlan, String path, SchemaRegistry registry) {
        Objects.requireNonNull(resourcePlan, "resourcePlan");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(registry, "registry");
        if (!path.startsWith("/") || resourcePlan.schema == null) return null;

        JsonPath jsonPath = JsonPath.parse(path);
        Object node = jsonPath.getNode(resourcePlan.schema);
        if (node == null) return null;
        PlanningContext context = new PlanningContext(registry);
        SchemaPlan plan = _buildPlanFromNode(node, resourcePlan.schemaUri, jsonPath.tail(),
                resourcePlan.byAnchorPlans, resourcePlan.byDynamicAnchorPlans, resourcePlan.byPathPlans,
                context, resourcePlan.dialect, resourcePlan.vocabulary);
        _bindDeferredRefs(context, resourcePlan.schemaUri);
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
    private static Map<String, SchemaPlan> _buildPlanMapByKey(String key, ObjectSchema schema, URI baseUri, PathSegment ps,
                                                              Map<String, SchemaPlan> byAnchorPlans,
                                                              Map<String, SchemaPlan> byDynamicAnchorPlans,
                                                              Map<String, SchemaPlan> byPathPlans,
                                                              PlanningContext context,
                                                              SchemaDialect dialect, Map<String, Boolean> vocabulary) {
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
            SchemaPlan plan = _buildPlanFromNode(subNode, baseUri, ccps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
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
                                                     Map<String, SchemaPlan> byAnchorPlans,
                                                     Map<String, SchemaPlan> byDynamicAnchorPlans,
                                                     Map<String, SchemaPlan> byPathPlans,
                                                     PlanningContext context,
                                                     SchemaDialect dialect, Map<String, Boolean> vocabulary) {
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
            planArr[i] = _buildPlanFromNode(subNode, baseUri, ccps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
        }
        return planArr;
    }

    /**
     * Compiles one subschema from the given keyword and writes back if needed.
     */
    private static SchemaPlan _buildPlanByKey(String key, ObjectSchema schema, URI baseUri, PathSegment ps,
                                              Map<String, SchemaPlan> byAnchorPlans,
                                              Map<String, SchemaPlan> byDynamicAnchorPlans,
                                              Map<String, SchemaPlan> byPathPlans,
                                              PlanningContext context,
                                              SchemaDialect dialect, Map<String, Boolean> vocabulary) {
        if (!schema.containsKey(key)) return null;
        Object subNode = schema.getNode(key);
        PathSegment cps = new PathSegment.Name(ps, key);
        return _buildPlanFromNode(subNode, baseUri, cps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
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
                                                    PlanningContext context, SchemaDialect inheritedDialect,
                                                    Map<String, Boolean> inheritedVocabulary) {
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

        SchemaPlan plan;
        String path = ps.rootedPointerExpr();
        if (schema instanceof BooleanSchema) {
            plan = SchemaPlan.of(idUri, ps, (BooleanSchema) schema);
        } else {
            ObjectSchema os = (ObjectSchema) schema;
            SchemaDialect dialect = _resolveDialect(os, inheritedDialect);
            Map<String, Boolean> vocabulary = _resolveVocabulary(os, inheritedVocabulary, context.registry, idUri, ps);

            String id = SchemaUtil.stripFragment(os.getId());
            String ref = os.getString("$ref");
            if ((dialect == SchemaDialect.DRAFT_07 && ref != null) || id == null || id.isEmpty()) {
                plan = _buildPlan(os, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
            } else {
                URI childIdUri = idUri.resolve(id);
                if (childIdUri.equals(idUri)) {
                    plan = _buildPlan(os, idUri, ps, byAnchorPlans, byDynamicAnchorPlans, byPathPlans, context, dialect, vocabulary);
                } else {
                    plan = _buildPlan(os, childIdUri, PathSegment.Root.INSTANCE,
                            new HashMap<>(), new HashMap<>(), new HashMap<>(), context, dialect, vocabulary);
                    context.registry.putPlan(childIdUri, plan);

                    for (Map.Entry<String, SchemaPlan> entry : plan.byPathPlans.entrySet()) {
                        byPathPlans.put(path + entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        byPathPlans.put(path, plan);
        return plan;
    }

    private static void _bindDeferredRefs(PlanningContext context, URI idUri) {
        for (Evaluator.RefEvaluator refEvaluator : context.refEvaluators) {
            URI refUri = SchemaUtil.resolveUri(refEvaluator.schemaUri, URI.create(refEvaluator.ref));
            String resource = SchemaUtil.stripFragment(refUri.toString());

            // Resolve resource and fragment separately so diagnostics can tell
            // callers whether they forgot to preload a remote schema or simply
            // referenced a bad anchor / JSON Pointer inside an existing one.
            SchemaPlan resourcePlan = context.registry.resolveResource(refUri);
            if (resourcePlan == null) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cannot resolve schema resource '" + resource + "' while resolving $ref '"
                                + refEvaluator.ref + "' -> '" + refUri + "'; preload or register the referenced schema",
                        refEvaluator.keywordPs, idUri));
            }
            String fragment = refUri.getFragment();
            SchemaPlan refPlan = context.registry.resolveFragment(resourcePlan, fragment);
            if (refPlan == null) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cannot resolve schema fragment '#" + fragment + "' in resource '" + resource
                                + "' while resolving $ref '" + refEvaluator.ref + "' -> '" + refUri + "'",
                        refEvaluator.keywordPs, idUri));
            }
            refEvaluator.plan = refPlan;
        }
        for (Evaluator.DynamicRefEvaluator dynamicRefEvaluator : context.dynamicRefEvaluators) {
            URI refUri = SchemaUtil.resolveUri(dynamicRefEvaluator.schemaUri, URI.create(dynamicRefEvaluator.ref));
            String resource = SchemaUtil.stripFragment(refUri.toString());

            // Dynamic references use the same static target resolution first;
            // runtime rebinding only happens after this initial plan is known.
            SchemaPlan resourcePlan = context.registry.resolveResource(refUri);
            if (resourcePlan == null) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cannot resolve schema resource '" + resource + "' while resolving $dynamicRef '"
                                + dynamicRefEvaluator.ref + "' -> '" + refUri + "'; preload or register the referenced schema",
                        dynamicRefEvaluator.keywordPs, idUri));
            }
            String fragment = refUri.getFragment();
            SchemaPlan refPlan = context.registry.resolveFragment(resourcePlan, fragment);
            if (refPlan == null) {
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cannot resolve schema fragment '#" + fragment + "' in resource '" + resource
                                + "' while resolving $dynamicRef '" + dynamicRefEvaluator.ref + "' -> '" + refUri + "'",
                        dynamicRefEvaluator.keywordPs, idUri));
            }
            dynamicRefEvaluator.initialPlan = refPlan;
            dynamicRefEvaluator.dynamicAnchorName = fragment != null && fragment.startsWith("/")
                    ? null : refPlan.dynamicAnchor;
        }
    }

    private static SchemaDialect _resolveDialect(ObjectSchema schema, SchemaDialect inheritedDialect) {
        SchemaDialect dialect = SchemaDialect.detect(schema.getString("$schema"));
        if (dialect == null) dialect = inheritedDialect;
        return dialect;
    }

    private static Map<String, Boolean> _resolveVocabulary(ObjectSchema schema,
                                                            Map<String, Boolean> inheritedVocabulary,
                                                            SchemaRegistry registry,
                                                            URI resourceUri,
                                                            PathSegment ps) {
        Map<String, Boolean> vocabulary = schema.getVocabulary();
        if (vocabulary != null) return vocabulary;

        String schemaUri = schema.getString("$schema");
        if (schemaUri != null) {
            ObjectSchema metaSchema = registry.resolveSchema(URI.create(schemaUri));
            if (metaSchema != null) {
                Map<String, Boolean> metaVocabulary = metaSchema.getVocabulary();
                if (metaVocabulary != null) return metaVocabulary;
            } else if (SchemaDialect.detect(schemaUri) == null) {
                // A custom metaschema controls vocabulary activation. If it is
                // unavailable, silently falling back to the inherited dialect can
                // compile the wrong keyword set, so fail at schema compile time.
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_RESOLVE,
                        "cannot resolve $schema '" + schemaUri
                                + "' to determine dialect/vocabulary; preload or register the metaschema",
                        new PathSegment.Name(ps, "$schema"), resourceUri));
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
    private static boolean _allowsKeyword(SchemaDialect dialect, Map<String, Boolean> vocabulary, String keyword) {
        if (!dialect.supportsKeyword(keyword)) return false;

        if (vocabulary == null) return true;
        String[] vocabUris = VocabularyRegistry.getVocabUris(keyword);
        if (vocabUris == null) return true;
        for (String vocabUri : vocabUris) {
            if (vocabulary.containsKey(vocabUri)) return true;
        }
        return false;
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
                vocabulary.containsKey(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT) ||
                vocabulary.containsKey(VocabularyRegistry.DRAFT_2019_09_VOCAB_FORMAT);
    }

    /**
     * Returns whether {@code format} should behave as an assertion by default.
     * <p>
     * Once the implementation recognizes the format-assertion vocabulary, the
     * boolean value only affects unknown-vocabulary handling. Presence of the
     * vocabulary entry means {@code format} behaves as an assertion.
     */
    private static boolean _isFormatAssertionEnabled(Map<String, Boolean> vocabulary) {
        return vocabulary != null &&
                vocabulary.containsKey(VocabularyRegistry.DRAFT_2020_12_VOCAB_FORMAT_ASSERTION);
    }


}
