package org.sjf4j.schema;


import org.sjf4j.JsonType;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.PathSegment;


public interface JsonSchema {

    void compile(SchemaStore outer);
    ValidationResult validate(Object node, ValidationOptions options);
    boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx);

    /// Static
    static JsonSchema fromJson(String json) {
        Object node = Sjf4j.fromJson(json) ;
        return fromNode(node);
    }

    static JsonSchema fromNode(Object node) {
        if (node == null) return null;
        JsonType jt = JsonType.of(node);
        if (jt.isBoolean()) return Nodes.toBoolean(node) ? BooleanSchema.TRUE : BooleanSchema.FALSE;
        if (jt.isObject()) return Nodes.as(node, ObjectSchema.class);
        throw new SchemaException("Invalid JSON Schema: expected object or boolean, but was " + jt);
    }

    /// Default
    default void compile() {
        compile(null);
    }

    default void validateOrThrow(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAIL_FAST);
        if (!result.isValid()) throw new ValidationException(result);
    }

    default boolean isValid(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAIL_FAST);
        return result.isValid();
    }

    default ValidationResult validateFailFast(Object node) {
        return validate(node, ValidationOptions.FAIL_FAST);
    }

    default ValidationResult validate(Object node) {
        return validate(node, ValidationOptions.DEFAULT);
    }


}
