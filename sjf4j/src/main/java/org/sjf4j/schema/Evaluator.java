package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.exception.SchemaException;
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
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Per-keyword evaluator used by compiled schemas.
 * <p>
 * Implementations validate one keyword (or tightly related keyword group)
 * against current instance node and report messages via context.
 */
public interface Evaluator {

    /**
     * Evaluates a keyword against the given instance.
     *
     * @return true when keyword validation succeeds for current instance branch
     */
    boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx);

//    default boolean evaluate(JsonSchema subSchema, String keyword, InstancedNode instance,
//                             PathSegment ps, ValidationContext ctx) {
//        if (subSchema == null) {
//            ctx.addError(ps, keyword, "Not found schema");
//            return false;
//        }
//        if (subSchema instanceof Boolean) {
//            if (!(Boolean) subSchema) {
//                ctx.addError(ps, keyword, "Schema 'false' always fails");
//                return false;
//            } else {
//                return true;
//            }
//        }
//        if (subSchema instanceof ObjectSchema) {
//            return ((ObjectSchema) subSchema).evaluate(instance, ps, ctx);
//        }
//        ctx.addError(ps, keyword, "Not a valid schema type " + subSchema.getClass().getName());
//        return false;
//    }
//
//    default boolean evaluateProperty(JsonSchema subSchema, String keyword, InstancedNode instance,
//                                     PathSegment ps, String key, ValidationContext ctx) {
//        InstancedNode subInstance = instance.getSubByKey(key);
//        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, instance.getObjectType(), key);
//        return subSchema.evaluate(subInstance, cps, ctx);
//    }
//
//    default boolean evaluateItem(JsonSchema subSchema, String keyword, InstancedNode instance,
//                                 PathSegment ps, int idx, ValidationContext ctx) {
//        InstancedNode subInstance = instance.getSubByIndex(idx);
//        PathSegment cps = ps == null ? null : new PathSegment.Index(ps, instance.getObjectType(), idx);
//        return subSchema.evaluate(subInstance, cps, ctx);
//    }


    /// Built-in evaluators

    // $ref
    final class RefEvaluator implements Evaluator {
        private final URI uri;
        private final JsonPointer refPath;
        private final String anchor;
        /**
         * Creates evaluator for $ref target.
         */
        public RefEvaluator(URI uri, JsonPointer refPath, String anchor) {
            this.uri = uri;
            this.refPath = refPath;
            this.anchor = anchor;
        }
        /**
         * Resolves $ref and delegates evaluation to the referenced schema.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (refPath != null) {
                JsonSchema schema = ctx.getSchemaByPath(uri, refPath);
                if (schema != null) {
                    return schema.evaluate(instance, ps, ctx);
                }
                ctx.addWarn(ps, "$ref", "Not found schema by path '" + refPath + "' in URI " + uri);
            }
            if (anchor != null) { // Always true
                ObjectSchema schema = ctx.getSchemaByAnchor(uri, anchor);
                if (schema == null) {
                    if (anchor.isEmpty()) {
                        ctx.addError(ps, "$ref", "Not found schema at URI " + uri);
                    } else {
                        ctx.addError(ps, "$ref", "Not found anchor '" + anchor + "' in URI " + uri);
                    }
                    return false;
                }
                if (instance.isRecursiveRef(schema)) return true;
                return schema.evaluate(instance, ps, ctx);
            }
            throw new AssertionError(RefEvaluator.class);
        }
    }

    // $dynamicRef
    final class DynamicRefEvaluator implements Evaluator {
        private final URI uri;
        private final JsonPointer refPath;
        private final String dynamicAnchor;
        /**
         * Creates evaluator for $dynamicRef target.
         */
        public DynamicRefEvaluator(URI uri, JsonPointer refPath, String dynamicAnchor) {
            this.uri = uri;
            this.refPath = refPath;
            this.dynamicAnchor = dynamicAnchor;
        }

        /**
         * Resolves $dynamicRef using dynamic anchors or fallback path.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            JsonSchema schema = ctx.getSchemaByDynamicAnchor(uri, dynamicAnchor);
            if (schema == null) {
                if (refPath != null) {
                    schema = ctx.getSchemaByPath(uri, refPath);
                    if (schema != null) {
                        return schema.evaluate(instance, ps, ctx);
                    }
                    ctx.addWarn(ps, "$dynamicRef",
                            "Not found schema by path '" + refPath + "' in URI " + uri);
                }
                schema = ctx.getSchemaByAnchor(uri, dynamicAnchor);
                if (schema == null) {
                    if (dynamicAnchor.isEmpty()) {
                        ctx.addError(ps, "$dynamicRef", "Not found schema at URI " + uri);
                        return false;
                    } else {
                        ctx.addError(ps, "$dynamicRef",
                                "Not found anchor '" + dynamicAnchor + "' in URI " + uri);
                        return false;
                    }
                }
            }
            if (instance.isRecursiveRef(schema)) return true;
            return schema.evaluate(instance, ps, ctx);
        }
    }

    // type
    final class TypeEvaluator implements Evaluator {
        private final String type;
        private final JsonType jsonType;
        private final String[] types;
        private final JsonType[] jsonTypes;

        /**
         * Creates evaluator for type keyword value.
         */
        public TypeEvaluator(Object type) {
            Objects.requireNonNull(type, "type is null");
            JsonType jt = JsonType.of(type);
            if (jt.isString()) {
                this.type = Nodes.toString(type);
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
                throw new SchemaException("Invalid 'type' keyword: expected String or Array, but found " +
                        type.getClass().getSimpleName());
            }
        }
        /**
         * Validates instance type against a single or union type.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (jsonType != null) {
                if (!matches(jsonType, instance)) {
                    ctx.addError(ps, "type", "Expected type " + type +
                            ", but was " + instance.getJsonType());
                    return false;
                }
                return true;
            }
            if (jsonTypes != null) {
                for (JsonType expected : jsonTypes) {
                    if (matches(expected, instance)) {
                        return true;
                    }
                }
                ctx.addError(ps, "type", "Expected one of " + Arrays.toString(types) +
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
                    return Numbers.isSemanticInteger(number);
                }
                return false;
            }
            return true;
        }
    }

    // const
    final class ConstEvaluator implements Evaluator {
        private final Object constValue;
        /**
         * Creates evaluator for const keyword.
         */
        public ConstEvaluator(Object constValue) {
            this.constValue = constValue;
        }
        /**
         * Ensures the instance equals the const value using node semantics.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            Object actual = instance.getNode();
            if (!Nodes.equals(constValue, actual)) {
                ctx.addError(ps, "const", "Value does not match constant. Expected: " +
                        Nodes.inspect(constValue) + ", actual: " + Nodes.inspect(actual));
                return false;
            }
            return true;
        }
    }

    // enum
    final class EnumEvaluator implements Evaluator {
        private final Object[] enumValues;
        /**
         * Creates evaluator for enum keyword values.
         */
        public EnumEvaluator(Object[] enumValues) {
            this.enumValues = Objects.requireNonNull(enumValues);
        }
        /**
         * Checks whether the instance matches any value in the enum.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            Object actual = instance.getNode();
            for (Object allowed : enumValues) {
                if (Nodes.equals(allowed, actual)) return true;
            }
            ctx.addError(ps, "enum", "Value not in enum: " + Nodes.inspect(enumValues));
            return false;
        }
    }


    // minimum / maximum / exclusiveMinimum / exclusiveMaximum
    final class NumberEvaluator implements Evaluator {
        private final boolean hasMinimum;
        private final double minimum;
        private final boolean hasMaximum;
        private final double maximum;
        private final boolean hasExclusiveMinimum;
        private final double exclusiveMinimum;
        private final boolean hasExclusiveMaximum;
        private final double exclusiveMaximum;
        public NumberEvaluator(Number minimum, Number maximum,
                               Number exclusiveMinimum, Number exclusiveMaximum) {
            this.hasMinimum = minimum != null;
            this.minimum = minimum != null ? minimum.doubleValue() : 0;
            this.hasMaximum = maximum != null;
            this.maximum = maximum != null ? maximum.doubleValue() : 0;
            this.hasExclusiveMinimum = exclusiveMinimum != null;
            this.exclusiveMinimum = exclusiveMinimum != null ? exclusiveMinimum.doubleValue() : 0;
            this.hasExclusiveMaximum = exclusiveMaximum != null;
            this.exclusiveMaximum = exclusiveMaximum != null ? exclusiveMaximum.doubleValue() : 0;
        }

        /**
         * Enforces min/max and exclusive bounds for numbers.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.NUMBER && instance.getJsonType() != JsonType.INTEGER) {
                return true;
            }
            Number actual = Nodes.toNumber(instance.getNode());
            double actualDouble = actual.doubleValue();
            if (hasMinimum && actualDouble < minimum) {
                ctx.addError(ps, "minimum", "Number '" + actual + "' must >= " + minimum);
                return false;
            } else if (hasMaximum && actualDouble > maximum) {
                ctx.addError(ps, "maximum", "Number '" + actual + "' must <= " + maximum);
                return false;
            } else if (hasExclusiveMinimum && actualDouble <= exclusiveMinimum) {
                ctx.addError(ps, "exclusiveMinimum", "Number '" + actual + "' must > " + exclusiveMinimum);
                return false;
            } else if (hasExclusiveMaximum && actualDouble >= exclusiveMaximum) {
                ctx.addError(ps, "exclusiveMaximum", "Number '" + actual + "' must < " + exclusiveMaximum);
                return false;
            }
            return true;
        }
    }

    // multipleOf
    final class MultipleOfEvaluator implements Evaluator {
        private final Number multipleOf;
        private final BigDecimal divisor;
        private final boolean isIntegerDivisor;
        private final long divisorLong;
        private final double divisorDouble;
        /**
         * Creates evaluator for multipleOf divisor.
         */
        public MultipleOfEvaluator(Number multipleOf) {
            this.multipleOf = multipleOf;
            this.divisor = Numbers.normalizeDecimal(multipleOf);
            if (divisor.signum() <= 0)
                throw new SchemaException("Invalid 'multipleOf' keyword: value must be > 0");
            this.isIntegerDivisor = divisor.scale() <= 0;
            this.divisorLong = isIntegerDivisor ? divisor.longValueExact() : 0L;
            this.divisorDouble = divisor.doubleValue();
        }

        /**
         * Validates that a numeric instance is a multiple of the divisor.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.NUMBER && instance.getJsonType() != JsonType.INTEGER) {
                return true;
            }
            Number actual = Nodes.toNumber(instance.getNode());
            if (isIntegerDivisor && Numbers.isSemanticInteger(actual)) {
                long v = actual.longValue();
                if (v % divisorLong != 0) {
                    ctx.addError(ps,"multipleOf", "Number not a multiple of " + multipleOf);
                    return false;
                }
            } else if (isIntegerDivisor && (actual instanceof Double || actual instanceof Float)) {
                double dv = actual.doubleValue();
                double q = dv / divisorDouble;
                if (q != Math.rint(q)) {
                    ctx.addError(ps,"multipleOf", "Number not a multiple of " + multipleOf);
                    return false;
                }
            } else {
                BigDecimal v = Numbers.normalizeDecimal(actual);
                BigDecimal[] dr = v.divideAndRemainder(divisor);
                if (dr[1].signum() != 0) {
                    ctx.addError(ps, "multipleOf", "Number not a multiple of " + multipleOf);
                    return false;
                }
            }
            return true;
        }
    }

    // minLength / maxLength
    final class StringEvaluator implements Evaluator {
        private final Integer minLength;
        private final Integer maxLength;
        /**
         * Creates evaluator for string length constraints.
         */
        public StringEvaluator(Integer minLength, Integer maxLength) {
            this.minLength = minLength;
            this.maxLength = maxLength;
        }
        /**
         * Enforces minLength/maxLength for strings.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return true;

            String actual = Nodes.toString(instance.getNode());
            int length = EvaluateUtil.stringIcuLength(actual);
            if (minLength != null && length < minLength) {
                ctx.addError(ps, "minLength", "String length must >= " + minLength);
                return false;
            }
            if (maxLength != null && length > maxLength) {
                ctx.addError(ps, "maxLength", "String length must <= " + maxLength);
                return false;
            }
            return true;
        }
    }

    // pattern
    final class PatternEvaluator implements Evaluator {
        private final String pattern;
        private final Pattern pn;
        /**
         * Creates evaluator for pattern keyword.
         */
        public PatternEvaluator(String pattern) {
            this.pattern = Objects.requireNonNull(pattern);
            this.pn = EvaluateUtil.compileRegexPattern(pattern, "pattern");
        }

        /**
         * Ensures a string matches the configured regex pattern.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return true;

            String actual = Nodes.toString(instance.getNode());
            if (!pn.matcher(actual).find()) {
                ctx.addError(ps, "pattern", "String must match pattern: " + pattern);
                return false;
            }
            return true;
        }
    }

    // format
    final class FormatEvaluator implements Evaluator {
        private final String format;
        private final FormatValidator formatValidator;
        /**
         * Creates evaluator for format keyword.
         */
        public FormatEvaluator(String format) {
            this.format = Objects.requireNonNull(format, "format is null");
            this.formatValidator = FormatValidator.of(format);
        }
        /**
         * Validates a string against the configured format in strict mode.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.STRING) return true;
            String actual = Nodes.toString(instance.getNode());
            if (ctx.getOptions().isStrictFormat()) {
                if (!formatValidator.validate(actual)) {
                    ctx.addError(ps, "format",
                            "Value '" + actual + "' does not match format " + format);
                    return false;
                }
            }
            return true;
        }
    }

    // minProperties / maxProperties
    final class ObjectEvaluator implements Evaluator {
        private final Integer minProperties;
        private final Integer maxProperties;
        /**
         * Creates evaluator for object size constraints.
         */
        public ObjectEvaluator(Integer minProperties, Integer maxProperties) {
            this.minProperties = minProperties;
            this.maxProperties = maxProperties;
        }

        /**
         * Enforces minProperties/maxProperties for objects.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;

            Object actual = instance.getNode();
            int size = Nodes.sizeInObject(actual);
            if (minProperties != null && size < minProperties) {
                ctx.addError(ps, "minProperties",
                        "Object must have >= " + minProperties + " properties");
                return false;
            }
            if (maxProperties != null && size > maxProperties) {
                ctx.addError(ps, "maxProperties",
                        "Object must have <= " + maxProperties + " properties");
                return false;
            }
            return true;
        }
    }

    // properties / patternProperties / additionalProperties
    final class PropertiesEvaluator implements Evaluator {
        private final Map<String, JsonSchema> properties;
        private final Pattern[] patternPns;
        private final JsonSchema[] patternSchemas;
        private final JsonSchema additionalPropertiesSchema;
        public PropertiesEvaluator(Map<String, JsonSchema> properties,
                                   Map<String, JsonSchema> patternProperties,
                                   JsonSchema additionalPropertiesSchema) {
            this.properties = properties;
            if (patternProperties != null) {
                this.patternPns = new Pattern[patternProperties.size()];
                this.patternSchemas = new JsonSchema[patternProperties.size()];
                int i = 0;
                for (Map.Entry<String, JsonSchema> entry : patternProperties.entrySet()) {
                    this.patternPns[i] = EvaluateUtil.compileRegexPattern(entry.getKey(), "patternProperties");
                    this.patternSchemas[i] = entry.getValue();
                    i++;
                }
            } else {
                this.patternPns = null;
                this.patternSchemas = null;
            }
            this.additionalPropertiesSchema = additionalPropertiesSchema;
        }

        /**
         * Validates properties, patternProperties, and additionalProperties.
         * <p>
         * Successful property validations mark indices as evaluated for later
         * unevaluatedProperties processing.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;

            Object actual = instance.getNode();
            boolean result = true;
            int propIdx = 0;
            for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(actual)) {
                String key = entry.getKey();
                Object value = entry.getValue();
                boolean matched = false;
                if (properties != null) {
                    JsonSchema subSchema = properties.get(key);
                    if (subSchema != null) {
                        InstancedNode subInstance = instance.inferSubByKey(key, value);
                        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, instance.getObjectType(), key);
                        boolean subResult = subSchema.evaluate(subInstance, cps, ctx);
                        if (subResult) instance.markEvaluated(propIdx);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return result;
                        matched = true;
                    }
                }

                if (patternPns != null) {
                    for (int i = 0; i < patternPns.length; i++) {
                        if (patternPns[i].matcher(key).find()) {
                            JsonSchema subSchema = patternSchemas[i];
                            if (subSchema != null) {
                                InstancedNode subInstance = instance.inferSubByKey(key, value);
                                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, instance.getObjectType(), key);
                                boolean subResult = subSchema.evaluate(subInstance, cps, ctx);
                                if (subResult) instance.markEvaluated(propIdx);
                                result = result && subResult;
                                if (ctx.shouldAbort()) return result;
                                matched = true;
                            }
                        }
                    }
                }

                if (additionalPropertiesSchema != null && !matched) {
                    InstancedNode subInstance = instance.inferSubByKey(key, value);
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, instance.getObjectType(), key);
                    boolean subResult = additionalPropertiesSchema.evaluate(subInstance, cps, ctx);
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
    final class RequiredEvaluator implements Evaluator {
        private final String[] required;
        private final Map<String, String[]> dependentRequired;
        /**
         * Creates evaluator for required/dependentRequired.
         */
        public RequiredEvaluator(String[] required, Map<String, String[]> dependentRequired) {
            this.required = required;
            this.dependentRequired = dependentRequired;
        }
        /**
         * Validates required and dependentRequired keys.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;

            Object actual = instance.getNode();
            boolean result = true;
            if (required != null) {
                for (String key : required) {
                    if (!Nodes.containsInObject(actual, key)) {
                        ctx.addError(ps, "required", "Missing required property '" + key + "'");
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
                                ctx.addError(ps, "dependentRequired", "Property '" + property +
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
    final class DependentSchemasEvaluator implements Evaluator {
        private final Map<String, JsonSchema> dependentSchemas;
        /**
         * Creates evaluator for dependentSchemas.
         */
        public DependentSchemasEvaluator(Map<String, JsonSchema> dependentSchemas) {
            this.dependentSchemas = dependentSchemas;
        }
        /**
         * Applies schemas when dependent properties are present.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;

            Object actual = instance.getNode();
            boolean result = true;
            for (Map.Entry<String, JsonSchema> entry : dependentSchemas.entrySet()) {
                String key = entry.getKey();
                if (Nodes.containsInObject(actual, key)) {
                    JsonSchema subSchema = dependentSchemas.get(key);
                    boolean subResult = subSchema.evaluate(instance, ps, ctx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
            }
            return result;
        }
    }

    // propertyNames
    final class PropertyNamesEvaluator implements Evaluator {
        private final JsonSchema propertyNamesSchema;
        /**
         * Creates evaluator for propertyNames schema.
         */
        public PropertyNamesEvaluator(JsonSchema propertyNamesSchema) {
            this.propertyNamesSchema = propertyNamesSchema;
        }
        /**
         * Validates each property name against propertyNames schema.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.OBJECT) return true;

            Object actual = instance.getNode();
            boolean result = true;
            for (String key : Nodes.keySetInObject(actual)) {
                ctx.pushIgnoreError();
                boolean probed = propertyNamesSchema.evaluate(InstancedNode.infer(key), ps, ctx);
                ctx.popIgnoreError();
                if (!probed) {
                    ctx.addError(ps, "propertyNames", "Property name '" + key + "' is invalid");
                    result = false;
                    if (ctx.shouldAbort()) return result;
                }
            }
            return result;
        }
    }

    // minItems / maxItems / uniqueItems
    final class ArrayEvaluator implements Evaluator {
        private final Integer minItems;
        private final Integer maxItems;
        private final Boolean uniqueItems;
        /**
         * Creates evaluator for array size/uniqueness constraints.
         */
        public ArrayEvaluator(Integer minItems, Integer maxItems, Boolean uniqueItems) {
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.uniqueItems = uniqueItems;
        }
        /**
         * Enforces minItems/maxItems/uniqueItems for arrays.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return true;

            Object actual = instance.getNode();
            boolean result = true;
            int size = Nodes.sizeInArray(actual);
            if (minItems != null && size < minItems) {
                ctx.addError(ps, "minItems", "Array size must >= " + minItems);
                result = false;
            }
            if (maxItems != null && size > maxItems) {
                ctx.addError(ps, "maxItems", "Array size must <= " + maxItems);
                result = false;
            }
            if (ctx.shouldAbort()) return result;
            if (Boolean.TRUE.equals(uniqueItems)) {
                Set<Object> set = new HashSet<>();
                for (Iterator<Object> it = Nodes.iteratorInArray(actual); it.hasNext(); ) {
                    Object v = it.next();
                    if (!set.add(v)) {
                        ctx.addError(ps, "uniqueItems", "Array items must be unique");
                        result = false;
                        break;
                    }

                }
            }
            return result;
        }
    }

    // items / prefixItems
    final class ItemsEvaluator implements Evaluator {
        private final JsonSchema itemsSchema;
        private final JsonSchema[] prefixItemsSchemas;
        /**
         * Creates evaluator for items/prefixItems schemas.
         */
        public ItemsEvaluator(JsonSchema itemsSchema, JsonSchema[] prefixItemsSchemas) {
            this.itemsSchema = itemsSchema;
            this.prefixItemsSchemas = prefixItemsSchemas;
        }
        /**
         * Validates prefixItems and items schemas for array elements.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return true;

            Object actual = instance.getNode();
            boolean result = true;
            int size = Nodes.sizeInArray(actual);
            int i = 0;
            if (prefixItemsSchemas != null) {
                for (; i < size && i < prefixItemsSchemas.length; i++) {
                    InstancedNode subInstance = instance.inferSubByIndex(i, Nodes.getInArray(actual, i));
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, instance.getObjectType(), i);
                    boolean subResult = prefixItemsSchemas[i].evaluate(subInstance, cps, ctx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
                if (result) instance.markEvaluated(0, i);
            }
            if (itemsSchema != null) {
                for (; i < size; i++) {
                    InstancedNode subInstance = instance.inferSubByIndex(i, Nodes.getInArray(actual, i));
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, instance.getObjectType(), i);
                    boolean subResult = itemsSchema.evaluate(subInstance, cps, ctx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
                if (result) instance.markEvaluated(0, i);
            }
            return result;
        }
    }


    // contains / minContains / maxContains
    final class ContainsEvaluator implements Evaluator {
        private final JsonSchema containsSchema;
        private final Integer minContains;
        private final Integer maxContains;
        /**
         * Creates evaluator for contains/minContains/maxContains.
         */
        public ContainsEvaluator(JsonSchema containsSchema, Integer minContains, Integer maxContains) {
            this.containsSchema = containsSchema;
            this.minContains = minContains == null ? 1 : minContains;
            this.maxContains = maxContains;
        }
        /**
         * Validates contains/minContains/maxContains for arrays.
         * <p>
         * Per-item contains failures are probed in ignore-error mode; only final
         * aggregate cardinality errors are reported.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.getJsonType() != JsonType.ARRAY) return true;
            if (containsSchema == null) return true;

            Object actual = instance.getNode();
            int matches = 0;
            Iterator<Object> it = Nodes.iteratorInArray(actual);
            for (int i = 0; it.hasNext(); i++) {
                Object subActual = it.next();
                ctx.pushIgnoreError();
                InstancedNode subInstance = instance.inferSubByIndex(i, subActual);
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, instance.getObjectType(), i);
                boolean result = containsSchema.evaluate(subInstance, cps, ctx);
                ctx.popIgnoreError();
                if (result) {
                    instance.markEvaluated(i);
                    matches++;
                }
            }
            if (matches < minContains) {
                ctx.addError(ps, "minContains", "Array must contain at least " +
                        minContains + " matching items, but found " + matches);
                return false;
            }
            if (maxContains != null && matches > maxContains) {
                ctx.addError(ps, "maxContains", "Array must contains at most " +
                        maxContains + " matching items, but found " + matches);
                return false;
            }
            return true;
        }
    }

    // if / then / else
    final class IfThenElseEvaluator implements Evaluator {
        private final JsonSchema ifSchema;
        private final JsonSchema thenSchema;
        private final JsonSchema elseSchema;
        /**
         * Creates evaluator for if/then/else keywords.
         */
        public IfThenElseEvaluator(JsonSchema ifSchema, JsonSchema thenSchema, JsonSchema elseSchema) {
            this.ifSchema = ifSchema;
            this.thenSchema = thenSchema;
            this.elseSchema = elseSchema;
        }

        /**
         * Applies then/else depending on whether if schema matches.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (ifSchema == null) return true;

            boolean result = true;
            BitSet cousinEvaluated = instance.popEvaluated();
            instance.pushEvaluated();
            ctx.pushIgnoreError();
            boolean tested = ifSchema.evaluate(instance, ps, ctx);
            ctx.popIgnoreError();
            if (tested) {
                if (thenSchema != null) {
                    result = thenSchema.evaluate(instance, ps, ctx);
                }
                BitSet ifThenEvaluated = instance.popEvaluated();
                if (result && cousinEvaluated != null && ifThenEvaluated != null)
                    cousinEvaluated.or(ifThenEvaluated);
            } else {
                BitSet droppedEvaluated = instance.popEvaluated();
                if (elseSchema != null) {
                    instance.pushEvaluated();
                    result = elseSchema.evaluate(instance, ps, ctx);
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
    final class AllOfEvaluator implements Evaluator {
        private final JsonSchema[] allOfSchemas;
        /**
         * Creates evaluator for allOf keyword.
         */
        public AllOfEvaluator(JsonSchema[] allOfSchemas) {
            this.allOfSchemas = allOfSchemas;
        }
        /**
         * Requires all subschemas to match.
         * <p>
         * Evaluated-location marks are merged only when all branches succeed.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            boolean result = true;
            BitSet[] evaluatedArr = null;
            BitSet cousinEvaluated = instance.popEvaluated();
            if (cousinEvaluated != null) {
                evaluatedArr = new BitSet[allOfSchemas.length];
            }
            for (int i = 0; i < allOfSchemas.length; i++) {
                JsonSchema schema = allOfSchemas[i];
                instance.pushEvaluated();
                boolean subResult = schema.evaluate(instance, ps, ctx);
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
    final class AnyOfEvaluator implements Evaluator {
        private final JsonSchema[] anyOfSchemas;
        /**
         * Creates evaluator for anyOf keyword.
         */
        public AnyOfEvaluator(JsonSchema[] anyOfSchemas) {
            this.anyOfSchemas = anyOfSchemas;
        }
        /**
         * Requires at least one subschema to match.
         * <p>
         * Branch errors are probed in ignore-error mode; a single aggregate error
         * is emitted when no branch matches.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            boolean result = false;
            BitSet[] evaluatedArr = null;
            BitSet cousinEvaluated = instance.popEvaluated();
            if (cousinEvaluated != null) {
                evaluatedArr = new BitSet[anyOfSchemas.length];
            }
            for (int i = 0; i < anyOfSchemas.length; i++) {
                JsonSchema schema = anyOfSchemas[i];
                instance.pushEvaluated();
                ctx.pushIgnoreError();
                boolean subResult = schema.evaluate(instance, ps, ctx);
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
                ctx.addError(ps, "anyOf", "No schemas matched");
            }
            return result;
        }
    }

    // oneOf
    final class OneOfEvaluator implements Evaluator {
        private final JsonSchema[] oneOfSchemas;
        /**
         * Creates evaluator for oneOf keyword.
         */
        public OneOfEvaluator(JsonSchema[] oneOfSchemas) {
            this.oneOfSchemas = oneOfSchemas;
        }
        /**
         * Requires exactly one subschema to match.
         * <p>
         * Branch errors are probed in ignore-error mode; evaluated-location marks
         * are merged only for successful single-match result.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            int matches = 0;
            BitSet[] evaluatedArr = null;
            BitSet cousinEvaluated = instance.popEvaluated();
            if (cousinEvaluated != null) {
                evaluatedArr = new BitSet[oneOfSchemas.length];
            }
            for (int i = 0; i < oneOfSchemas.length; i++) {
                JsonSchema schema = oneOfSchemas[i];
                instance.pushEvaluated();
                ctx.pushIgnoreError();
                boolean subResult = schema.evaluate(instance, ps, ctx);
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
                ctx.addError(ps, "oneOf", "Must match exactly 1 schema, but found " + matches);
                return false;
            }
            return true;
        }
    }

    // not
    final class NotEvaluator implements Evaluator {
        private final JsonSchema notSchema;
        /**
         * Creates evaluator for not keyword.
         */
        public NotEvaluator(JsonSchema notSchema) {
            this.notSchema = notSchema;
        }
        /**
         * Fails when the subschema matches.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            instance.pushEvaluated();
            ctx.pushIgnoreError();
            boolean result = notSchema.evaluate(instance, ps, ctx);
            ctx.popIgnoreError();
            instance.popEvaluated();

            if (result) {
                ctx.addError(ps, "not", "Must not match schema in not");
                return false;
            }
            return true;
        }
    }

    // unevaluatedProperties / unevaluatedItems
    final class UnevaluatedEvaluator implements Evaluator {
        private final JsonSchema unevaluatedPropertiesSchema;
        private final JsonSchema unevaluatedItemsSchema;
        public UnevaluatedEvaluator(JsonSchema unevaluatedPropertiesSchema,
                                    JsonSchema unevaluatedItemsSchema) {
            this.unevaluatedPropertiesSchema = unevaluatedPropertiesSchema;
            this.unevaluatedItemsSchema = unevaluatedItemsSchema;
        }
        /**
         * Validates unevaluated properties/items against fallback schemas.
         * <p>
         * Uses merged evaluated bitset from prior keywords and propagates newly
         * evaluated marks back to parent scope.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            boolean result = true;
            BitSet merged = instance.mergedEvaluated();
            Object actual = instance.getNode();
            if (unevaluatedPropertiesSchema != null) {
                if (instance.getJsonType() != JsonType.OBJECT) return true;
                int propIdx = 0;
                for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(actual)) {
                    String key = entry.getKey();
                    if (!merged.get(propIdx)) {
                        InstancedNode subInstance = instance.inferSubByKey(key, entry.getValue());
                        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, instance.getObjectType(), key);
                        boolean subResult = unevaluatedPropertiesSchema.evaluate(subInstance, cps, ctx);
                        if (subResult) instance.markEvaluated(propIdx);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return false;
                    }
                    propIdx++;
                }
            }
            if (unevaluatedItemsSchema != null) {
                if (instance.getJsonType() != JsonType.ARRAY) return true;
                Iterator<Object> it = Nodes.iteratorInArray(actual);
                for (int i = 0; it.hasNext(); i++) {
                    Object subActual = it.next();
                    if (!merged.get(i)) {
                        InstancedNode subInstance = instance.inferSubByIndex(i, subActual);
                        PathSegment cps = ps == null ? null : new PathSegment.Index(ps, instance.getObjectType(), i);
                        boolean subResult = unevaluatedItemsSchema.evaluate(subInstance, cps, ctx);
                        if (subResult) instance.markEvaluated(i);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return false;
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
