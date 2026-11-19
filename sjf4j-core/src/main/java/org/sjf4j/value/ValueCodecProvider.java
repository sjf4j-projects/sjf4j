package org.sjf4j.value;

/**
 * ServiceLoader marker for a class annotated with {@code @ValueCodecRegistry}.
 *
 * <p>The provider class declares codecs through {@code @NodeValue} fields
 * or static no-argument methods. It must have an accessible no-argument
 * constructor for ServiceLoader. ValueRegistry reads those declarations
 * using the same registration and validation path as registerAnnotated().
 */
public interface ValueCodecProvider {
}
