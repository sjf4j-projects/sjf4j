package org.sjf4j.node;

import org.sjf4j.util.Strings;


/**
 * Built-in naming strategies for JSON-facing property names.
 */
public enum NamingStrategy {
    IDENTITY,
    SNAKE_CASE;

    public String translate(String propertyName) {
        return this == IDENTITY ? propertyName : Strings.toSnakeCase(propertyName);
    }
}
