/**
 * Direct structural conversion between OBNT representations.
 *
 * <p>{@link NodeMapper} converts maps, lists, arrays, POJOs, JOJOs, and
 * JAJOs to a requested target type without parsing or writing a format. It
 * honors generic target types, {@link org.sjf4j.annotation.node.OneOf
 * @OneOf} mappings, and configured {@link org.sjf4j.value.ValueCodec value
 * codecs}. Use a {@code Binder} for format input and output.</p>
 */
package org.sjf4j.mapping;
