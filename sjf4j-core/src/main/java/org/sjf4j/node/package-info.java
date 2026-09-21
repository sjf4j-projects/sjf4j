/**
 * Type metadata and value-codec extensions for OBNT.
 *
 * <p>{@link org.sjf4j.JsonType} describes JSON-level shape ({@code object},
 * {@code array}, or scalar), while {@link org.sjf4j.NodeKind} provides the more specific
 * runtime classification used for dispatch. {@link TypeInfo} holds the cached
 * metadata for a Java class, including object, container, polymorphic, and
 * value-codec metadata. {@link TypeRegistry} creates and caches that metadata
 * for framework readers, writers, conversion, copying, and traversal.</p>
 *
 * <p>{@link ValueCodec codecs} map domain values to raw JSON-compatible values;
 * object metadata describes structural binding for other classes. These APIs
 * support the framework's metadata and extension mechanisms and are generally
 * not needed for ordinary JSON reads and writes.</p>
 */
package org.sjf4j.node;

import org.sjf4j.value.ValueCodec;
