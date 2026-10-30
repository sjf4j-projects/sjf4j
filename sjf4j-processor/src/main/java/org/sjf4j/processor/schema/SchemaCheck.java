package org.sjf4j.processor.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Semantic output of SchemaCompiler. No Java source code, temporary names or
 * fallback field names appear here. Source generation belongs to SchemaEmitter.
 */
abstract class SchemaCheck {
    enum Kind { OBJECT, STRING, NUMBER, ARRAY, PASS, FALLBACK }

    final Kind kind;

    SchemaCheck(Kind kind) {
        this.kind = kind;
    }

    boolean hasFallback() {
        return kind == Kind.FALLBACK;
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

        StringCheck(Integer minLength, Integer maxLength, List<String> choices) {
            super(Kind.STRING);
            this.minLength = minLength;
            this.maxLength = maxLength;
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
