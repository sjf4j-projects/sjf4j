/**
 * JSON Schema model, compilation, registry, and validation runtime support.
 * <p>
 * The package is organized around four concerns:
 * <ul>
 *   <li>{@link org.sjf4j.schema.JsonSchema}, {@link org.sjf4j.schema.ObjectSchema}, and
 *   {@link org.sjf4j.schema.BooleanSchema} model parsed schema documents.</li>
 *   <li>{@link org.sjf4j.schema.SchemaPlanner} compiles one schema resource into a
 *   {@link org.sjf4j.schema.SchemaPlan}, resolving references, anchors, dynamic anchors,
 *   and active vocabularies.</li>
 *   <li>{@link org.sjf4j.schema.SchemaRegistry} indexes root schema resources and caches
 *   compiled plans by absolute resource URI.</li>
 *   <li>{@link org.sjf4j.schema.SchemaPlan}, {@link org.sjf4j.schema.Evaluator}, and
 *   {@link org.sjf4j.schema.ValidationContext} execute validation without mutating the
 *   schema model.</li>
 * </ul>
 * {@link org.sjf4j.schema.SchemaValidator} is a higher-level annotation-driven facade on
 * top of the core model, registry, and runtime layers.
 */
package org.sjf4j.schema;
