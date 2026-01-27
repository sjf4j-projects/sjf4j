package org.sjf4j.schema;


import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.node.NodeType;
import org.sjf4j.node.Nodes;


public interface JsonSchema {

    void compile(SchemaStore outer);
    ValidationResult validate(Object node, ValidationOptions options);

    /// Static
    static JsonSchema fromJson(String json) {
        Object node = Sjf4j.fromJson(json) ;
        return fromNode(node);
    }

    static JsonSchema fromNode(Object node) {
        if (node == null) return null;
        NodeType nt = NodeType.of(node);
        if (nt.isBoolean()) return ((Boolean) node) ? BooleanSchema.TRUE : BooleanSchema.FALSE;
        if (nt.isObject()) return Nodes.as(node, ObjectSchema.class);
        throw new SchemaException("Invalid JSON Schema: expected object or boolean, but got " + nt);
    }

    /// Default
    default void compile() {
        compile(null);
    }

    default void validateOrThrow(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAILFAST);
        if (!result.isValid()) throw new ValidationException(result);
    }

    default boolean isValid(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAILFAST);
        return result.isValid();
    }

    default ValidationResult validateFailFast(Object node) {
        return validate(node, ValidationOptions.FAILFAST);
    }

    default ValidationResult validate(Object node) {
        return validate(node, ValidationOptions.DEFAULT);
    }


}
