package org.sjf4j.processor.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Semantic output of SchemaCompiler. No Java source code, temporary names or
 * fallback field names appear here. Source generation belongs to SchemaEmitter.
 */
abstract class SchemaCheck {
    enum Kind { TYPE, SHAPE_GUARD, OBJECT, STRING, NUMBER, ARRAY, ONE_OF, GENERIC_ONE_OF, ALL_OF, ANY_OF, NOT, IF_THEN_ELSE, LITERAL, ALL, PASS, REJECT, FALLBACK }

    final Kind kind;
    final Diagnostic diagnostic;

    SchemaCheck(Kind kind) {
        this(kind, null);
    }

    SchemaCheck(Kind kind, Diagnostic diagnostic) {
        this.kind = kind;
        this.diagnostic = diagnostic;
    }

    /**
     * Source location for one assertion represented by a compiled check.
     *
     * <p>{@code schemaUri} identifies the resource without its fragment.
     * {@code keywordPointer} is the decoded JSON Pointer within that resource,
     * including the keyword token (or the schema location for boolean false).
     */
    static final class Diagnostic {
        final String keyword;
        final String schemaUri;
        final String keywordPointer;

        Diagnostic(String keyword, String schemaUri, String keywordPointer) {
            this.keyword = keyword;
            this.schemaUri = schemaUri;
            this.keywordPointer = keywordPointer;
        }
    }

    boolean hasFallback() {
        return kind == Kind.FALLBACK;
    }

    /** Complete typed oneOf branch predicates, with optional runtime-guarded dispatch. */
    static final class OneOfCheck extends SchemaCheck {
        final String uri;
        final List<Subtype> subtypes;
        final GenericOneOfCheck generic;
        OneOfCheck(String uri, Diagnostic diagnostic, List<Subtype> subtypes,
                   GenericOneOfCheck generic) {
            super(Kind.ONE_OF, diagnostic);
            this.uri = uri;
            this.subtypes = Collections.unmodifiableList(new ArrayList<Subtype>(subtypes));
            this.generic = generic;
        }
        @Override boolean hasFallback() { return generic == null; }

        static final class Subtype {
            final String type;
            final SchemaValue value;
            final GenericOneOfCheck check;
            // Present only if the schema proves mutually exclusive discriminator
            // values and a direct, JSON-visible accessor is available.
            final String discriminatorAccess;
            final String discriminatorValue;
            final int guardedBranch;

            Subtype(String type, SchemaValue value, GenericOneOfCheck check,
                    String discriminatorAccess, String discriminatorValue, int guardedBranch) {
                this.type = type;
                this.value = value;
                this.check = check;
                this.discriminatorAccess = discriminatorAccess;
                this.discriminatorValue = discriminatorValue;
                this.guardedBranch = guardedBranch;
            }
        }
    }

    /** General oneOf: count matching subschemas, including overlapping branches. */
    static final class GenericOneOfCheck extends SchemaCheck {
        final List<SchemaCheck> branches;

        GenericOneOfCheck(Diagnostic diagnostic, List<SchemaCheck> branches) {
            super(Kind.GENERIC_ONE_OF, diagnostic);
            this.branches = Collections.unmodifiableList(new ArrayList<SchemaCheck>(branches));
        }

        @Override boolean hasFallback() {
            for (SchemaCheck branch : branches) if (branch.hasFallback()) return true;
            return false;
        }
    }

    /** Logical branches, evaluated as independent boolean predicates. */
    static final class ApplicatorCheck extends SchemaCheck {
        final List<SchemaCheck> branches;

        ApplicatorCheck(Kind kind, Diagnostic diagnostic, List<SchemaCheck> branches) {
            super(kind, diagnostic);
            if (kind != Kind.ALL_OF && kind != Kind.ANY_OF && kind != Kind.NOT)
                throw new IllegalArgumentException("Unsupported applicator: " + kind);
            this.branches = Collections.unmodifiableList(new ArrayList<SchemaCheck>(branches));
        }

        @Override boolean hasFallback() {
            for (SchemaCheck branch : branches) if (branch.hasFallback()) return true;
            return false;
        }
    }

