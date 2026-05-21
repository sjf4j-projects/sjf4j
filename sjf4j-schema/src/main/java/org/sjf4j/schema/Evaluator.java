package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.facade.simple.SimpleJsonReader;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Numbers;
import org.sjf4j.path.PathSegment;

import java.io.StringReader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Deque;
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
 * against the current instance node and report messages via context. They are
 * runtime-only executors: parsing, resource registration, and reference binding
 * happen earlier in {@link SchemaPlanner}.
 */
public interface Evaluator {

    /**
     * Evaluates a keyword against the given instance.
     *
     * @return true when keyword validation succeeds for current instance branch
     */
    boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx);


    /// Built-in evaluators

    // $ref
    final class RefEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final String ref;
        SchemaPlan plan;

        /**
         * Creates evaluator for $ref target.
         */
        public RefEvaluator(PathSegment keywordPs, URI schemaUri, String ref) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.ref = ref;

        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (plan != null) {
                instance.checkCyclicRef(plan, keywordPs);
                return plan.evaluate(instance, ps, ctx);
            }
            throw new AssertionError(RefEvaluator.class);
        }
    }

    // $dynamicRef
    final class DynamicRefEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final String ref;
        SchemaPlan initialPlan;
        String dynamicAnchorName;

        /**
         * Creates evaluator for $dynamicRef target.
         * <p>
         * The final target is resolved in two phases: first to an initial static
         * plan during compilation, then optionally rebound at runtime using the
         * active dynamic-anchor scope stack.
         */
        public DynamicRefEvaluator(PathSegment keywordPs, URI schemaUri, String ref) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.ref = ref;
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (initialPlan != null) {
                SchemaPlan plan = initialPlan;
                if (dynamicAnchorName != null) {
                    // JSON Pointer fragments behave like plain $ref targets.
                    // Only anchor-like fragments participate in dynamic rebinding.
                    Deque<SchemaPlan> planStack = ctx.planStack();
                    if (planStack != null) {
                        Iterator<SchemaPlan> it = planStack.descendingIterator();
                        while (it.hasNext()) {
                            SchemaPlan scopedPlan = it.next();
                            if (scopedPlan.byDynamicAnchorPlans == null) continue;
                            SchemaPlan candidate = scopedPlan.byDynamicAnchorPlans.get(dynamicAnchorName);
                            if (candidate != null && candidate != initialPlan) {
                                plan = candidate;
                                break;
                            }
                        }
                    }
                }

                instance.checkCyclicRef(plan, keywordPs);
                return plan.evaluate(instance, ps, ctx);
            }
            throw new AssertionError(DynamicRefEvaluator.class);
        }
    }


    // type
    final class TypeEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final String type;
        final JsonType jsonType;
        final String[] types;
        final JsonType[] jsonTypes;

        /**
         * Creates evaluator for type keyword value.
         */
        public TypeEvaluator(PathSegment keywordPs, URI schemaUri, Object type) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            Objects.requireNonNull(type, "type");
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
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_INVALID,
                        "invalid 'type' keyword: expected string or array, found '" +
                                type.getClass().getSimpleName() + "'",
                        keywordPs, null));
            }
        }
        /**
         * Validates instance type against a single or union type.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (jsonType != null) {
                if (!_matches(jsonType, instance)) {
                    ctx.addError(instance, ps, keywordPs, schemaUri, "type",
                            "expected type " + type + ", found " + instance.jsonType());
                    return false;
                }
                return true;
            }
            if (jsonTypes != null) {
                for (JsonType expected : jsonTypes) {
                    if (_matches(expected, instance)) {
                        return true;
                    }
                }
                ctx.addError(instance, ps, keywordPs, schemaUri, "type",
                        "expected one of " + Arrays.toString(types) + ", found " + instance.jsonType());
                return false;
            }
            return true;
        }

        private boolean _matches(JsonType expected, InstancedNode instance) {
            JsonType jt = instance.jsonType();
            if (expected != jt) {
                // JSON Schema compatibility: integer ⊂ number
                if (expected == JsonType.INTEGER && jt == JsonType.NUMBER) {
                    Object actual = instance.node();
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
        final PathSegment keywordPs;
        final URI schemaUri;
        final Object constValue;
        
        /**
         * Creates evaluator for const keyword.
         */
        public ConstEvaluator(PathSegment keywordPs, URI schemaUri, Object constValue) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.constValue = constValue;
        }
        /**
         * Ensures the instance equals the const value using node semantics.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            Object actual = instance.node();
            if (!Nodes.equals(constValue, actual)) {
                ctx.addError(instance, ps, keywordPs, schemaUri, "const",
                        "expected constant " + Nodes.inspect(constValue) + ", found " + Nodes.inspect(actual));
                return false;
            }
            return true;
        }
    }

    // enum
    final class EnumEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final Object[] enumValues;
        /**
         * Creates evaluator for enum keyword values.
         */
        public EnumEvaluator(PathSegment keywordPs, URI schemaUri, Object[] enumValues) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.enumValues = Objects.requireNonNull(enumValues);
        }
        /**
         * Checks whether the instance matches any value in the enum.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            Object actual = instance.node();
            for (Object allowed : enumValues) {
                if (Nodes.equals(allowed, actual)) return true;
            }
            ctx.addError(instance, ps, keywordPs, schemaUri, "enum",
                    "expected one of " + Nodes.inspect(enumValues) + ", found " + Nodes.inspect(actual));
            return false;
        }
    }


    // minimum / maximum / exclusiveMinimum / exclusiveMaximum
    final class NumberEvaluator implements Evaluator {
        final PathSegment minimumKeywordPs;
        final PathSegment maximumKeywordPs;
        final PathSegment exclusiveMinimumKeywordPs;
        final PathSegment exclusiveMaximumKeywordPs;
        final URI schemaUri;
        final boolean hasMinimum;
        final double minimum;
        final boolean hasMaximum;
        final double maximum;
        final boolean hasExclusiveMinimum;
        final double exclusiveMinimum;
        final boolean hasExclusiveMaximum;
        final double exclusiveMaximum;
        public NumberEvaluator(PathSegment minimumKeywordPs, PathSegment maximumKeywordPs,
                               PathSegment exclusiveMinimumKeywordPs, PathSegment exclusiveMaximumKeywordPs,
                               URI schemaUri, Number minimum, Number maximum,
                               Number exclusiveMinimum, Number exclusiveMaximum) {
            this.minimumKeywordPs = minimumKeywordPs;
            this.maximumKeywordPs = maximumKeywordPs;
            this.exclusiveMinimumKeywordPs = exclusiveMinimumKeywordPs;
            this.exclusiveMaximumKeywordPs = exclusiveMaximumKeywordPs;
            this.schemaUri = schemaUri;
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
            JsonType jt = instance.jsonType();
            if (jt != JsonType.NUMBER && jt != JsonType.INTEGER) {
                return true;
            }
            Number actual = Nodes.toNumber(instance.node());
            double actualDouble = actual.doubleValue();
            if (hasMinimum && actualDouble < minimum) {
                ctx.addError(instance, ps, minimumKeywordPs, schemaUri, "minimum",
                        "expected number >= " + minimum + ", found " + actual);
                return false;
            } else if (hasMaximum && actualDouble > maximum) {
                ctx.addError(instance, ps, maximumKeywordPs, schemaUri, "maximum",
                        "expected number <= " + maximum + ", found " + actual);
                return false;
            } else if (hasExclusiveMinimum && actualDouble <= exclusiveMinimum) {
                ctx.addError(instance, ps, exclusiveMinimumKeywordPs, schemaUri, "exclusiveMinimum",
                        "expected number > " + exclusiveMinimum + ", found " + actual);
                return false;
            } else if (hasExclusiveMaximum && actualDouble >= exclusiveMaximum) {
                ctx.addError(instance, ps, exclusiveMaximumKeywordPs, schemaUri, "exclusiveMaximum",
                        "expected number < " + exclusiveMaximum + ", found " + actual);
                return false;
            }
            return true;
        }
    }

    // multipleOf
    final class MultipleOfEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final Number multipleOf;
        final BigDecimal divisor;
        final boolean isIntegerDivisor;
        final long divisorLong;
        final double divisorDouble;
        /**
         * Creates evaluator for multipleOf divisor.
         */
        public MultipleOfEvaluator(PathSegment keywordPs, URI schemaUri, Number multipleOf) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.multipleOf = multipleOf;
            this.divisor = Numbers.normalizeDecimal(multipleOf);
            if (divisor.signum() <= 0)
                throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_INVALID,
                        "invalid 'multipleOf' keyword: value must be > 0", keywordPs, null));
            this.isIntegerDivisor = divisor.scale() <= 0;
            this.divisorLong = isIntegerDivisor ? divisor.longValueExact() : 0L;
            this.divisorDouble = divisor.doubleValue();
        }

        /**
         * Validates that a numeric instance is a multiple of the divisor.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            JsonType jt = instance.jsonType();
            if (jt != JsonType.NUMBER && jt != JsonType.INTEGER) {
                return true;
            }
            Number actual = Nodes.toNumber(instance.node());
            if (isIntegerDivisor && Numbers.isSemanticInteger(actual)) {
                long v = actual.longValue();
                if (v % divisorLong != 0) {
                    ctx.addError(instance, ps, keywordPs, schemaUri, "multipleOf",
                            "expected a multiple of " + multipleOf + ", found " + actual);
                    return false;
                }
            } else if (isIntegerDivisor && (actual instanceof Double || actual instanceof Float)) {
                double dv = actual.doubleValue();
                double q = dv / divisorDouble;
                if (q != Math.rint(q)) {
                    ctx.addError(instance, ps, keywordPs, schemaUri, "multipleOf",
                            "expected a multiple of " + multipleOf + ", found " + actual);
                    return false;
                }
            } else {
                BigDecimal v = Numbers.normalizeDecimal(actual);
                BigDecimal[] dr = v.divideAndRemainder(divisor);
                if (dr[1].signum() != 0) {
                    ctx.addError(instance, ps, keywordPs, schemaUri, "multipleOf",
                            "expected a multiple of " + multipleOf + ", found " + actual);
                    return false;
                }
            }
            return true;
        }
    }

    // minLength / maxLength
    final class StringEvaluator implements Evaluator {
        final PathSegment minLengthKeywordPs;
        final PathSegment maxLengthKeywordPs;
        final URI schemaUri;
        final Integer minLength;
        final Integer maxLength;
        /**
         * Creates evaluator for string length constraints.
         */
        public StringEvaluator(PathSegment minLengthKeywordPs, PathSegment maxLengthKeywordPs,
                               URI schemaUri, Integer minLength, Integer maxLength) {
            this.minLengthKeywordPs = minLengthKeywordPs;
            this.maxLengthKeywordPs = maxLengthKeywordPs;
            this.schemaUri = schemaUri;
            this.minLength = minLength;
            this.maxLength = maxLength;
        }
        /**
         * Enforces minLength/maxLength for strings.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.STRING) return true;

            String actual = Nodes.toString(instance.node());
            int length = SchemaUtil.stringIcuLength(actual);
            if (minLength != null && length < minLength) {
                ctx.addError(instance, ps, minLengthKeywordPs, schemaUri, "minLength",
                        "expected string length >= " + minLength + ", found " + length);
                return false;
            }
            if (maxLength != null && length > maxLength) {
                ctx.addError(instance, ps, maxLengthKeywordPs, schemaUri, "maxLength",
                        "expected string length <= " + maxLength + ", found " + length);
                return false;
            }
            return true;
        }
    }

    // pattern
    final class PatternEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final String pattern;
        final Pattern pn;
        /**
         * Creates evaluator for pattern keyword.
         */
        public PatternEvaluator(PathSegment keywordPs, URI schemaUri, String pattern) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.pattern = Objects.requireNonNull(pattern);
            this.pn = SchemaUtil.compileRegexPattern(pattern, "pattern");
        }

        /**
         * Ensures a string matches the configured regex pattern.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.STRING) return true;

            String actual = Nodes.toString(instance.node());
            if (!pn.matcher(actual).find()) {
                ctx.addError(instance, ps, keywordPs, schemaUri, "pattern",
                        "expected string matching pattern '" + pattern + "'");
                return false;
            }
            return true;
        }
    }

    // format
    final class FormatEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final String format;
        final FormatValidator formatValidator;
        final boolean assertion;
        /**
         * Creates evaluator for format keyword.
         */
        public FormatEvaluator(PathSegment keywordPs, URI schemaUri, String format, boolean assertion) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.format = Objects.requireNonNull(format, "format");
            this.formatValidator = FormatValidator.of(format);
            this.assertion = assertion;
        }
        /**
         * Validates a string against the configured format when assertion is enabled.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.STRING) return true;
            String actual = Nodes.toString(instance.node());
            if (assertion || ctx.isStrictFormat()) {
                if (!formatValidator.validate(actual)) {
                    ctx.addError(instance, ps, keywordPs, schemaUri, "format",
                            "expected format " + format + ", found '" + actual + "'");
                    return false;
                }
            }
            return true;
        }
    }

    // contentEncoding / contentMediaType
    final class ContentEvaluator implements Evaluator {
        final PathSegment contentEncodingKeywordPs;
        final PathSegment contentMediaTypeKeywordPs;
        final URI schemaUri;
        final String contentEncoding;
        final String contentMediaType;

        public ContentEvaluator(PathSegment contentEncodingKeywordPs, PathSegment contentMediaTypeKeywordPs,
                                URI schemaUri, String contentEncoding, String contentMediaType) {
            this.contentEncodingKeywordPs = contentEncodingKeywordPs;
            this.contentMediaTypeKeywordPs = contentMediaTypeKeywordPs;
            this.schemaUri = schemaUri;
            this.contentEncoding = contentEncoding;
            this.contentMediaType = contentMediaType;
        }

        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.STRING) return true;

            String content = Nodes.toString(instance.node());
            if (contentEncoding != null) {
                if (!"base64".equalsIgnoreCase(contentEncoding)) return true;
                try {
                    byte[] decoded = Base64.getDecoder().decode(content);
                    content = new String(decoded, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    ctx.addError(instance, ps, contentEncodingKeywordPs, schemaUri, "contentEncoding",
                            "expected base64-encoded content");
                    return false;
                }
            }

            if (contentMediaType != null) {
                if (!"application/json".equalsIgnoreCase(contentMediaType)) return true;
                try {
                    SimpleJsonReader reader = new SimpleJsonReader(new StringReader(content));
                    reader.skipNext();
                    if (reader.peekToken() != StreamingReader.Token.UNKNOWN) {
                        throw new IllegalArgumentException("trailing content");
                    }
                } catch (Exception e) {
                    ctx.addError(instance, ps, contentMediaTypeKeywordPs, schemaUri, "contentMediaType",
                            "expected valid JSON content");
                    return false;
                }
            }
            return true;
        }
    }

    // minProperties / maxProperties
    final class ObjectEvaluator implements Evaluator {
        final PathSegment minPropertiesKeywordPs;
        final PathSegment maxPropertiesKeywordPs;
        final URI schemaUri;
        final Integer minProperties;
        final Integer maxProperties;
        /**
         * Creates evaluator for object size constraints.
         */
        public ObjectEvaluator(PathSegment minPropertiesKeywordPs, PathSegment maxPropertiesKeywordPs,
                               URI schemaUri, Integer minProperties, Integer maxProperties) {
            this.minPropertiesKeywordPs = minPropertiesKeywordPs;
            this.maxPropertiesKeywordPs = maxPropertiesKeywordPs;
            this.schemaUri = schemaUri;
            this.minProperties = minProperties;
            this.maxProperties = maxProperties;
        }

        /**
         * Enforces minProperties/maxProperties for objects.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.OBJECT) return true;

            Object actual = instance.node();
            int size = Nodes.sizeInObject(actual);
            if (minProperties != null && size < minProperties) {
                ctx.addError(instance, ps, minPropertiesKeywordPs, schemaUri, "minProperties",
                        "expected at least " + minProperties + " properties, found " + size);
                return false;
            }
            if (maxProperties != null && size > maxProperties) {
                ctx.addError(instance, ps, maxPropertiesKeywordPs, schemaUri, "maxProperties",
                        "expected at most " + maxProperties + " properties, found " + size);
                return false;
            }
            return true;
        }
    }

    // properties / patternProperties / additionalProperties
    final class PropertiesEvaluator implements Evaluator {
        final Map<String, SchemaPlan> properties;
        final Pattern[] patterns;
        final SchemaPlan[] patternPlans;
        final SchemaPlan additionalPropertiesPlan;
        public PropertiesEvaluator(Map<String, SchemaPlan> properties,
                                   Map<String, SchemaPlan> patternProperties,
                                   SchemaPlan additionalPropertiesPlan) {
            this.properties = properties;
            if (patternProperties != null) {
                this.patterns = new Pattern[patternProperties.size()];
                this.patternPlans = new SchemaPlan[patternProperties.size()];
                int i = 0;
                for (Map.Entry<String, SchemaPlan> entry : patternProperties.entrySet()) {
                    this.patterns[i] = SchemaUtil.compileRegexPattern(entry.getKey(), "patternProperties");
                    this.patternPlans[i] = entry.getValue();
                    i++;
                }
            } else {
                this.patterns = null;
                this.patternPlans = null;
            }
            this.additionalPropertiesPlan = additionalPropertiesPlan;
        }

        /**
         * Validates properties, patternProperties, and additionalProperties.
         * <p>
         * Successful property validations mark indices as evaluated for later
         * unevaluatedProperties processing.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.OBJECT) return true;

            Object actual = instance.node();
            boolean result = true;
            int propIdx = 0;
            for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(actual)) {
                String key = entry.getKey();
                Object value = entry.getValue();
                boolean matched = false;
                if (properties != null) {
                    SchemaPlan plan = properties.get(key);
                    if (plan != null) {
                        InstancedNode subInstance = instance.inferSubByKey(key, value, ctx.reusedLeaf());
                        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                        boolean subResult = plan.evaluate(subInstance, cps, ctx);
                        if (subResult) instance.markEvaluated(propIdx);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return result;
                        matched = true;
                    }
                }

                if (patterns != null) {
                    for (int i = 0; i < patterns.length; i++) {
                        if (patterns[i].matcher(key).find()) {
                            SchemaPlan plan = patternPlans[i];
                            if (plan != null) {
                                InstancedNode subInstance = instance.inferSubByKey(key, value, ctx.reusedLeaf());
                                PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                                boolean subResult = plan.evaluate(subInstance, cps, ctx);
                                if (subResult) instance.markEvaluated(propIdx);
                                result = result && subResult;
                                if (ctx.shouldAbort()) return result;
                                matched = true;
                            }
                        }
                    }
                }

                if (additionalPropertiesPlan != null && !matched) {
                    InstancedNode subInstance = instance.inferSubByKey(key, value, ctx.reusedLeaf());
                    PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                    boolean subResult = additionalPropertiesPlan.evaluate(subInstance, cps, ctx);
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
        final PathSegment requiredKeywordPs;
        final PathSegment dependentRequiredKeywordPs;
        final URI schemaUri;
        final String[] required;
        final Map<String, String[]> dependentRequired;
        /**
         * Creates evaluator for required/dependentRequired.
         */
        public RequiredEvaluator(PathSegment requiredKeywordPs, PathSegment dependentRequiredKeywordPs,
                                 URI schemaUri, String[] required, Map<String, String[]> dependentRequired) {
            this.requiredKeywordPs = requiredKeywordPs;
            this.dependentRequiredKeywordPs = dependentRequiredKeywordPs;
            this.schemaUri = schemaUri;
            this.required = required;
            this.dependentRequired = dependentRequired;
        }
        /**
         * Validates required and dependentRequired keys.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.OBJECT) return true;

            Object actual = instance.node();
            boolean result = true;
            if (required != null) {
                for (String key : required) {
                    if (!Nodes.containsInObject(actual, key)) {
                        ctx.addError(instance, ps, requiredKeywordPs, schemaUri, "required",
                                "missing required property '" + key + "'");
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
                                ctx.addError(instance, ps, dependentRequiredKeywordPs, schemaUri, "dependentRequired",
                                        "missing property '" + property + "' required when '" + key + "' is present");
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

    // dependencies
    final class DependenciesEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final Map<String, String[]> dependentRequired;
        final Map<String, SchemaPlan> dependentPlans;
        /**
         * Creates evaluator for legacy dependencies keyword.
         */
        public DependenciesEvaluator(PathSegment keywordPs, URI schemaUri,
                                     Map<String, String[]> dependentRequired,
                                     Map<String, SchemaPlan> dependentPlans) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.dependentRequired = dependentRequired;
            this.dependentPlans = dependentPlans;
        }
        /**
         * Applies array and schema forms of legacy dependencies.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.OBJECT) return true;

            Object actual = instance.node();
            boolean result = true;
            if (dependentRequired != null) {
                for (Map.Entry<String, String[]> entry : dependentRequired.entrySet()) {
                    String key = entry.getKey();
                    if (Nodes.containsInObject(actual, key)) {
                        for (String property : entry.getValue()) {
                            if (!Nodes.containsInObject(actual, property)) {
                                ctx.addError(instance, ps, keywordPs, schemaUri, "dependencies",
                                        "missing property '" + property + "' required when '" + key + "' is present");
                                result = false;
                            }
                        }
                        if (ctx.shouldAbort()) return result;
                    }
                }
            }
            if (dependentPlans != null) {
                for (Map.Entry<String, SchemaPlan> entry : dependentPlans.entrySet()) {
                    String key = entry.getKey();
                    if (Nodes.containsInObject(actual, key)) {
                        boolean subResult = entry.getValue().evaluate(instance, ps, ctx);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return result;
                    }
                }
            }
            return result;
        }
    }

    // dependentSchemas
    final class DependentSchemasEvaluator implements Evaluator {
        final Map<String, SchemaPlan> dependentPlans;
        /**
         * Creates evaluator for dependentSchemas.
         */
        public DependentSchemasEvaluator(Map<String, SchemaPlan> dependentPlans) {
            this.dependentPlans = dependentPlans;
        }
        /**
         * Applies schemas when dependent properties are present.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.OBJECT) return true;

            Object actual = instance.node();
            boolean result = true;
            for (Map.Entry<String, SchemaPlan> entry : dependentPlans.entrySet()) {
                String key = entry.getKey();
                if (Nodes.containsInObject(actual, key)) {
                    SchemaPlan plan = dependentPlans.get(key);
                    boolean subResult = plan.evaluate(instance, ps, ctx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
            }
            return result;
        }
    }

    // propertyNames
    final class PropertyNamesEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final SchemaPlan propertyNamesPlan;
        /**
         * Creates evaluator for propertyNames schema.
         */
        public PropertyNamesEvaluator(PathSegment keywordPs, URI schemaUri, SchemaPlan propertyNamesPlan) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.propertyNamesPlan = propertyNamesPlan;
        }
        /**
         * Validates each property name against propertyNames schema.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.OBJECT) return true;

            Object actual = instance.node();
            boolean result = true;
            for (String key : Nodes.keySetInObject(actual)) {
                ctx.pushIgnoreError();
                boolean probed = propertyNamesPlan.evaluate(InstancedNode.infer(key), ps, ctx);
                ctx.popIgnoreError();
                if (!probed) {
                    PathSegment instanceKeywordPs = ps == null
                            ? new PathSegment.Name(instance.materializePath(), key)
                            : new PathSegment.Name(ps, key);
                    ctx.addError(instance, instanceKeywordPs, keywordPs, schemaUri, "propertyNames",
                            "invalid property name '" + key + "'");
                    result = false;
                    if (ctx.shouldAbort()) return result;
                }
            }
            return result;
        }
    }

    // minItems / maxItems / uniqueItems
    final class ArrayEvaluator implements Evaluator {
        final PathSegment minItemsKeywordPs;
        final PathSegment maxItemsKeywordPs;
        final PathSegment uniqueItemsKeywordPs;
        final URI schemaUri;
        final Integer minItems;
        final Integer maxItems;
        final Boolean uniqueItems;
        /**
         * Creates evaluator for array size/uniqueness constraints.
         */
        public ArrayEvaluator(PathSegment minItemsKeywordPs, PathSegment maxItemsKeywordPs,
                              PathSegment uniqueItemsKeywordPs, URI schemaUri,
                              Integer minItems, Integer maxItems, Boolean uniqueItems) {
            this.minItemsKeywordPs = minItemsKeywordPs;
            this.maxItemsKeywordPs = maxItemsKeywordPs;
            this.uniqueItemsKeywordPs = uniqueItemsKeywordPs;
            this.schemaUri = schemaUri;
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.uniqueItems = uniqueItems;
        }
        /**
         * Enforces minItems/maxItems/uniqueItems for arrays.
         * <p>
         * Uniqueness currently relies on Java object equality for already mapped
         * runtime values.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.ARRAY) return true;

            Object actual = instance.node();
            boolean result = true;
            int size = Nodes.sizeInArray(actual);
            if (minItems != null && size < minItems) {
                ctx.addError(instance, ps, minItemsKeywordPs, schemaUri, "minItems",
                        "expected at least " + minItems + " items, found " + size);
                result = false;
            }
            if (maxItems != null && size > maxItems) {
                ctx.addError(instance, ps, maxItemsKeywordPs, schemaUri, "maxItems",
                        "expected at most " + maxItems + " items, found " + size);
                result = false;
            }
            if (ctx.shouldAbort()) return result;
            if (Boolean.TRUE.equals(uniqueItems)) {
                Set<Object> set = new HashSet<>();
                for (Iterator<Object> it = Nodes.iteratorInArray(actual); it.hasNext(); ) {
                    Object v = it.next();
                    if (!set.add(v)) {
                        ctx.addError(instance, ps, uniqueItemsKeywordPs, schemaUri, "uniqueItems",
                                "expected unique array items");
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
        final SchemaPlan itemsPlan;
        final SchemaPlan[] prefixItemsPlans;
        /**
         * Creates evaluator for items/prefixItems schemas.
         */
        public ItemsEvaluator(SchemaPlan itemsPlan, SchemaPlan[] prefixItemsPlans) {
            this.itemsPlan = itemsPlan;
            this.prefixItemsPlans = prefixItemsPlans;
        }
        /**
         * Validates prefixItems and items schemas for array elements.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (instance.jsonType() != JsonType.ARRAY) return true;

            Object actual = instance.node();
            boolean result = true;
            int size = Nodes.sizeInArray(actual);
            int i = 0;
            if (prefixItemsPlans != null) {
                for (; i < size && i < prefixItemsPlans.length; i++) {
                    InstancedNode subInstance = instance.inferSubByIndex(i, Nodes.getInArray(actual, i), ctx.reusedLeaf());
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, i);
                    boolean subResult = prefixItemsPlans[i].evaluate(subInstance, cps, ctx);
                    result = result && subResult;
                    if (ctx.shouldAbort()) return result;
                }
                if (result) instance.markEvaluated(0, i);
            }
            if (itemsPlan != null) {
                for (; i < size; i++) {
                    InstancedNode subInstance = instance.inferSubByIndex(i, Nodes.getInArray(actual, i), ctx.reusedLeaf());
                    PathSegment cps = ps == null ? null : new PathSegment.Index(ps, i);
                    boolean subResult = itemsPlan.evaluate(subInstance, cps, ctx);
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
        final PathSegment minContainsKeywordPs;
        final PathSegment maxContainsKeywordPs;
        final URI schemaUri;
        final SchemaPlan containsPlan;
        final Integer minContains;
        final Integer maxContains;
        /**
         * Creates evaluator for contains/minContains/maxContains.
         */
        public ContainsEvaluator(PathSegment minContainsKeywordPs, PathSegment maxContainsKeywordPs,
                                 URI schemaUri, SchemaPlan containsPlan, Integer minContains, Integer maxContains) {
            this.minContainsKeywordPs = minContainsKeywordPs;
            this.maxContainsKeywordPs = maxContainsKeywordPs;
            this.schemaUri = schemaUri;
            this.containsPlan = containsPlan;
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
            if (instance.jsonType() != JsonType.ARRAY) return true;
            if (containsPlan == null) return true;

            Object actual = instance.node();
            int matches = 0;
            Iterator<Object> it = Nodes.iteratorInArray(actual);
            for (int i = 0; it.hasNext(); i++) {
                Object subActual = it.next();
                ctx.pushIgnoreError();
                InstancedNode subInstance = instance.inferSubByIndex(i, subActual, ctx.reusedLeaf());
                PathSegment cps = ps == null ? null : new PathSegment.Index(ps, i);
                boolean result = containsPlan.evaluate(subInstance, cps, ctx);
                ctx.popIgnoreError();
                if (result) {
                    instance.markEvaluated(i);
                    matches++;
                }
            }
            if (matches < minContains) {
                ctx.addError(instance, ps, minContainsKeywordPs, schemaUri, "minContains",
                        "expected at least " + minContains + " matching items, found " + matches);
                return false;
            }
            if (maxContains != null && matches > maxContains) {
                ctx.addError(instance, ps, maxContainsKeywordPs, schemaUri, "maxContains",
                        "expected at most " + maxContains + " matching items, found " + matches);
                return false;
            }
            return true;
        }
    }

    // if / then / else
    final class IfThenElseEvaluator implements Evaluator {
        final SchemaPlan ifPlan;
        final SchemaPlan thenPlan;
        final SchemaPlan elsePlan;
        /**
         * Creates evaluator for if/then/else keywords.
         */
        public IfThenElseEvaluator(SchemaPlan ifPlan, SchemaPlan thenPlan, SchemaPlan elsePlan) {
            this.ifPlan = ifPlan;
            this.thenPlan = thenPlan;
            this.elsePlan = elsePlan;
        }

        /**
         * Applies then/else depending on whether if schema matches.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            if (ifPlan == null) return true;

            boolean result = true;
            BitSet cousinEvaluated = instance.popEvaluated();
            instance.pushEvaluated();
            ctx.pushIgnoreError();
            boolean tested = ifPlan.evaluate(instance, ps, ctx);
            ctx.popIgnoreError();
            if (tested) {
                if (thenPlan != null) {
                    result = thenPlan.evaluate(instance, ps, ctx);
                }
                BitSet ifThenEvaluated = instance.popEvaluated();
                if (result && cousinEvaluated != null && ifThenEvaluated != null)
                    cousinEvaluated.or(ifThenEvaluated);
            } else {
                BitSet droppedEvaluated = instance.popEvaluated();
                if (elsePlan != null) {
                    instance.pushEvaluated();
                    result = elsePlan.evaluate(instance, ps, ctx);
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
        final SchemaPlan[] allOfPlans;
        /**
         * Creates evaluator for allOf keyword.
         */
        public AllOfEvaluator(SchemaPlan[] allOfPlans) {
            this.allOfPlans = allOfPlans;
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
                evaluatedArr = new BitSet[allOfPlans.length];
            }
            for (int i = 0; i < allOfPlans.length; i++) {
                SchemaPlan plan = allOfPlans[i];
                instance.pushEvaluated();
                boolean subResult = plan.evaluate(instance, ps, ctx);
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
        final PathSegment keywordPs;
        final URI schemaUri;
        final SchemaPlan[] anyOfPlans;
        /**
         * Creates evaluator for anyOf keyword.
         */
        public AnyOfEvaluator(PathSegment keywordPs, URI schemaUri, SchemaPlan[] anyOfPlans) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.anyOfPlans = anyOfPlans;
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
                evaluatedArr = new BitSet[anyOfPlans.length];
            }
            for (int i = 0; i < anyOfPlans.length; i++) {
                SchemaPlan plan = anyOfPlans[i];
                instance.pushEvaluated();
                ctx.pushIgnoreError();
                boolean subResult = plan.evaluate(instance, ps, ctx);
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
                ctx.addError(instance, ps, keywordPs, schemaUri, "anyOf",
                        "expected at least one matching schema");
            }
            return result;
        }
    }

    // oneOf
    final class OneOfEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final SchemaPlan[] oneOfPlans;
        /**
         * Creates evaluator for oneOf keyword.
         */
        public OneOfEvaluator(PathSegment keywordPs, URI schemaUri, SchemaPlan[] oneOfPlans) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.oneOfPlans = oneOfPlans;
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
                evaluatedArr = new BitSet[oneOfPlans.length];
            }
            for (int i = 0; i < oneOfPlans.length; i++) {
                SchemaPlan plan = oneOfPlans[i];
                instance.pushEvaluated();
                ctx.pushIgnoreError();
                boolean subResult = plan.evaluate(instance, ps, ctx);
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
                ctx.addError(instance, ps, keywordPs, schemaUri, "oneOf",
                        "expected exactly 1 matching schema, found " + matches);
                return false;
            }
            return true;
        }
    }

    // not
    final class NotEvaluator implements Evaluator {
        final PathSegment keywordPs;
        final URI schemaUri;
        final SchemaPlan notPlan;
        /**
         * Creates evaluator for not keyword.
         */
        public NotEvaluator(PathSegment keywordPs, URI schemaUri, SchemaPlan notPlan) {
            this.keywordPs = keywordPs;
            this.schemaUri = schemaUri;
            this.notPlan = notPlan;
        }
        /**
         * Fails when the subschema matches.
         */
        @Override
        public boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx) {
            instance.pushEvaluated();
            ctx.pushIgnoreError();
            boolean result = notPlan.evaluate(instance, ps, ctx);
            ctx.popIgnoreError();
            instance.popEvaluated();

            if (result) {
                ctx.addError(instance, ps, keywordPs, schemaUri, "not",
                        "schema in 'not' matched");
                return false;
            }
            return true;
        }
    }

    // unevaluatedProperties / unevaluatedItems
    final class UnevaluatedEvaluator implements Evaluator {
        final SchemaPlan unevaluatedPropertiesPlan;
        final SchemaPlan unevaluatedItemsPlan;
        public UnevaluatedEvaluator(SchemaPlan unevaluatedPropertiesPlan,
                                    SchemaPlan unevaluatedItemsPlan) {
            this.unevaluatedPropertiesPlan = unevaluatedPropertiesPlan;
            this.unevaluatedItemsPlan = unevaluatedItemsPlan;
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
            Object actual = instance.node();
            if (unevaluatedPropertiesPlan != null) {
                if (instance.jsonType() != JsonType.OBJECT) return true;
                int propIdx = 0;
                for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(actual)) {
                    String key = entry.getKey();
                    if (!merged.get(propIdx)) {
                        InstancedNode subInstance = instance.inferSubByKey(key, entry.getValue(), ctx.reusedLeaf());
                        PathSegment cps = ps == null ? null : new PathSegment.Name(ps, key);
                        boolean subResult = unevaluatedPropertiesPlan.evaluate(subInstance, cps, ctx);
                        if (subResult) instance.markEvaluated(propIdx);
                        result = result && subResult;
                        if (ctx.shouldAbort()) return false;
                    }
                    propIdx++;
                }
            }
            if (unevaluatedItemsPlan != null) {
                if (instance.jsonType() != JsonType.ARRAY) return true;
                Iterator<Object> it = Nodes.iteratorInArray(actual);
                for (int i = 0; it.hasNext(); i++) {
                    Object subActual = it.next();
                    if (!merged.get(i)) {
                        InstancedNode subInstance = instance.inferSubByIndex(i, subActual, ctx.reusedLeaf());
                        PathSegment cps = ps == null ? null : new PathSegment.Index(ps, i);
                        boolean subResult = unevaluatedItemsPlan.evaluate(subInstance, cps, ctx);
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
