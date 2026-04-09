package org.sjf4j.schema;


import org.sjf4j.JsonType;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.PathSegment;


/**
 * Base interface for JSON Schema implementations.
 *
 * <p>Schema instances are compiled against a {@link SchemaStore} and then
 * evaluated against JSON nodes via {@link #validate} or {@link #evaluate}.
 */
@AnyOf({
        @AnyOf.Mapping(BooleanSchema.class),
        @AnyOf.Mapping(ObjectSchema.class),
})
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
    default void requireValid(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAILFAST);
        if (!result.isValid()) throw new ValidationException(result);
    }

    /**
     * Returns true when the node is valid.
     */
    default boolean isValid(Object node) {
        ValidationResult result = validate(node, ValidationOptions.FAILFAST);
        return result.isValid();
    }

    /**
     * Validates with fail-fast options.
     */
    default ValidationResult validateFailFast(Object node) {
        return validate(node, ValidationOptions.FAILFAST);
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
     */
    static JsonSchema fromJson(String json) {
        return Sjf4j.global().fromJson(json, JsonSchema.class);
    }

    /**
     * Creates a schema instance from a parsed JSON node.
     */
    static JsonSchema fromNode(Object node) {
        return Sjf4j.global().fromNode(node, JsonSchema.class);
    }

}
