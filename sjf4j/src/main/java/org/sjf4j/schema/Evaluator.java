package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.node.Numbers;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public interface Evaluator {

    boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx);

//    default boolean probeToEvaluate(Object subSchema, InstancedNode instance,
//                                    PathSegment ps, ValidationContext ctx) {
//        ValidationContext probeCtx = ctx.createProbe();
//        evaluate(subSchema, instance, path, probeCtx);
//        return probeCtx.isValid();
//    }

    default boolean evaluate(Object subSchema, String keyword, InstancedNode instance,
                             PathSegment ps, ValidationContext ctx) {
        if (subSchema == null) {
            ctx.addError(JsonPointer.fromLast(ps), keyword, "Not found schema");
            return false;
        }
        if (subSchema instanceof Boolean) {
            if (!(Boolean) subSchema) {
                ctx.addError(JsonPointer.fromLast(ps), keyword, "Schema 'false' always fails");
                return false;
            } else {
                return true;
            }
        }
        if (subSchema instanceof ObjectSchema) {
            return ((ObjectSchema) subSchema)._validate(instance, ps, ctx);
        }
        ctx.addError(JsonPointer.fromLast(ps), keyword, "Not a valid schema type " + subSchema.getClass().getName());
        return false;
    }

    default boolean evaluateProperty(Object subSchema, String keyword, InstancedNode instance,
                                     PathSegment ps, String key, ValidationContext ctx) {
        InstancedNode subInstance = instance.getSubByKey(key);
        PathSegment cps = new PathSegment.Name(ps, instance.getObjectType(), key);
        return evaluate(subSchema, keyword, subInstance, cps, ctx);
    }

    default boolean evaluateItem(Object subSchema, String keyword, InstancedNode instance,
                                 PathSegment ps, int idx, ValidationContext ctx) {
        InstancedNode subInstance = instance.getSubByIndex(idx);
        PathSegment cps = new PathSegment.Index(ps, instance.getObjectType(), idx);
        return evaluate(subSchema, keyword, subInstance, cps, ctx);
    }


    /// Build-in evaluators

    // $ref
    class RefEvaluator implements Evaluator {
        private final URI uri;
        private final JsonPointer refPath;
        private final String anchor;
        public RefEvaluator(URI uri, JsonPointer refPath, String anchor) {
            this.uri = uri;
            this.refPath = refPath;
            this.anchor = anchor;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (refPath != null) {
                Object schema = ctx.getSchemaByPath(uri, refPath);
                if (schema != null) {
                    if (schema instanceof Boolean) {
                        return evaluate(schema, "$ref", instance, ps, ctx);
                    } else if (schema instanceof ObjectSchema) {
                        if (instance.isRecursiveRef(schema)) return true;
                        return evaluate(schema, "$ref", instance, ps, ctx);
                    } else {
                        ctx.addError(JsonPointer.fromLast(ps), "$ref",
                                "Not a valid schema by path '" + refPath + "' in " + uri);
                        return false;
                    }
                }
                ctx.addWarn(JsonPointer.fromLast(ps), "$ref",
                        "Not found schema by path '" + refPath + "' in URI " + uri);
            }
            if (anchor != null) { // Always true
                ObjectSchema schema = ctx.getSchemaByAnchor(uri, anchor);
                if (schema == null) {
                    if (anchor.isEmpty()) {
                        ctx.addError(JsonPointer.fromLast(ps), "$ref",
                                "Not found schema at URI " + uri);
                    } else {
                        ctx.addError(JsonPointer.fromLast(ps), "$ref",
                                "Not found anchor '" + anchor + "' in URI " + uri);
                    }
                    return false;
                }
                if (instance.isRecursiveRef(schema)) return true;
                return evaluate(schema, "$ref", instance, ps, ctx);
            }
            throw new AssertionError(RefEvaluator.class);
        }
    }

    // $dynamicRef
    class DynamicRefEvaluator implements Evaluator {
        private final URI uri;
        private final JsonPointer refPath;
        private final String dynamicAnchor;
        public DynamicRefEvaluator(URI uri, JsonPointer refPath, String dynamicAnchor) {
            this.uri = uri;
            this.refPath = refPath;
            this.dynamicAnchor = dynamicAnchor;
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            ObjectSchema schema = ctx.getSchemaByDynamicAnchor(uri, dynamicAnchor);
            if (schema == null) {
                if (refPath != null) {
                    Object schemaNode = ctx.getSchemaByPath(uri, refPath);
                    if (schemaNode != null) {
                        if (schemaNode instanceof Boolean) {
                            return evaluate(schemaNode, "$dynamicRef", instance, ps, ctx);
                        } else if (schemaNode instanceof ObjectSchema) {
                            if (instance.isRecursiveRef(schemaNode)) return true;
                            return evaluate(schemaNode, "$dynamicRef", instance, ps, ctx);
                        } else {
                            ctx.addError(JsonPointer.fromLast(ps), "$dynamicRef",
                                    "Not a valid schema by path '" + refPath + "' in " + uri);
                            return false;
                        }
                    }
                    ctx.addWarn(JsonPointer.fromLast(ps), "$dynamicRef",
                            "Not found schema by path '" + refPath + "' in URI " + uri);
                }
                schema = ctx.getSchemaByAnchor(uri, dynamicAnchor);
                if (schema == null) {
                    if (dynamicAnchor.isEmpty()) {
                        ctx.addError(JsonPointer.fromLast(ps), "$dynamicRef", "Not found schema at URI " + uri);
                    } else {
                        ctx.addError(JsonPointer.fromLast(ps), "$dynamicRef",
                                "Not found anchor '" + dynamicAnchor + "' in URI " + uri);
                    }
                    return false;
                }
            }
            if (instance.isRecursiveRef(schema)) return true;
            return evaluate(schema, "$dynamicRef", instance, ps, ctx);
        }
    }

    // type
    class TypeEvaluator implements Evaluator {
        private final String type;
        private final JsonType jsonType;
        private final String[] types;
        private final JsonType[] jsonTypes;

        public TypeEvaluator(Object type) {
            Objects.requireNonNull(type, "type is null");
            JsonType jt = JsonType.of(type);
            if (jt.isString()) {
                this.type = (String) type;
                this.jsonType = JsonType.ofSchema(this.type);
                this.types = null;
                this.jsonTypes = null;
            } else if (jt.isArray()) {
                this.type = null;
                this.jsonType = null;
                this.types = Nodes.toArray(type, String.class);
                this.jsonTypes = (JsonType[]) Array.newInstance(JsonType.class, types.length);
                for (int i = 0; i < types.length; i++) this.jsonTypes[i] = JsonType.ofSchema(types[i]);
            } else {
                throw new IllegalArgumentException("TypeEvaluator only supports String or Array, but found: " +
                        type.getClass().getSimpleName());
            }
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (jsonType != null) {
                if (!matches(jsonType, instance)) {
                    ctx.addError(JsonPointer.fromLast(ps), "type", "Expected type " + type +
                            ", but found " + instance.getJsonType());
                    return false;
                }
                return true;
            } else if (jsonTypes != null) {
                for (JsonType expected : jsonTypes) {
                    if (matches(expected, instance)) {
                        return true; // matched any one
                    }
                }
                ctx.addError(JsonPointer.fromLast(ps), "type", "Expected one of " + Arrays.toString(types) +
                        ", but found " + instance.getJsonType());
                return false;
            }
            return true;
        }

        private boolean matches(JsonType expected, InstancedNode instance) {
            JsonType jt = instance.getJsonType();
            if (expected != jt) {
                // JSON Schema compatibility: integer ⊂ number
                if (expected == JsonType.INTEGER && jt == JsonType.NUMBER) {
                    Object actual = instance.getNode();
                    Number number = Nodes.toNumber(actual);
                    return number != null && Numbers.isSemanticInteger(number);
                }
                return false;
            }
            return true;
        }
    }

    // const
    class ConstEvaluator implements Evaluator {
        private final Object constValue;
        public ConstEvaluator(Object constValue) {
            this.constValue = constValue;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            Object actual = instance.getNode();
            if (!Nodes.equals(constValue, actual)) {
                ctx.addError(JsonPointer.fromLast(ps), "const", "Value does not match constant. Expected: " +
                        Nodes.inspect(constValue) + ", actual: " + Nodes.inspect(actual));
                return false;
            }
            return true;
        }
    }

    // enum
    class EnumEvaluator implements Evaluator {
        private final Object[] enumValues;
        public EnumEvaluator(Object[] enumValues) {
            this.enumValues = Objects.requireNonNull(enumValues);
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            Object actual = instance.getNode();
            for (Object allowed : enumValues) {
                if (Nodes.equals(allowed, actual)) return true;
            }
            ctx.addError(JsonPointer.fromLast(ps), "enum", "Value not in enum: " + Nodes.inspect(enumValues));
            return false;
        }
    }


    // minimum / maximum / exclusiveMinimum / exclusiveMaximum
    class NumberEvaluator implements Evaluator {
        private final Number minimum;
        private final Number maximum;
        private final Number exclusiveMinimum;
        private final Number exclusiveMaximum;
        public NumberEvaluator(Number minimum, Number maximum,
                               Number exclusiveMinimum, Number exclusiveMaximum) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.exclusiveMinimum = exclusiveMinimum;
            this.exclusiveMaximum = exclusiveMaximum;
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.NUMBER && instance.getJsonType() != JsonType.INTEGER) {
                return true;
            }
            Number actual = (Number) instance.getNode();
            if (minimum != null && Numbers.compare(actual, minimum) < 0) {
                ctx.addError(JsonPointer.fromLast(ps), "minimum", "Number must >= " + minimum);
                return false;
            } else if (maximum != null && Numbers.compare(actual, maximum) > 0) {
                ctx.addError(JsonPointer.fromLast(ps), "maximum", "Number must <= " + maximum);
                return false;
            } else if (exclusiveMinimum != null && Numbers.compare(actual, exclusiveMinimum) <= 0) {
                ctx.addError(JsonPointer.fromLast(ps), "exclusiveMinimum", "Number must > " + exclusiveMinimum);
                return false;
            } else if (exclusiveMaximum != null && Numbers.compare(actual, exclusiveMaximum) >= 0) {
                ctx.addError(JsonPointer.fromLast(ps), "exclusiveMaximum", "Number must < " + exclusiveMaximum);
                return false;
            }
            return true;
        }
    }

    // multipleOf
    class MultipleOfEvaluator implements Evaluator {
        private final Number multipleOf;
        private final BigDecimal divisor;
        private final boolean isIntegerDivisor;
        private final long divisorLong;
        private final double divisorDouble;
        public MultipleOfEvaluator(Number multipleOf) {
            this.multipleOf = multipleOf;
            this.divisor = Numbers.normalizeDecimal(multipleOf);
            if (divisor.signum() <= 0)
                throw new IllegalArgumentException("multipleOf must > 0");
            this.isIntegerDivisor = divisor.scale() <= 0;
            this.divisorLong = isIntegerDivisor ? divisor.longValueExact() : 0L;
            this.divisorDouble = divisor.doubleValue();
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.NUMBER && instance.getJsonType() != JsonType.INTEGER) {
                return true;
            }
            Number actual = (Number) instance.getNode();
            if (isIntegerDivisor && Numbers.isSemanticInteger(actual)) {
                long v = actual.longValue();
                if (v % divisorLong != 0) {
                    ctx.addError(JsonPointer.fromLast(ps),"multipleOf", "Number not a multiple of " + multipleOf);
                    return false;
                }
            } else if (isIntegerDivisor && (actual instanceof Double || actual instanceof Float)) {
                double dv = actual.doubleValue();
                double q = dv / divisorDouble;
                if (q != Math.rint(q)) {
                    ctx.addError(JsonPointer.fromLast(ps),"multipleOf", "Number not a multiple of " + multipleOf);
                    return false;
                }
            } else {
                BigDecimal v = Numbers.normalizeDecimal(actual);
                BigDecimal[] dr = v.divideAndRemainder(divisor);
                if (dr[1].signum() != 0) {
                    ctx.addError(JsonPointer.fromLast(ps), "multipleOf", "Number not a multiple of " + multipleOf);
                    return false;
                }
            }
            return true;
        }
    }

    // minLength / maxLength
    class StringEvaluator implements Evaluator {
        private final Integer minLength;
        private final Integer maxLength;
        public StringEvaluator(Integer minLength, Integer maxLength) {
            this.minLength = minLength;
            this.maxLength = maxLength;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return true;
            String actual = (String) instance.getNode();
            int length = StringUtil.length(actual);
            if (minLength != null && length < minLength) {
                ctx.addError(JsonPointer.fromLast(ps), "minLength", "String length must >= " + minLength);
                return false;
            }
            if (maxLength != null && length > maxLength) {
                ctx.addError(JsonPointer.fromLast(ps), "maxLength", "String length must <= " + maxLength);
                return false;
            }
            return true;
        }
    }

    // pattern
    class PatternEvaluator implements Evaluator {
        private final String pattern;
        private final Pattern pn;
        public PatternEvaluator(String pattern) {
            this.pattern = Objects.requireNonNull(pattern);
            this.pn = Pattern.compile(pattern);
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return true;
            String actual = (String) instance.getNode();
            if (!pn.matcher(actual).find()) {
                ctx.addError(JsonPointer.fromLast(ps), "pattern", "String must match pattern: " + pattern);
                return false;
            }
            return true;
        }
    }

    // format
    class FormatEvaluator implements Evaluator {
        private final String format;
        private final FormatValidator formatValidator;
        public FormatEvaluator(String format) {
            this.format = Objects.requireNonNull(format, "format is null");
            this.formatValidator = FormatValidator.of(format);
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return true;
            String actual = (String) instance.getNode();
            if (ctx.getOptions().isStrictFormat()) {
                if (!formatValidator.validate(actual)) {
                    ctx.addError(JsonPointer.fromLast(ps), "format",
                            "Value '" + actual + "' does not match format " + format);
                    return false;
                }
            }
            return true;
        }
    }

    // minProperties / maxProperties
    class ObjectEvaluator implements Evaluator {
        private final Integer minProperties;
        private final Integer maxProperties;
        public ObjectEvaluator(Integer minProperties, Integer maxProperties) {
            this.minProperties = minProperties;
            this.maxProperties = maxProperties;
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;

            Object actual = instance.getNode();
            int size = Nodes.sizeInObject(actual);
            if (minProperties != null && size < minProperties) {
                ctx.addError(JsonPointer.fromLast(ps), "minProperties",
                        "Object must have >= " + minProperties + " properties");
                return false;
            }
            if (maxProperties != null && size > maxProperties) {
                ctx.addError(JsonPointer.fromLast(ps), "maxProperties",
                        "Object must have <= " + maxProperties + " properties");
                return false;
            }
            return true;
        }
    }

    // properties / patternProperties / additionalProperties
    class PropertiesEvaluator implements Evaluator {
        private final Map<String, Object> properties;
        private final Pattern[] patternPns;
        private final Object[] patternSchemas;
        private final Object additionalPropertiesSchema;
        public PropertiesEvaluator(Map<String, Object> properties,
                                   Map<String, Object> patternProperties,
                                   Object additionalPropertiesSchema) {
            this.properties = properties;
            if (patternProperties != null) {
                this.patternPns = new Pattern[patternProperties.size()];
                this.patternSchemas = new Object[patternProperties.size()];
                int i = 0;
                for (Map.Entry<String, Object> entry : patternProperties.entrySet()) {
                    this.patternPns[i] = Pattern.compile(entry.getKey());
                    this.patternSchemas[i] = entry.getValue();
                    i++;
                }
            } else {
                this.patternPns = null;
                this.patternSchemas = null;
            }
            this.additionalPropertiesSchema = additionalPropertiesSchema;
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;
            Object actual = instance.getNode();
            boolean result = true;

            int propIdx = 0;
            for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(actual)) {
                String key = entry.getKey();
                boolean matched = false;
                if (properties != null) {
                    Object subSchema = properties.get(key);
                    if (subSchema != null) {
                        boolean subResult = evaluateProperty(subSchema,
                                "properties", instance, ps, key, ctx);
                        if (subResult) instance.markEvaluated(propIdx);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return result;
                        matched = true;
                    }
                }

                if (patternPns != null) {
                    for (int i = 0; i < patternPns.length; i++) {
                        if (patternPns[i].matcher(key).find()) {
                            Object subSchema = patternSchemas[i];
                            if (subSchema != null) {
                                boolean subResult = evaluateProperty(subSchema,
                                        "patternProperties", instance, ps, key, ctx);
                                if (subResult) instance.markEvaluated(propIdx);
                                result = result && subResult;
                                if (ctx.shouldAbort()) return result;
                                matched = true;
                            }
                        }
                    }
                }

                if (additionalPropertiesSchema != null && !matched) {
                    boolean subResult = evaluateProperty(additionalPropertiesSchema,
                            "additionalProperties", instance, ps, key, ctx);
                    if (subResult) instance.markEvaluated(propIdx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
                propIdx++;
            }
            return result;
        }
    }

    // required / dependentRequired
    class RequiredEvaluator implements Evaluator {
        private final String[] required;
        private final Map<String, String[]> dependentRequired;
        public RequiredEvaluator(String[] required, Map<String, String[]> dependentRequired) {
            this.required = required;
            this.dependentRequired = dependentRequired;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;
            Object actual = instance.getNode();
            boolean result = true;

            if (required != null) {
                for (String key : required) {
                    if (!Nodes.containsInObject(actual, key)) {
                        ctx.addError(JsonPointer.fromLast(ps), "required", "Missing required property '" + key + "'");
                        result = false;
                    }
                }
                if (ctx.shouldAbort()) return result;
            }

            if (dependentRequired != null) {
                for (Map.Entry<String, String[]> entry : dependentRequired.entrySet()) {
                    String key = entry.getKey();
                    if (Nodes.containsInObject(actual, key)) {
                        String[] required = entry.getValue();
                        for (String property : required) {
                            if (!Nodes.containsInObject(actual, property)) {
                                ctx.addError(JsonPointer.fromLast(ps), "dependentRequired", "Property '" + property +
                                        "' is required when property '" + key + "' is present");
                                result = false;
                            }
                        }
                        if (ctx.shouldAbort()) return result;
                    }
                }
            }
            return result;
        }
    }

    // dependentSchemas
    class DependentSchemasEvaluator implements Evaluator {
        private final Map<String, Object> dependentSchemas;
        public DependentSchemasEvaluator(Map<String, Object> dependentSchemas) {
            this.dependentSchemas = dependentSchemas;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;

            Object actual = instance.getNode();
            boolean result = true;
            for (Map.Entry<String, Object> entry : dependentSchemas.entrySet()) {
                String key = entry.getKey();
                if (Nodes.containsInObject(actual, key)) {
                    Object subSchema = dependentSchemas.get(key);
                    boolean subResult = evaluate(subSchema, "dependentSchemas", instance, ps, ctx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
            }
            return result;
        }
    }

    // propertyNames
    class PropertyNamesEvaluator implements Evaluator {
        private final Object propertyNamesSchema;
        public PropertyNamesEvaluator(Object propertyNamesSchema) {
            this.propertyNamesSchema = propertyNamesSchema;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;

            Object actual = instance.getNode();
            boolean result = true;
            for (String key : Nodes.keySetInObject(actual)) {
                ctx.pushIgnoreError();
                boolean probed = evaluate(propertyNamesSchema, "propertyNames", InstancedNode.infer(key), ps, ctx);
                ctx.popIgnoreError();
                if (!probed) {
                    ctx.addError(JsonPointer.fromLast(ps), "propertyNames",
                            "Property name '" + key + "' is invalid");
                    result = false;
                    if (ctx.shouldAbort()) return result;
                }
            }
            return result;
        }
    }

    // minItems / maxItems / uniqueItems
    class ArrayEvaluator implements Evaluator {
        private final Integer minItems;
        private final Integer maxItems;
        private final Boolean uniqueItems;
        public ArrayEvaluator(Integer minItems, Integer maxItems, Boolean uniqueItems) {
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.uniqueItems = uniqueItems;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return true;

            Object actual = instance.getNode();
            boolean result = true;
            int size = Nodes.sizeInArray(actual);
            if (minItems != null && size < minItems) {
                ctx.addError(JsonPointer.fromLast(ps), "minItems", "Array size must >= " + minItems);
                result = false;
            }
            if (maxItems != null && size > maxItems) {
                ctx.addError(JsonPointer.fromLast(ps), "maxItems", "Array size must <= " + maxItems);
                result = false;
            }
            if (Boolean.TRUE.equals(uniqueItems)) {
                Set<Object> set = new HashSet<>();
                Nodes.visitArray(actual, (i, v) -> set.add(v));
                if (set.size() != size) {
                    ctx.addError(JsonPointer.fromLast(ps), "uniqueItems", "Array items must be unique");
                    result = false;
                }
            }
            return result;
        }
    }

    // items / prefixItems
    class ItemsEvaluator implements Evaluator {
        private final Object itemsSchema;
        private final Object[] prefixItemsSchemas;
        public ItemsEvaluator(Object itemsSchema, Object[] prefixItemsSchemas) {
            this.itemsSchema = itemsSchema;
            this.prefixItemsSchemas = prefixItemsSchemas;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return true;

            Object actual = instance.getNode();
            boolean result = true;
            int size = Nodes.sizeInArray(actual);
            int i = 0;
            if (prefixItemsSchemas != null) {
                for (; i < size && i < prefixItemsSchemas.length; i++) {
                    boolean subResult = evaluateItem(prefixItemsSchemas[i], "prefixItems", instance, ps, i, ctx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
                if (result) instance.markEvaluated(0, i);
            }
            if (itemsSchema != null) {
                for (; i < size; i++) {
                    boolean subResult = evaluateItem(itemsSchema, "items", instance, ps, i, ctx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
                if (result) instance.markEvaluated(0, i);
            }
            return result;
        }
    }


    // contains / minContains / maxContains
    class ContainsEvaluator implements Evaluator {
        private final Object containsSchema;
        private final Integer minContains;
        private final Integer maxContains;
        public ContainsEvaluator(Object containsSchema, Integer minContains, Integer maxContains) {
            this.containsSchema = containsSchema;
            this.minContains = minContains == null ? 1 : minContains;
            this.maxContains = maxContains;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return true;
            if (containsSchema == null) return true;

            Object actual = instance.getNode();
            int matches = 0;
            int size = Nodes.sizeInArray(actual);
            for (int i = 0; i < size; i++) {
                ctx.pushIgnoreError();
                boolean result = evaluateItem(containsSchema, "contains", instance, ps, i, ctx);
                ctx.popIgnoreError();
                if (result) {
                    instance.markEvaluated(i);
                    matches++;
                }
            }
            if (matches < minContains) {
                ctx.addError(JsonPointer.fromLast(ps), "minContains", "Array must contain at least " +
                        minContains + " matching items, but found " + matches);
                return false;
            }
            if (maxContains != null && matches > maxContains) {
                ctx.addError(JsonPointer.fromLast(ps), "maxContains", "Array must contain no more than " +
                        maxContains + " matching items, but found " + matches);
                return false;
            }
            return true;
        }
    }

    // if / then / else
    class IfThenElseEvaluator implements Evaluator {
        private final Object ifSchema;
        private final Object thenSchema;
        private final Object elseSchema;
        public IfThenElseEvaluator(Object ifSchema, Object thenSchema, Object elseSchema) {
            this.ifSchema = ifSchema;
            this.thenSchema = thenSchema;
            this.elseSchema = elseSchema;
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (ifSchema == null) return true;

            boolean result = true;
            BitSet cousinEvaluated = instance.popEvaluated();
            instance.pushEvaluated();
            ctx.pushIgnoreError();
            boolean tested = evaluate(ifSchema, "if", instance, ps, ctx);
            ctx.popIgnoreError();
            if (tested) {
                if (thenSchema != null) {
                    result = evaluate(thenSchema, "then", instance, ps, ctx);
                }
                BitSet ifThenEvaluated = instance.popEvaluated();
                if (result && cousinEvaluated != null && ifThenEvaluated != null)
                    cousinEvaluated.or(ifThenEvaluated);
            } else {
                BitSet droppedEvaluated = instance.popEvaluated();
                if (elseSchema != null) {
                    instance.pushEvaluated();
                    result = evaluate(elseSchema, "else", instance, ps, ctx);
                    BitSet elseEvaluated = instance.popEvaluated();
                    if (result && cousinEvaluated != null && elseEvaluated != null)
                        cousinEvaluated.or(elseEvaluated);
                }
            }
            if (cousinEvaluated != null) instance.pushEvaluated(cousinEvaluated);
            return result;
        }
    }

    // allOf
    class AllOfEvaluator implements Evaluator {
        private final Object[] allOfSchemas;
        public AllOfEvaluator(Object[] allOfSchemas) {
            this.allOfSchemas = allOfSchemas;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            boolean result = true;
            BitSet[] evaluatedArr = null;
            BitSet cousinEvaluated = instance.popEvaluated();
            if (cousinEvaluated != null) {
                evaluatedArr = new BitSet[allOfSchemas.length];
            }
            for (int i = 0; i < allOfSchemas.length; i++) {
                Object schema = allOfSchemas[i];
                instance.pushEvaluated();
                boolean subResult = evaluate(schema, "allOf", instance, ps, ctx);
                BitSet childEvaluated = instance.popEvaluated();
                if (subResult && evaluatedArr != null && childEvaluated != null) evaluatedArr[i] = childEvaluated;
                result = result && subResult;
                if (ctx.shouldAbort()) return result;
            }
            if (cousinEvaluated != null)
                instance.pushEvaluated(cousinEvaluated);
            if (result && evaluatedArr != null) {
                for (BitSet evaluated : evaluatedArr) {
                    if (evaluated != null) cousinEvaluated.or(evaluated);
                }
            }
            return result;
        }
    }

    // anyOf
    class AnyOfEvaluator implements Evaluator {
        private final Object[] anyOfSchemas;
        public AnyOfEvaluator(Object[] anyOfSchemas) {
            this.anyOfSchemas = anyOfSchemas;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            boolean result = false;
            BitSet[] evaluatedArr = null;
            BitSet cousinEvaluated = instance.popEvaluated();
            if (cousinEvaluated != null) {
                evaluatedArr = new BitSet[anyOfSchemas.length];
            }
            for (int i = 0; i < anyOfSchemas.length; i++) {
                Object schema = anyOfSchemas[i];
                instance.pushEvaluated();
                ctx.pushIgnoreError();
                boolean subResult = evaluate(schema, "anyOf", instance, ps, ctx);
                ctx.popIgnoreError();
                BitSet childEvaluated = instance.popEvaluated();
                if (subResult && evaluatedArr != null && childEvaluated != null) {
                    evaluatedArr[i] = childEvaluated;
                }
                result = result || subResult;
            }
            if (cousinEvaluated != null)
                instance.pushEvaluated(cousinEvaluated);
            if (result && evaluatedArr != null) {
                for (BitSet evaluated : evaluatedArr) {
                    if (evaluated != null) cousinEvaluated.or(evaluated);
                }
            }
            if (!result) {
                ctx.addError(JsonPointer.fromLast(ps), "anyOf", "No schemas matched");
            }
            return result;
        }
    }

    // oneOf
    class OneOfEvaluator implements Evaluator {
        private final Object[] oneOfSchemas;
        public OneOfEvaluator(Object[] oneOfSchemas) {
            this.oneOfSchemas = oneOfSchemas;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            int matches = 0;
            BitSet[] evaluatedArr = null;
            BitSet cousinEvaluated = instance.popEvaluated();
            if (cousinEvaluated != null) {
                evaluatedArr = new BitSet[oneOfSchemas.length];
            }
            for (int i = 0; i < oneOfSchemas.length; i++) {
                Object schema = oneOfSchemas[i];
                instance.pushEvaluated();
                ctx.pushIgnoreError();
                boolean subResult = evaluate(schema, "oneOf", instance, ps, ctx);
                ctx.popIgnoreError();
                BitSet childEvaluated = instance.popEvaluated();
                if (subResult && evaluatedArr != null && childEvaluated != null) evaluatedArr[i] = childEvaluated;
                if (subResult) matches++;
            }
            if (cousinEvaluated != null)
                instance.pushEvaluated(cousinEvaluated);
            if (matches == 1 && evaluatedArr != null) {
                for (BitSet evaluated : evaluatedArr) {
                    if (evaluated != null) cousinEvaluated.or(evaluated);
                }
            }
            if (matches != 1) {
                ctx.addError(JsonPointer.fromLast(ps), "oneOf",
                        "Must match exactly 1 schema, but found " + matches);
                return false;
            }
            return true;
        }
    }

    // not
    class NotEvaluator implements Evaluator {
        private final Object notSchema;
        public NotEvaluator(Object notSchema) {
            this.notSchema = notSchema;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            instance.pushEvaluated();
            ctx.pushIgnoreError();
            boolean result = evaluate(notSchema, "not", instance, ps, ctx);
            ctx.popIgnoreError();
            instance.popEvaluated();

            if (result) {
                ctx.addError(JsonPointer.fromLast(ps), "not", "Must not match schema in not");
                return false;
            }
            return true;
        }
    }

    // unevaluatedProperties / unevaluatedItems
    class UnevaluatedEvaluator implements Evaluator {
        private final Object unevaluatedPropertiesSchema;
        private final Object unevaluatedItemsSchema;
        public UnevaluatedEvaluator(Object unevaluatedPropertiesSchema,
                                    Object unevaluatedItemsSchema) {
            this.unevaluatedPropertiesSchema = unevaluatedPropertiesSchema;
            this.unevaluatedItemsSchema = unevaluatedItemsSchema;
        }
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            boolean result = true;
            BitSet merged = instance.mergedEvaluated();
            if (unevaluatedPropertiesSchema != null) {
                if (instance.getJsonType() != JsonType.OBJECT) return true;
                int propIdx = 0;
                for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(instance.getNode())) {
                    String key = entry.getKey();
                    if (!merged.get(propIdx)) {
                        boolean subResult = evaluateProperty(unevaluatedPropertiesSchema,
                                "unevaluatedProperties", instance, ps, key, ctx);
                        if (subResult) instance.markEvaluated(propIdx);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return result;
                    }
                    propIdx++;
                }
            }
            if (unevaluatedItemsSchema != null) {
                if (instance.getJsonType() != JsonType.ARRAY) return true;
                int size = Nodes.sizeInArray(instance.getNode());
                for (int i = 0; i < size; i++) {
                    if (!merged.get(i)) {
                        boolean subResult = evaluateItem(unevaluatedItemsSchema,
                                "unevaluatedItems", instance, ps, i, ctx);
                        if (subResult) instance.markEvaluated(i);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return result;
                    }
                }
            }
            BitSet curEvaluated = instance.popEvaluated();
            BitSet parentEvaluated = instance.peekEvaluated();
            if (parentEvaluated != null && curEvaluated != null) parentEvaluated.or(curEvaluated);
            return result;
        }
    }

}
