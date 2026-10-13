/**
 * Codecs and registration for logical value nodes.
 *
 * <p>{@link ValueCodec} converts a Java value to and from its raw node
 * representation. {@link ValueRegistry} resolves built-in, registered, and
 * {@link org.sjf4j.annotation.node.NodeValue @NodeValue}-declared codecs;
 * {@link PatternedValueCodec} supports formatted values. Type and property
 * metadata that select these codecs is cached in {@link org.sjf4j.meta}.</p>
 */
package org.sjf4j.value;
