/**
 * Cached type and binding metadata for OBNT.
 *
 * <p>{@link org.sjf4j.JsonType} describes JSON-semantic shape: object node,
 * array node, or value node. {@link org.sjf4j.NodeKind} is the lower-level
 * runtime representation and dispatch classification. {@link TypeInfo} holds
 * cached metadata for a Java class, and {@link TypeRegistry} analyzes classes
 * and creates that metadata for readers, writers, conversion, copying, and
 * traversal.</p>
 *
 * <p>This package holds metadata for object bindings, containers, polymorphic
 * types, and values. Value codecs and their registration APIs live in
 * {@link org.sjf4j.value}. Most application code does not need to use the
 * metadata APIs directly for ordinary JSON reads and writes.</p>
 */
package org.sjf4j.meta;
