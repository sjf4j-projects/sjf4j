package org.sjf4j.schema;

import org.sjf4j.node.NodeKey;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathToken;
import org.sjf4j.util.NodeUtil;
import org.sjf4j.util.NumberUtil;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class SchemaItem {
    protected String title;
    protected String description;
    protected Object defaultValue;
    protected List<Object> examples;

    public String getTitle() {return title;}
    public void setTitle(String title) {this.title = title;}
    public String getDescription() {return description;}
    public void setDescription(String description) {this.description = description;}
    public Object getDefault() {return defaultValue;}
    public void setDefault(Object defaultValue) {this.defaultValue = defaultValue;}
    public List<Object> getExamples() {return examples;}
    public void setExamples(List<Object> examples) {this.examples = examples;}

    protected Object constValue;
    protected List<Object> enumValues;

    // validate
    protected abstract void validateItem(Object node, ValidationContext context);

    public void validate(Object node, ValidationContext context) {
        if (constValue != null && !NodeUtil.equals(constValue, node)) {
            context.addError("const", "Node must equal const: " + constValue, node);
        } else if (enumValues != null) {
            for (Object enumValue : enumValues) {
                if (NodeUtil.equals(enumValue, node)) return;
            }
            context.addError("const", "Node not in enum: " + enumValues, node);
        } else {
            validateItem(node, context);
        }
    }

    protected boolean matches(Object node) {
        ValidationContext probe = ValidationContext.getProbe();
        validate(node, probe);
        return !probe.shouldAbort();
    }


    /// Subclasses: Empty, False, String, Number, Boolean, Null,
    ///             Object, Array, AllOf, AnyOf, OneOf, Not,
    ///             Ref,

    // Empty (True)
    public static class EmptySchema extends SchemaItem {
        private EmptySchema() {}
        public static final EmptySchema INSTANCE = new EmptySchema();
        @Override
        public void validateItem(Object node, ValidationContext context) { }
    }

    // False
    public static class FalseSchema extends SchemaItem {
        private FalseSchema() {}
        public static final FalseSchema INSTANCE = new FalseSchema();
        @Override
        public void validateItem(Object node, ValidationContext context) {
            context.addError("false", "False schema always fails", node);
        }
    }

    // String
    public static class StringSchema extends SchemaItem {
        private final Integer minLength;
        private final Integer maxLength;
        private final String pattern;
        private final Pattern pn;
        private final String format;
        private final FormatValidator formatValidator;

        public StringSchema(Integer minLength, Integer maxLength, String pattern, String format) {
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.pattern = pattern;
            this.pn = pattern == null ? null : Pattern.compile(pattern);
            this.format = format;
            this.formatValidator = FormatValidator.get(format);
        }

        @Override
        protected void validateItem(Object node, ValidationContext context) {
            NodeType nt = NodeType.of(node);
            if (!nt.isString()) {
                context.addError("type", "Not a string", node);
                return;
            }

            String s = NodeUtil.toString(node);
            if (minLength != null && s.length() < minLength) {
                context.addError("minLength", "Length must be >= " + minLength, node);
            } else if (maxLength != null && s.length() > maxLength) {
                context.addError("maxLength", "Length must be <= " + maxLength, node);
            }
            if (context.shouldAbort()) return;

            if (pn != null && !pn.matcher(s).matches()) {
                context.addError("pattern", "Must match pattern " + pattern, node);
            }
            if (context.shouldAbort()) return;

            if (formatValidator != null && !formatValidator.validate(s)) {
                context.addError("format", "Must match format " + format, node);
            }
        }
    }

    // Number
    public static class NumberSchema extends SchemaItem {
        private final Double minimum;
        private final Double maximum;
        private final Double exclusiveMinimum;
        private final Double exclusiveMaximum;
        private final Number multipleOf;

        private BigDecimal divisor;
        private boolean isIntegerDivisor;
        private long divisorLong;
        private double divisorDouble;

        public NumberSchema(Double minimum, Double maximum, Double exclusiveMinimum, Double exclusiveMaximum,
                            Number multipleOf) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.exclusiveMinimum = exclusiveMinimum;
            this.exclusiveMaximum = exclusiveMaximum;
            this.multipleOf = multipleOf;
            if (multipleOf != null) {
                this.divisor = NumberUtil.normalize(multipleOf);
                if (divisor.signum() <= 0)
                    throw new IllegalArgumentException("multipleOf must be > 0");
                this.isIntegerDivisor = divisor.scale() <= 0;
                this.divisorLong = isIntegerDivisor ? divisor.longValueExact() : 0L;
                this.divisorDouble = divisor.doubleValue();
            }
        }

        @Override
        protected void validateItem(Object node, ValidationContext context) {
            NodeType nt = NodeType.of(node);
            if (!nt.isNumber()) {
                context.addError("type", "Not a number", node);
                return;
            }

            Number n = (Number) node;
            double d = n.doubleValue();
            if (minimum != null && d < minimum) {
                context.addError("minimum", "Value must be >= " + minimum, node);
            } else if (maximum != null && d > maximum) {
                context.addError("maximum", "Value must be <= " + maximum, node);
            } else if (exclusiveMinimum != null && d <= exclusiveMinimum) {
                context.addError("exclusiveMinimum", "Value must be > " + exclusiveMinimum, node);
            } else if (exclusiveMaximum != null && d >= exclusiveMaximum) {
                context.addError("exclusiveMaximum", "Value must be < " + exclusiveMaximum, node);
            }
            if (context.shouldAbort()) return;

            if (multipleOf != null) {
                if (isIntegerDivisor && NumberUtil.isInteger(n)) {
                    long v = n.longValue();
                    if (v % divisorLong != 0) {
                        context.addError("multipleOf", "Value must be multiple of " + multipleOf, node);
                    }
                } else if (isIntegerDivisor && (n instanceof Double || n instanceof Float)) {
                    double dv = n.doubleValue();
                    double q = dv / divisorDouble;
                    if (q != Math.rint(q)) {
                        context.addError("multipleOf", "Value must be multiple of " + multipleOf, node);
                    }
                } else {
                    BigDecimal v = NumberUtil.normalize(n);
                    BigDecimal[] dr = v.divideAndRemainder(divisor);
                    if (dr[1].signum() != 0) {
                        context.addError("multipleOf", "Value must be multiple of " + multipleOf, node);
                    }
                }
            }
        }
    }

    // Boolean
    public static class BooleanSchema extends SchemaItem {
        private BooleanSchema() {}
        public static final BooleanSchema INSTANCE = new BooleanSchema();
        @Override
        public void validateItem(Object node, ValidationContext context) {
            NodeType nt = NodeType.of(node);
            if (!nt.isBoolean()) {
                context.addError("type", "Not a boolean", node);
            }
        }
    }

    // Null
    public static class NullSchema extends SchemaItem {
        private NullSchema() {}
        public static final NullSchema INSTANCE = new NullSchema();
        @Override
        public void validateItem(Object node, ValidationContext context) {
            if (node != null) {
                context.addError("type", "Not a null", node);
            }
        }
    }

    // Object
    public static class ObjectSchema extends SchemaItem {
        private final Map<String, SchemaItem> properties;
        private final Set<String> required;
        private final Integer minProperties;
        private final Integer maxProperties;
        private final SchemaItem additionalProperties;

        public ObjectSchema(Map<String, SchemaItem> properties, Set<String> required,
                            SchemaItem additionalProperties, Integer minProperties, Integer maxProperties) {
            this.properties = properties;
            this.required = required;
            this.minProperties = minProperties;
            this.maxProperties = maxProperties;
            this.additionalProperties = additionalProperties;
        }

        @Override
        protected void validateItem(Object node, ValidationContext context) {
            NodeType nt = NodeType.of(node);
            if (!nt.isObject()) {
                context.addError("type", "Not a object", node);
                return;
            }

            int size = NodeWalker.sizeInObject(node);
            if (minProperties != null && size < minProperties) {
                context.addError("minProperties", "Size must be >= " + minProperties, node);
            } else if (maxProperties != null && size > maxProperties) {
                context.addError("maxProperties", "Size must be <= " + maxProperties, node);
            }
            if (context.shouldAbort()) return;

            if (required != null) {
                for (String name : required) {
                    if (!NodeWalker.containsInObject(node, name)) {
                        context.addError("required", "Missing " + name, node);
                    }
                }
            }
            if (context.shouldAbort()) return;

            for (Map.Entry<String, Object> entry : NodeWalker.entrySetInObject(node)) {
                String name = entry.getKey();
                SchemaItem schemaItem = properties.get(name);
                if (schemaItem != null) {
                    context.pushPathToken(new PathToken.Name(name));
                    schemaItem.validate(entry.getValue(), context);
                    context.popPathToken();
                } else if (additionalProperties != null) {
                    context.pushPathToken(new PathToken.Name(name));
                    additionalProperties.validate(entry.getValue(), context);
                    context.popPathToken();
                }
                if (context.shouldAbort()) return;
            }
        }
    }

    // Array
    public static class ArraySchema extends SchemaItem {
        private final SchemaItem items;
        private final Integer minItems;
        private final Integer maxItems;
        private final Boolean uniqueItems;

        public ArraySchema(SchemaItem items, Integer minItems, Integer maxItems, Boolean uniqueItems) {
            this.items = items;
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.uniqueItems = uniqueItems;
        }

        @Override
        protected void validateItem(Object node, ValidationContext context) {
            NodeType nt = NodeType.of(node);
            if (!nt.isArray()) {
                context.addError("type", "Not a array", node);
                return;
            }

            int size = NodeWalker.sizeInArray(node);
            if (minItems != null && size < minItems) {
                context.addError("minItems", "Size must be >= " + minItems, node);
            } else if (maxItems != null && size > maxItems) {
                context.addError("maxItems", "Size must be <= " + maxItems, node);
            }
            if (context.shouldAbort()) return;

            for (int i = 0; i < size; i++) {
                Object v = NodeWalker.getInArray(node, i);
                if (items != null) {
                    context.pushPathToken(new PathToken.Index(i));
                    items.validate(v, context);
                    context.popPathToken();
                    if (context.shouldAbort()) return;
                }
            }

            if (uniqueItems != null && uniqueItems) {
                if (size <= 1) return;
                Set<NodeKey> seen = new HashSet<>(size * 2);
                for (int i = 0; i < size; i++) {
                    Object v = NodeWalker.getInArray(node, i);
                    NodeKey key = new NodeKey(v);
                    if (!seen.add(key)) {
                        context.addError("uniqueItems", "Array items are not unique", v);
                        break;
                    }
                }
            }
        }
    }

    // AllOf
    public static class AllOfSchema extends SchemaItem {
        private final List<SchemaItem> schemas;
        public AllOfSchema(List<SchemaItem> schemas) {
            this.schemas = schemas;
        }

        @Override
        public void validateItem(Object node, ValidationContext context) {
            if (schemas != null) {
                for (SchemaItem schema : schemas) {
                    schema.validate(node, context);
                    if (context.shouldAbort()) return;
                }
            }
        }
    }

    // AnyOf
    public static class AnyOfSchema extends SchemaItem {
        private final List<SchemaItem> schemas;
        public AnyOfSchema(List<SchemaItem> schemas) {
            this.schemas = schemas;
        }

        @Override
        public void validateItem(Object node, ValidationContext context) {
            if (schemas != null) {
                for (SchemaItem schema : schemas) {
                    if (schema.matches(node)) return;
                }
                context.addError("anyOf", "Must match at least one schema", node);
            }
        }
    }

    // OneOf
    public static class OneOfSchema extends SchemaItem {
        private final List<SchemaItem> schemas;
        public OneOfSchema(List<SchemaItem> schemas) {
            this.schemas = schemas;
        }

        @Override
        public void validateItem(Object node, ValidationContext context) {
            if (schemas != null) {
                int matches = 0;
                for (SchemaItem schema : schemas) {
                    if (schema.matches(node)) matches++;
                }
                if (matches != 1) {
                    context.addError("oneOf", "Must match exactly one schema", node);
                }
            }
        }
    }

    // Not
    public static class NotSchema extends SchemaItem {
        private final SchemaItem schema;
        public NotSchema(SchemaItem schema) {
            this.schema = schema;
        }

        @Override
        public void validateItem(Object node, ValidationContext context) {
            if (schema.matches(node)) {
                context.addError("not", "Must not match schema", node);
            }
        }
    }


}
