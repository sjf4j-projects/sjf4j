/**
 * Runtime traversal and conversion of OBNT node representations.
 *
 * <p>{@link NodeMapper} converts maps, lists, arrays, POJOs, JOJOs, and
 * JAJOs to requested types without parsing or writing a format. It honors
 * generic target types, {@link org.sjf4j.annotation.node.OneOf @OneOf}
 * mappings, and configured {@link org.sjf4j.value.ValueCodec value codecs}.
 * {@link NodeWalker} handles structural access and traversal, while
 * {@link NodeStream} provides stream operations with JSONPath helpers.
 * Use a {@link org.sjf4j.binding.Binder Binder} for format input and output.</p>
 */
package org.sjf4j.node;
