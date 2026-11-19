package org.sjf4j.value;

/**
 * ServiceLoader marker for a class annotated with {@code @ValueCodecRegistry}.
 *
 * <p>The provider class declares codecs through static fields or factory methods;
 * {@code @NodeValue} is optional for named formats. It must have an accessible no-argument
 * constructor for ServiceLoader. ValueRegistry reads those declarations
 * using the same registration and validation path as registerAnnotated().
 */
public interface ValueCodecProvider {
}
