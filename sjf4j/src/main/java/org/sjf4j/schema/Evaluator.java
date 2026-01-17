package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathToken;
import org.sjf4j.util.NodeUtil;
import org.sjf4j.util.NumberUtil;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public interface Evaluator {

    boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx);

    default boolean probeToEvaluate(Object subSchema, InstancedNode instance,
                                    JsonPointer path, ValidationContext ctx) {
        ValidationContext probeCtx = ctx.createProbe();
        evaluate(subSchema, instance, path, probeCtx);
        return probeCtx.isValid();
    }

    default boolean evaluate(Object subSchema, InstancedNode instance,
                             JsonPointer path, ValidationContext ctx) {
        if (subSchema == null) {
            ctx.addError(path.toExpr(), "schema", "Not found schema");
            return false;
        }
        if (subSchema instanceof Boolean) {
            if (!(Boolean) subSchema) {
                ctx.addError(path.toExpr(), "schema", "False schema always fails");
                return false;
            } else {
                return true;
            }
        }
        if (subSchema instanceof JsonSchema) {
            return ((JsonSchema) subSchema).validate(instance, path, ctx);
        }
        ctx.addError(path.toExpr(), "schema", "Not a valid schema type " + subSchema.getClass().getName());
        return false;
    }

    default boolean evaluateProperty(Object subSchema, InstancedNode instance,
                                     JsonPointer path, String key, ValidationContext ctx) {
        InstancedNode subInstance = instance.getSubByKey(key);
        path.push(new PathToken.Name(key));
        boolean result = evaluate(subSchema, subInstance, path, ctx);
        path.pop();
        if (!ctx.isProbe() && result) instance.addEvaluatedProperty(key);
        return result;
    }

    default boolean evaluateItem(Object subSchema, InstancedNode instance,
                                 JsonPointer path, int idx, ValidationContext ctx) {
        InstancedNode subInstance = instance.getSubByIndex(idx);
        path.push(new PathToken.Index(idx));
        boolean result = evaluate(subSchema, subInstance, path, ctx);
        path.pop();
        if (!ctx.isProbe() && result) instance.setEvaluatedItems(idx + 1);
        return result;
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (refPath != null) {
                Object schema = ctx.getSchemaByPath(uri, refPath);
                if (schema != null) {
                    if (schema instanceof Boolean) {
                        return evaluate(schema, instance, path, ctx);
                    } else if (schema instanceof JsonSchema) {
                        if (instance.isRecursiveRef(schema)) return true;
                        return evaluate(schema, instance, path, ctx);
                    } else {
                        ctx.addError(path.toExpr(), "$ref",
                                "Not a valid schema by path '" + refPath + "' in " + uri);
                        return false;
                    }
                }
                ctx.addWarn(path.toExpr(), "$ref",
                        "Not found schema by path '" + refPath + "' in " + uri);
            }
            if (anchor != null) { // Always true
                JsonSchema schema = ctx.getSchemaByAnchor(uri, anchor);
                if (schema == null) {
                    ctx.addError(path.toExpr(), "$ref",
                            "Not found anchor '" + anchor + "' in " + uri);
                    return false;
                }
                if (instance.isRecursiveRef(schema)) return true;
                return evaluate(schema, instance, path, ctx);
            }
            throw new AssertionError(RefEvaluator.class);
        }
    }

    // $dynamicRef
    class DynamicRefEvaluator implements Evaluator {
        private final String dynamicAnchor;
        public DynamicRefEvaluator(String dynamicAnchor) {
            this.dynamicAnchor = dynamicAnchor;
        }
        @Override
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            JsonSchema schema = ctx.getSchemaByDynamicAnchor(dynamicAnchor);
            if (schema == null) {
                schema = ctx.getSchemaByAnchor(null, dynamicAnchor);
                if (schema == null) {
                    ctx.addError(path.toExpr(), "$dynamicRef",
                            "Not found schema by $dynamicRef '" + dynamicAnchor + "'");
                    return false;
                }
            }
            if (instance.isRecursiveRef(schema)) return true;
            return evaluate(schema, instance, path, ctx);
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
            NodeType nt = NodeType.of(type);
            if (nt.isString()) {
                this.type = (String) type;
                this.jsonType = JsonType.from(this.type);
                this.types = null;
                this.jsonTypes = null;
            } else if (nt.isArray()) {
                this.type = null;
                this.jsonType = null;
                this.types = NodeUtil.asArray(type, String.class);
                this.jsonTypes = (JsonType[]) Array.newInstance(JsonType.class, types.length);
                for (int i = 0; i < types.length; i++) this.jsonTypes[i] = JsonType.from(types[i]);
            } else {
                throw new IllegalArgumentException("TypeEvaluator only supports String or Array, but found: " +
                        type.getClass().getSimpleName());
            }
        }
        @Override
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (jsonType != null) {
                if (!matches(jsonType, instance)) {
                    ctx.addError(path.toExpr(), "type", "Expected type " + type +
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
                ctx.addError(path.toExpr(), "type", "Expected one of " + Arrays.toString(types) +
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
                    return actual instanceof Number && NumberUtil.isSemanticInteger((Number) actual);
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            Object actual = instance.getNode();
            if (!NodeUtil.equals(constValue, actual)) {
                ctx.addError(path.toExpr(), "const", "Value does not match constant. Expected: " +
                        NodeUtil.inspect(constValue) + ", actual: " + NodeUtil.inspect(actual));
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            Object actual = instance.getNode();
            for (Object allowed : enumValues) {
                if (NodeUtil.equals(allowed, actual)) return true;
            }
            ctx.addError(path.toExpr(), "enum", "Value not in enum: " + NodeUtil.inspect(enumValues));
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.NUMBER && instance.getJsonType() != JsonType.INTEGER) {
                return false;
            }
            Number actual = (Number) instance.getNode();
            if (minimum != null && NumberUtil.compare(actual, minimum) < 0) {
                ctx.addError(path.toExpr(), "minimum", "Number must >= " + minimum);
                return false;
            } else if (maximum != null && NumberUtil.compare(actual, maximum) > 0) {
                ctx.addError(path.toExpr(), "maximum", "Number must <= " + maximum);
                return false;
            } else if (exclusiveMinimum != null && NumberUtil.compare(actual, exclusiveMinimum) <= 0) {
                ctx.addError(path.toExpr(), "exclusiveMinimum", "Number must > " + exclusiveMinimum);
                return false;
            } else if (exclusiveMaximum != null && NumberUtil.compare(actual, exclusiveMaximum) >= 0) {
                ctx.addError(path.toExpr(), "exclusiveMaximum", "Number must < " + exclusiveMaximum);
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
            this.divisor = NumberUtil.normalizeDecimal(multipleOf);
            if (divisor.signum() <= 0)
                throw new IllegalArgumentException("multipleOf must > 0");
            this.isIntegerDivisor = divisor.scale() <= 0;
            this.divisorLong = isIntegerDivisor ? divisor.longValueExact() : 0L;
            this.divisorDouble = divisor.doubleValue();
        }

        @Override
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.NUMBER && instance.getJsonType() != JsonType.INTEGER) {
                return false;
            }
            Number actual = (Number) instance.getNode();
            if (isIntegerDivisor && NumberUtil.isSemanticInteger(actual)) {
                long v = actual.longValue();
                if (v % divisorLong != 0) {
                    ctx.addError(path.toExpr(),"multipleOf", "Number not a multiple of " + multipleOf);
                    return false;
                }
            } else if (isIntegerDivisor && (actual instanceof Double || actual instanceof Float)) {
                double dv = actual.doubleValue();
                double q = dv / divisorDouble;
                if (q != Math.rint(q)) {
                    ctx.addError(path.toExpr(),"multipleOf", "Number not a multiple of " + multipleOf);
                    return false;
                }
            } else {
                BigDecimal v = NumberUtil.normalizeDecimal(actual);
                BigDecimal[] dr = v.divideAndRemainder(divisor);
                if (dr[1].signum() != 0) {
                    ctx.addError(path.toExpr(), "multipleOf", "Number not a multiple of " + multipleOf);
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return false;
            String actual = (String) instance.getNode();
            int length = actual.length();
            if (minLength != null && length < minLength) {
                ctx.addError(path.toExpr(), "minLength", "String length must >= " + minLength);
                return false;
            }
            if (maxLength != null && length > maxLength) {
                ctx.addError(path.toExpr(), "maxLength", "String length must <= " + maxLength);
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return false;
            String actual = (String) instance.getNode();
            if (!pn.matcher(actual).find()) {
                ctx.addError(path.toExpr(), "pattern", "String must match pattern: " + pattern);
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return false;
            String actual = (String) instance.getNode();
            if (!formatValidator.validate(actual)) {
                ctx.addError(path.toExpr(), "format", "String must match format: " + format);
                return false;
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return false;

            Object actual = instance.getNode();
            int size = NodeWalker.sizeInObject(actual);
            if (minProperties != null && size < minProperties) {
                ctx.addError(path.toExpr(), "minProperties",
                        "Object must have >= " + minProperties + " properties");
                return false;
            }
            if (maxProperties != null && size > maxProperties) {
                ctx.addError(path.toExpr(), "maxProperties",
                        "Object must have <= " + maxProperties + " properties");
                return false;
            }
            return true;
        }
    }

    // required / properties / patternProperties / additionalProperties
    class PropertiesEvaluator implements Evaluator {
        private final String[] required;
        private final Map<String, Object> properties;
        private final Pattern[] patternPns;
        private final Object[] patternSchemas;
        private final Object additionalPropertiesSchema;
        public PropertiesEvaluator(String[] required,
                                   Map<String, Object> properties,
                                   Map<String, Object> patternProperties,
                                   Object additionalPropertiesSchema) {
            this.required = required;
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return false;

            Object actual = instance.getNode();
            boolean result = true;
            if (required != null) {
                for (String key : required) {
                    if (!NodeWalker.containsInObject(actual, key)) {
                        ctx.addError(path.toExpr(), "required", "Missing required property '" + key + "'");
                        result = false;
                    }
                }
            }
            if (ctx.shouldAbort()) return result;

            // properties
            for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(actual)) {
                String key = entry.getKey();
                boolean matched = false;
                if (properties != null) {
                    Object subSchema = properties.get(key);
                    if (subSchema != null) {
                        result = result && evaluateProperty(subSchema, instance, path, key, ctx);
                        matched = true;
                    }
                }
                if (patternPns != null) {
                    for (int i = 0; i < patternPns.length; i++) {
                        if (patternPns[i].matcher(key).find()) {
                            Object subSchema = patternSchemas[i];
                            if (subSchema != null) {
                                result = result && evaluateProperty(subSchema, instance, path, key, ctx);
                                matched = true;
                            }
                        }
                    }
                }
                if (additionalPropertiesSchema != null && !matched) {
                    result = result && evaluateProperty(additionalPropertiesSchema, instance, path, key, ctx);
                }
                if (ctx.shouldAbort()) return result;
            }
            return result;
        }
    }

    // dependentRequired
    class DependentRequiredEvaluator implements Evaluator {
        private final Map<String, String[]> dependentRequired;
        public DependentRequiredEvaluator(Map<String, String[]> dependentRequired) {
            this.dependentRequired = dependentRequired;
        }
        @Override
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return false;

            Object actual = instance.getNode();
            boolean result = true;
            for (Map.Entry<String, String[]> entry : dependentRequired.entrySet()) {
                String key = entry.getKey();
                if (NodeWalker.containsInObject(actual, key)) {
                    String[] required = entry.getValue();
                    for (String property : required) {
                        if (!NodeWalker.containsInObject(actual, property)) {
                            ctx.addError(path.toExpr(), "dependentRequired", "Property '" + property +
                                    "' is required when property '" + key + "' is present");
                            result = false;
                        }
                    }
                    if (ctx.shouldAbort()) return result;
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return false;

            Object actual = instance.getNode();
            boolean result = true;
            for (Map.Entry<String, Object> entry : dependentSchemas.entrySet()) {
                String key = entry.getKey();
                if (NodeWalker.containsInObject(actual, key)) {
                    Object subSchema = dependentSchemas.get(key);
                    result = result && evaluate(subSchema, instance, path, ctx);
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return false;

            Object actual = instance.getNode();
            boolean result = true;
            for (String key : NodeWalker.keySetInObject(actual)) {
                if (!probeToEvaluate(propertyNamesSchema, InstancedNode.infer(key), path, ctx)) {
                    ctx.addError(path.toExpr(), "propertyNames",
                            "Property name '" + key + "' is invalid");
                    result = false;
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return false;

            Object actual = instance.getNode();
            boolean result = true;
            int size = NodeWalker.sizeInArray(actual);
            if (minItems != null && size < minItems) {
                ctx.addError(path.toExpr(), "minItems", "Array size must >= " + minItems);
                result = false;
            }
            if (maxItems != null && size > maxItems) {
                ctx.addError(path.toExpr(), "maxItems", "Array size must <= " + maxItems);
                result = false;
            }
            if (Boolean.TRUE.equals(uniqueItems)) {
                Set<Object> set = new HashSet<>();
                NodeWalker.visitArray(actual, (i, v) -> set.add(v));
                if (set.size() != size) {
                    ctx.addError(path.toExpr(), "uniqueItems", "Array items must be unique");
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return false;

            Object actual = instance.getNode();
            boolean result = true;
            int size = NodeWalker.sizeInArray(actual);
            for (int i = 0; i < size; i++) {
                if (prefixItemsSchemas != null && i < prefixItemsSchemas.length) {
                    result = result && evaluateItem(prefixItemsSchemas[i], instance, path, i, ctx);
                } else if (itemsSchema != null) {
                    result = result && evaluateItem(itemsSchema, instance, path, i, ctx);
                }
                if (ctx.shouldAbort()) return result;
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return false;
            if (containsSchema == null) return true;

            Object actual = instance.getNode();
            int matches = 0;
            int size = NodeWalker.sizeInArray(actual);
            for (int i = 0; i < size; i++) {
//                if (evaluateItem(containsSchema, instance, path, i, ctx)) matches++;
                InstancedNode subInstance = instance.getSubByIndex(i);
                path.push(new PathToken.Index(i));
                boolean result = probeToEvaluate(containsSchema, subInstance, path, ctx);
                path.pop();
                if (result) {
                    instance.setEvaluatedItems(i + 1);
                    matches++;
                }
            }
            if (matches < minContains) {
                ctx.addError(path.toExpr(), "minContains", "Array must contain at least " +
                        minContains + " matching items, but found " + matches);
                return false;
            }
            if (maxContains != null && matches > maxContains) {
                ctx.addError(path.toExpr(), "maxContains", "Array must contain no more than " +
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (ifSchema == null) return true;

            if (probeToEvaluate(ifSchema, instance, path, ctx)) {
                if (thenSchema != null) return evaluate(thenSchema, instance, path, ctx);
            } else {
                if (elseSchema != null) return evaluate(elseSchema, instance, path, ctx);
            }
            return true;
        }
    }

    // allOf
    class AllOfEvaluator implements Evaluator {
        private final Object[] allOfSchemas;
        public AllOfEvaluator(Object[] allOfSchemas) {
            this.allOfSchemas = allOfSchemas;
        }
        @Override
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            boolean result = true;
            for (Object schema : allOfSchemas) {
                result = result && evaluate(schema, instance, path, ctx);
                if (ctx.shouldAbort()) return result;
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            for (Object schema : anyOfSchemas) {
                if (probeToEvaluate(schema, instance, path, ctx)) {
                    return true;
                }
            }
            ctx.addError(path.toExpr(), "anyOf", "No schemas in anyOf matched");
            return false;
        }
    }

    // oneOf
    class OneOfEvaluator implements Evaluator {
        private final Object[] oneOfSchemas;
        public OneOfEvaluator(Object[] oneOfSchemas) {
            this.oneOfSchemas = oneOfSchemas;
        }
        @Override
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            int matches = 0;
            for (Object schema : oneOfSchemas) {
                if (probeToEvaluate(schema, instance, path, ctx)) {
                    matches++;
                }
            }
            if (matches != 1) {
                ctx.addError(path.toExpr(), "oneOf", "Must match exactly one schema in oneOf");
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            if (probeToEvaluate(notSchema, instance, path, ctx)) {
                ctx.addError(path.toExpr(), "not", "Must not match schema in not");
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
        public boolean evaluate(InstancedNode instance, JsonPointer path, ValidationContext ctx) {
            boolean result = true;
            if (instance.getJsonType() == JsonType.OBJECT && unevaluatedPropertiesSchema != null) {
                Set<String> evaluated = instance.getEvaluatedProperties();
                for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(instance.getNode())) {
                    String key = entry.getKey();
                    if (evaluated == null || !evaluated.contains(key)) {
                        result = result && evaluateProperty(unevaluatedPropertiesSchema, instance, path, key, ctx);
                        if (ctx.shouldAbort()) return result;
                    }
                }
            }
            if (instance.getJsonType() == JsonType.ARRAY && unevaluatedItemsSchema != null) {
                int evaluated = instance.getEvaluatedItems();
                int size = NodeWalker.sizeInArray(instance.getNode());
                for (int i = evaluated; i < size; i++) {
                    result = result && evaluateItem(unevaluatedItemsSchema, instance, path, i, ctx);
                    if (ctx.shouldAbort()) return result;
                }
            }
            return result;
        }
    }

}
