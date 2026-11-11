package org.sjf4j.annotation.mapping;

/**
 * Controls how a compiled mapper adapts a value whose Java type is not known
 * at compile time (for example, a value read as {@link Object} from a Map or
 * JsonObject) to a statically declared target type.
 *
 * <p>This policy does not change conversions whose source type is statically
 * known, direct assignments, or explicitly selected mapping methods. It is
 * applied independently at nested raw-value conversion points.</p>
 */
public enum RawPolicy {

    /**
     * Uses Java casts and, where needed, primitive unboxing. No OBNT scalar
     * coercion or structural conversion is performed at this conversion point.
     *
     * <p>Generic container casts are unchecked because Java erases type
     * parameters: casting to {@code Map<String, Item>} verifies only the raw
     * {@code Map} type and does not inspect keys or values. A null reference
     * remains null unless primitive unboxing is required.</p>
     */
    CAST,

    /**
     * Uses strict OBNT adaptation, preserving the current CompiledMapper
     * behavior. For example, Number-to-Number conversions are supported, but
     * String-to-Number coercion is rejected.
     */
    STRICT,

    /**
     * Uses OBNT lenient scalar adaptation for raw values, including numeric
     * strings and supported boolean spellings.
     *
     * <p>Leniency is applied at each compiled raw scalar conversion point;
     * it is not an unconditional recursive conversion of arbitrary objects.
     * Other mappings continue to use their normal compiled structural rules.</p>
     */
    LENIENT
}