    /** The if predicate selects then or else; it is not itself an assertion. */
    static final class ConditionalCheck extends SchemaCheck {
        final SchemaCheck condition;
        final SchemaCheck thenCheck;
        final SchemaCheck elseCheck;

        ConditionalCheck(SchemaCheck condition, SchemaCheck thenCheck, SchemaCheck elseCheck) {
            super(Kind.IF_THEN_ELSE);
            this.condition = condition;
            this.thenCheck = thenCheck;
            this.elseCheck = elseCheck;
        }

        @Override boolean hasFallback() {
            return condition.hasFallback()
                    || (thenCheck != null && thenCheck.hasFallback())
                    || (elseCheck != null && elseCheck.hasFallback());
        }
    }

    /** A JSON Schema single or union type assertion, independent of extra keywords. */
    static final class TypeCheck extends SchemaCheck {
        final List<String> types;

        TypeCheck(List<String> types, Diagnostic diagnostic) {
            super(Kind.TYPE, diagnostic);
            this.types = Collections.unmodifiableList(new ArrayList<String>(types));
        }
    }

    /**
     * Each JSON Schema keyword applies to its own instance JSON shape,
     * regardless of whether a separate type assertion exists.
     * This is independent of whether the Java representation is POJO or OBNT.
     */
    static final class ShapeGuard extends SchemaCheck {
        final String jsonType;
        final SchemaValue value;
        final SchemaCheck child;

        ShapeGuard(String jsonType, SchemaValue value, SchemaCheck child) {
            super(Kind.SHAPE_GUARD);
            this.jsonType = jsonType;
            this.value = value;
            this.child = child;
        }

        @Override boolean hasFallback() { return child.hasFallback(); }
    }

    static final class Reject extends SchemaCheck {
        Reject(Diagnostic diagnostic) { super(Kind.REJECT, diagnostic); }
    }

    /** A scalar JSON Schema const or enum assertion. */
    static final class LiteralCheck extends SchemaCheck {
        final List<Object> values;
        LiteralCheck(List<Object> values, Diagnostic diagnostic) {
            super(Kind.LITERAL, diagnostic);
            this.values = Collections.unmodifiableList(new ArrayList<Object>(values));
        }
    }

    /** Independent keywords under the same schema object must all pass. */
    static final class AllCheck extends SchemaCheck {
        final List<SchemaCheck> checks;
        AllCheck(List<SchemaCheck> checks) {
            super(Kind.ALL);
            this.checks = Collections.unmodifiableList(new ArrayList<SchemaCheck>(checks));
        }
        @Override boolean hasFallback() {
            for (SchemaCheck check : checks) if (check.hasFallback()) return true;
            return false;
        }
    }

    static final class Pass extends SchemaCheck {
        Pass() { super(Kind.PASS); }
    }

    static final class Fallback extends SchemaCheck {
        final String uri;

        Fallback(String uri) {
            super(Kind.FALLBACK);
            this.uri = uri;
        }
    }

    static final class ObjectCheck extends SchemaCheck {
        final boolean dynamic;
        final boolean jsonObject;
        final boolean typeRequired;
        final boolean additionalPropertiesFalse;
        final SchemaCheck additionalPropertiesCheck;
        final List<PatternProperty> patternProperties;
        final SchemaCheck propertyNamesCheck;
        final List<DependentSchema> dependentSchemas;
        final SchemaValue elementValue;
        final Integer minProperties;
        final Integer maxProperties;
        final Diagnostic typeDiagnostic;
        final Diagnostic minPropertiesDiagnostic;
        final Diagnostic maxPropertiesDiagnostic;
        final Diagnostic requiredDiagnostic;
        final Diagnostic dependentRequiredDiagnostic;
        final Diagnostic additionalPropertiesDiagnostic;
        final Diagnostic propertyNamesDiagnostic;
        final int staticPropertyCount;
        final List<String> allowedNames;
        final List<Required> required;
        final List<DependentRequired> dependentRequired;
        final List<PropertyCheck> properties;

