package org.sjf4j.processor.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Semantic output of SchemaCompiler. No Java source code, temporary names or
 * fallback field names appear here. Source generation belongs to SchemaEmitter.
 */
abstract class SchemaCheck {
    enum Kind { TYPE, OBJECT, STRING, NUMBER, ARRAY, ONE_OF, GENERIC_ONE_OF, ALL_OF, ANY_OF, NOT, IF_THEN_ELSE, LITERAL, ALL, PASS, REJECT, FALLBACK }

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

    /** Disjoint Java subtypes compiled directly, with one full-schema fallback for unknown types. */
    static final class OneOfCheck extends SchemaCheck {
        final String uri;
        final List<Subtype> subtypes;
        OneOfCheck(String uri, Diagnostic diagnostic, List<Subtype> subtypes) {
            super(Kind.ONE_OF, diagnostic);
            this.uri = uri;
            this.subtypes = Collections.unmodifiableList(new ArrayList<Subtype>(subtypes));
        }
        @Override boolean hasFallback() { return true; }

        static final class Subtype {
            final String type;
            final ObjectCheck check;
            Subtype(String type, ObjectCheck check) {
                this.type = type;
                this.check = check;
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
        final boolean typeRequired;
        final boolean additionalPropertiesFalse;
        final Integer minProperties;
        final Integer maxProperties;
        final Diagnostic typeDiagnostic;
        final Diagnostic minPropertiesDiagnostic;
        final Diagnostic maxPropertiesDiagnostic;
        final Diagnostic requiredDiagnostic;
        final Diagnostic additionalPropertiesDiagnostic;
        final int staticPropertyCount;
        final List<String> allowedNames;
        final List<Required> required;
        final List<PropertyCheck> properties;

        ObjectCheck(boolean dynamic, boolean typeRequired,
                    boolean additionalPropertiesFalse,
                    Integer minProperties, Integer maxProperties, int staticPropertyCount,
                    List<String> allowedNames, List<Required> required,
                    List<PropertyCheck> properties,
                    Diagnostic typeDiagnostic, Diagnostic minPropertiesDiagnostic,
                    Diagnostic maxPropertiesDiagnostic, Diagnostic requiredDiagnostic,
                    Diagnostic additionalPropertiesDiagnostic) {
            super(Kind.OBJECT);
            this.dynamic = dynamic;
            this.typeRequired = typeRequired;
            this.additionalPropertiesFalse = additionalPropertiesFalse;
            this.minProperties = minProperties;
            this.maxProperties = maxProperties;
            this.typeDiagnostic = typeDiagnostic;
            this.minPropertiesDiagnostic = minPropertiesDiagnostic;
            this.maxPropertiesDiagnostic = maxPropertiesDiagnostic;
            this.requiredDiagnostic = requiredDiagnostic;
            this.additionalPropertiesDiagnostic = additionalPropertiesDiagnostic;
            this.staticPropertyCount = staticPropertyCount;
            this.allowedNames = Collections.unmodifiableList(new ArrayList<String>(allowedNames));
            this.required = Collections.unmodifiableList(new ArrayList<Required>(required));
            this.properties = Collections.unmodifiableList(new ArrayList<PropertyCheck>(properties));
        }

        @Override
        boolean hasFallback() {
            for (PropertyCheck property : properties) {
                if (property.check.hasFallback()) return true;
            }
            return false;
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

    static final class PropertyCheck {
        final String name;
        final String staticAccess;
        final SchemaValue value;
        final SchemaCheck check;

        PropertyCheck(String name, String staticAccess, SchemaValue value, SchemaCheck check) {
            this.name = name;
            this.staticAccess = staticAccess;
            this.value = value;
            this.check = check;
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
        final SchemaCheck items;
        final SchemaValue itemValue;
        final Diagnostic typeDiagnostic;
        final Diagnostic minItemsDiagnostic;
        final Diagnostic maxItemsDiagnostic;

        ArrayCheck(Integer minItems, Integer maxItems, SchemaCheck items, SchemaValue itemValue,
                   Diagnostic typeDiagnostic, Diagnostic minItemsDiagnostic,
                   Diagnostic maxItemsDiagnostic) {
            super(Kind.ARRAY);
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.items = items;
            this.itemValue = itemValue;
            this.typeDiagnostic = typeDiagnostic;
            this.minItemsDiagnostic = minItemsDiagnostic;
            this.maxItemsDiagnostic = maxItemsDiagnostic;
        }

        @Override
        boolean hasFallback() {
            return items != null && items.hasFallback();
        }
    }
}
