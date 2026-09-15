package org.sjf4j.annotation.node;

/**
 * Type-level POJO property discovery policy.
 * <p>
 * A property can be discovered from bean-style accessors, from a Java field, or
 * from both together when they belong to the same implicit Java property name.
 * SJF4J groups candidates by implicit property family first, then produces one
 * JSON-facing property from that family.
 * <p>
 * Bean candidates are JavaBean-style methods such as {@code getX()},
 * {@code isX()}, {@code setX(...)} and record component accessors.
 * Field candidates are declared Java fields that survive the normal ignore
 * checks ({@code static}, {@code transient}, synthetic, {@code @NodeIgnore}).
 * <p>
 * The selected strategy answers two questions:
 * <ol>
 *     <li>Which candidate sources are allowed to create a property family.</li>
 *     <li>When both bean and field sources exist for the same family, which one
 *     provides reader/writer access first and which one only fills gaps.</li>
 * </ol>
 * <p>
 * Framework default is {@link #BEAN_FIELD}.
 */
public enum PropertyStrategy {
    /**
     * Discovers bean-style properties only.
     * <p>
     * Fields do not participate in property discovery, even when they are public.
     * A property exists only when discovered from bean-style accessors or record
     * components.
     */
    BEAN_ONLY,

    /**
     * Discovers declared fields only.
     * <p>
     * All eligible declared fields participate, including non-public fields.
     * Bean-style methods do not create properties and do not contribute fallback
     * reader/writer access.
     */
    FIELD_ONLY,

    /**
     * Bean properties first, then field fallback.
     * <p>
     * This is the framework default and is intentionally Jackson-like.
     * Bean candidates create the primary property families. Field candidates then
     * join the same family when they match the same implicit property, instead of
     * creating a duplicate JSON property.
     * <p>
     * Reader/writer priority is bean first, field second. Field access is used
     * only when the bean side does not provide the needed getter or setter.
     * <p>
     * For default auto-discovery, secondary field discovery is narrower than in
     * field-first modes:
     * <ul>
     *     <li>public fields participate automatically</li>
     *     <li>non-public fields participate only when explicitly bound by
     *     property metadata such as {@code @NodeProperty}</li>
     * </ul>
     */
    BEAN_FIELD,

    /**
     * Field properties first, then bean fallback.
     * <p>
     * All eligible declared fields participate, including non-public fields.
     * Bean-style methods may still join the same implicit property family, but
     * only as secondary access paths.
     * <p>
     * Reader/writer priority is field first, bean second. Bean access is used
     * only when the field side does not provide the needed getter or setter.
     */
    FIELD_BEAN,
}