        ObjectCheck(boolean dynamic, boolean jsonObject, boolean typeRequired,
                    boolean additionalPropertiesFalse, SchemaCheck additionalPropertiesCheck,
                    List<PatternProperty> patternProperties, SchemaCheck propertyNamesCheck,
                    List<DependentSchema> dependentSchemas, SchemaValue elementValue,
                    Integer minProperties, Integer maxProperties, int staticPropertyCount,
                    List<String> allowedNames, List<Required> required,
                    List<DependentRequired> dependentRequired, List<PropertyCheck> properties,
                    Diagnostic typeDiagnostic, Diagnostic minPropertiesDiagnostic,
                    Diagnostic maxPropertiesDiagnostic, Diagnostic requiredDiagnostic,
                    Diagnostic dependentRequiredDiagnostic, Diagnostic additionalPropertiesDiagnostic,
                    Diagnostic propertyNamesDiagnostic) {
            super(Kind.OBJECT);
            this.dynamic = dynamic;
            this.jsonObject = jsonObject;
            this.typeRequired = typeRequired;
            this.additionalPropertiesFalse = additionalPropertiesFalse;
            this.additionalPropertiesCheck = additionalPropertiesCheck;
            this.patternProperties = Collections.unmodifiableList(new ArrayList<PatternProperty>(patternProperties));
            this.propertyNamesCheck = propertyNamesCheck;
            this.dependentSchemas = Collections.unmodifiableList(new ArrayList<DependentSchema>(dependentSchemas));
            this.elementValue = elementValue;
            this.minProperties = minProperties;
            this.maxProperties = maxProperties;
            this.typeDiagnostic = typeDiagnostic;
            this.minPropertiesDiagnostic = minPropertiesDiagnostic;
            this.maxPropertiesDiagnostic = maxPropertiesDiagnostic;
            this.requiredDiagnostic = requiredDiagnostic;
            this.dependentRequiredDiagnostic = dependentRequiredDiagnostic;
            this.additionalPropertiesDiagnostic = additionalPropertiesDiagnostic;
            this.propertyNamesDiagnostic = propertyNamesDiagnostic;
            this.staticPropertyCount = staticPropertyCount;
            this.allowedNames = Collections.unmodifiableList(new ArrayList<String>(allowedNames));
            this.required = Collections.unmodifiableList(new ArrayList<Required>(required));
            this.dependentRequired = Collections.unmodifiableList(new ArrayList<DependentRequired>(dependentRequired));
            this.properties = Collections.unmodifiableList(new ArrayList<PropertyCheck>(properties));
        }

        @Override
        boolean hasFallback() {
            if (additionalPropertiesCheck != null && additionalPropertiesCheck.hasFallback()) return true;
            if (propertyNamesCheck != null && propertyNamesCheck.hasFallback()) return true;
            for (DependentSchema dependency : dependentSchemas) {
                if (dependency.check.hasFallback()) return true;
            }
            for (PatternProperty pattern : patternProperties) {
                if (pattern.check.hasFallback()) return true;
            }
            for (PropertyCheck property : properties) {
                if (property.check.hasFallback()) return true;
            }
            return false;
        }
    }

    /** Triggers a child schema against the entire containing object, never the property value. */
    static final class DependentSchema {
        final String trigger;
        final SchemaCheck check;

        DependentSchema(String trigger, SchemaCheck check) {
            this.trigger = trigger;
            this.check = check;
        }
    }

    /** Pattern match is a separate assertion and also marks local coverage for additionalProperties. */
    static final class PatternProperty {
        final String javaPattern;
        final SchemaCheck check;

        PatternProperty(String javaPattern, SchemaCheck check) {
            this.javaPattern = javaPattern;
            this.check = check;
        }
    }

    static final class Required {
        final String name;
        final String staticAccess;

        Required(String name, String staticAccess) {
            this.name = name;
            this.staticAccess = staticAccess;
        }
    }

    static final class DependentRequired {
        final String trigger;
        final List<String> dependencies;

        DependentRequired(String trigger, List<String> dependencies) {
            this.trigger = trigger;
            this.dependencies = Collections.unmodifiableList(new ArrayList<String>(dependencies));
        }
    }

    static final class PropertyCheck {
        final String name;
        final String staticAccess;
        final SchemaValue value;
        final SchemaCheck check;
        final String codecOwner;

