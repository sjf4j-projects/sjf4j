/**
 * Type metadata and value-binding extensions for OBNT.
 *
 * <p>{@link org.sjf4j.JsonType} describes JSON-semantic shape: object node,
 * array node, or value node. {@link org.sjf4j.NodeKind} is the lower-level
 * runtime representation and dispatch classification. {@link TypeInfo} holds
 * cached metadata for a Java class, and {@link TypeRegistry} analyzes classes
 * and creates that metadata for readers, writers, conversion, copying, and
 * traversal.</p>
 *
 * <p>A {@link org.sjf4j.annotation.node.NodeValue @NodeValue} value binding may
 * use {@link ValueCodec} or annotated conversion methods. Facade writes encode
 * and facade reads decode raw OBNT representations; schema validation encodes
 * only. A representation may have object node, array node, or value node shape;
 * the binding is responsible for its contents. These APIs support metadata and
 * extension use cases and are generally not needed for ordinary JSON reads and writes.</p>
 */
package org.sjf4j.node;

import org.sjf4j.value.ValueCodec;
