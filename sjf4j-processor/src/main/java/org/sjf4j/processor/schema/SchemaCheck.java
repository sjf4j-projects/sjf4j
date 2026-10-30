package org.sjf4j.processor.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Semantic output of SchemaCompiler. No Java source code, temporary names or
 * fallback field names appear here. Source generation belongs to SchemaEmitter.
 */
abstract class SchemaCheck {
    enum Kind { OBJECT, STRING, NUMBER, ARRAY, ONE_OF, GENERIC_ONE_OF, LITERAL, ALL, PASS, REJECT, FALLBACK }

    final Kind kind;

    SchemaCheck(Kind kind) {
        this.kind = kind;
    }

    boolean hasFallback() {
        return kind == Kind.FALLBACK;
    }

    /** Disjoint Java subtypes compiled directly, with one full-schema fallback for unknown types. */
    static final class OneOfCheck extends SchemaCheck {
        final String uri;
        final List<Subtype> subtypes;
        OneOfCheck(String uri, List<Subtype> subtypes) {
            super(Kind.ONE_OF);
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

        GenericOneOfCheck(List<SchemaCheck> branches) {
            super(Kind.GENERIC_ONE_OF);
            this.branches = Collections.unmodifiableList(new ArrayList<SchemaCheck>(branches));
        }

        @Override boolean hasFallback() {
            for (SchemaCheck branch : branches) if (branch.hasFallback()) return true;
            return false;
        }
    }

    static final class Reject extends SchemaCheck {
        Reject() { super(Kind.REJECT); }
    }

    /** A scalar JSON Schema const or enum assertion. */
    static final class LiteralCheck extends SchemaCheck {
        final List<Object> values;
        LiteralCheck(List<Object> values) {
            super(Kind.LITERAL);
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
        final boolean nullAsMissing;
        final boolean additionalPropertiesFalse;
        final List<String> allowedNames;
        final List<Required> required;
        final List<PropertyCheck> properties;

        ObjectCheck(boolean dynamic, boolean nullAsMissing, boolean additionalPropertiesFalse,
                    List<String> allowedNames, List<Required> required,
                    List<PropertyCheck> properties) {
            super(Kind.OBJECT);
            this.dynamic = dynamic;
            this.nullAsMissing = nullAsMissing;
            this.additionalPropertiesFalse = additionalPropertiesFalse;
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

        StringCheck(Integer minLength, Integer maxLength, List<String> choices, String format) {
            super(Kind.STRING);
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.format = format;
            this.choices = choices == null ? null
                    : Collections.unmodifiableList(new ArrayList<String>(choices));
        }
    }

    static final class NumericBound {
        final String keyword;
        final Number limit;

        NumericBound(String keyword, Number limit) {
            this.keyword = keyword;
            this.limit = limit;
        }
    }

    static final class NumberCheck extends SchemaCheck {
        final boolean integer;
        final List<NumericBound> bounds;

        NumberCheck(boolean integer, List<NumericBound> bounds) {
            super(Kind.NUMBER);
            this.integer = integer;
            this.bounds = Collections.unmodifiableList(new ArrayList<NumericBound>(bounds));
        }
    }

    static final class ArrayCheck extends SchemaCheck {
        final Integer minItems;
        final Integer maxItems;
        final SchemaCheck items;
        final SchemaValue itemValue;

        ArrayCheck(Integer minItems, Integer maxItems, SchemaCheck items, SchemaValue itemValue) {
            super(Kind.ARRAY);
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.items = items;
            this.itemValue = itemValue;
        }

        @Override
        boolean hasFallback() {
            return items != null && items.hasFallback();
        }
    }
}
