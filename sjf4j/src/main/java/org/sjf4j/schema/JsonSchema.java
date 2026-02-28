package org.sjf4j.schema;


import org.sjf4j.JsonType;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.PathSegment;


/**
 * Base interface for JSON Schema implementations.
 *
 * <p>Schema instances are compiled against a {@link SchemaStore} and then
 * evaluated against JSON nodes via {@link #validate} or {@link #evaluate}.
 */
public interface JsonSchema {

    /**
     * Compiles this schema into runtime evaluators.
     * <p>
     * Compilation resolves references, anchors, and keyword evaluators against
     * the provided store context.
     */
    void compile(SchemaStore outer);
    /**
     * Validates a node with the given options and returns structured result.
     */
    ValidationResult validate(Object node, ValidationOptions options);
    /**
     * Evaluates this schema against an instance during validation.
     * <p>
     * Implementations should append messages into {@link ValidationContext} and
     * return whether the current schema branch succeeds.
     */
    boolean evaluate(InstancedNode instance, PathSegment ps, ValidationContext ctx);

    /// Default

    /**
     * Compiles this schema using default resolution context.
     */
    default void compile() {
        compile(null);
    }

    /**
     * Validates and throws if invalid.
     */
    default void validateOrThrow(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAIL_FAST);
        if (!result.isValid()) throw new ValidationException(result);
    }

    /**
     * Returns true when the node is valid.
     */
    default boolean isValid(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAIL_FAST);
        return result.isValid();
    }

    /**
     * Validates with fail-fast options.
     */
    default ValidationResult validateFailFast(Object node) {
        return validate(node, ValidationOptions.FAIL_FAST);
    }

    /**
     * Validates with default options.
     */
    default ValidationResult validate(Object node) {
        return validate(node, ValidationOptions.DEFAULT);
    }

    /// Static

    /**
     * Parses JSON text and creates a schema instance.
     * <p>
     * Valid schema roots are object schemas and boolean schemas.
     */
    static JsonSchema fromJson(String json) {
        Object node = Sjf4j.fromJson(json) ;
        return fromNode(node);
    }

    /**
     * Creates a schema instance from a parsed JSON node.
     *
     * @throws SchemaException when root node is not object/boolean
     */
    static JsonSchema fromNode(Object node) {
        if (node == null) return null;
        JsonType jt = JsonType.of(node);
        if (jt.isBoolean()) return Nodes.toBoolean(node) ? BooleanSchema.TRUE : BooleanSchema.FALSE;
        if (jt.isObject()) return Nodes.as(node, ObjectSchema.class);
        throw new SchemaException("Invalid JSON Schema: expected object or boolean, but was " + jt);
    }

}