        PropertyCheck(String name, String staticAccess, SchemaValue value, SchemaCheck check,
                      String codecOwner) {
            this.name = name;
            this.staticAccess = staticAccess;
            this.value = value;
            this.check = check;
            this.codecOwner = codecOwner;
        }
    }

    static final class StringCheck extends SchemaCheck {
        final Integer minLength;
        final Integer maxLength;
        final List<String> choices;
        final String format;
        final String pattern;
        final Diagnostic typeDiagnostic;
        final Diagnostic minLengthDiagnostic;
        final Diagnostic maxLengthDiagnostic;
        final Diagnostic enumDiagnostic;
        final Diagnostic formatDiagnostic;
        final Diagnostic patternDiagnostic;

        StringCheck(Integer minLength, Integer maxLength, List<String> choices, String format,
                    String pattern, Diagnostic typeDiagnostic, Diagnostic minLengthDiagnostic,
                    Diagnostic maxLengthDiagnostic, Diagnostic enumDiagnostic,
                    Diagnostic formatDiagnostic, Diagnostic patternDiagnostic) {
            super(Kind.STRING);
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.format = format;
            this.pattern = pattern;
            this.typeDiagnostic = typeDiagnostic;
            this.minLengthDiagnostic = minLengthDiagnostic;
            this.maxLengthDiagnostic = maxLengthDiagnostic;
            this.enumDiagnostic = enumDiagnostic;
            this.formatDiagnostic = formatDiagnostic;
            this.patternDiagnostic = patternDiagnostic;
            this.choices = choices == null ? null
                    : Collections.unmodifiableList(new ArrayList<String>(choices));
        }
    }

    static final class NumericBound {
        final String keyword;
        final Number limit;
        final Diagnostic diagnostic;

        NumericBound(String keyword, Number limit, Diagnostic diagnostic) {
            this.keyword = keyword;
            this.limit = limit;
            this.diagnostic = diagnostic;
        }
    }

    static final class NumberCheck extends SchemaCheck {
        final boolean integer;
        final List<NumericBound> bounds;
        final Number multipleOf;
        final Diagnostic typeDiagnostic;
        final Diagnostic multipleOfDiagnostic;

        NumberCheck(boolean integer, List<NumericBound> bounds, Number multipleOf,
                    Diagnostic typeDiagnostic, Diagnostic multipleOfDiagnostic) {
            super(Kind.NUMBER);
            this.integer = integer;
            this.multipleOf = multipleOf;
            this.typeDiagnostic = typeDiagnostic;
            this.multipleOfDiagnostic = multipleOfDiagnostic;
            this.bounds = Collections.unmodifiableList(new ArrayList<NumericBound>(bounds));
        }
    }

    static final class ArrayCheck extends SchemaCheck {
        final Integer minItems;
        final Integer maxItems;
        final List<SchemaCheck> prefixItems;
        final SchemaCheck items;
        final SchemaValue itemValue;
        final boolean uniqueItems;
        final Diagnostic typeDiagnostic;
        final Diagnostic minItemsDiagnostic;
        final Diagnostic maxItemsDiagnostic;
        final Diagnostic uniqueItemsDiagnostic;

        ArrayCheck(Integer minItems, Integer maxItems, List<SchemaCheck> prefixItems,
                   SchemaCheck items, SchemaValue itemValue, boolean uniqueItems,
                   Diagnostic typeDiagnostic, Diagnostic minItemsDiagnostic,
                   Diagnostic maxItemsDiagnostic, Diagnostic uniqueItemsDiagnostic) {
            super(Kind.ARRAY);
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.prefixItems = Collections.unmodifiableList(new ArrayList<SchemaCheck>(prefixItems));
            this.items = items;
            this.itemValue = itemValue;
            this.uniqueItems = uniqueItems;
            this.typeDiagnostic = typeDiagnostic;
            this.minItemsDiagnostic = minItemsDiagnostic;
            this.maxItemsDiagnostic = maxItemsDiagnostic;
            this.uniqueItemsDiagnostic = uniqueItemsDiagnostic;
        }

        @Override
        boolean hasFallback() {
            if (items != null && items.hasFallback()) return true;
            for (SchemaCheck prefix : prefixItems) if (prefix.hasFallback()) return true;
            return false;
        }
    }
}
