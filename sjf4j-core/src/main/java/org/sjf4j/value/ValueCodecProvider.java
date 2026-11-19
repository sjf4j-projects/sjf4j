package org.sjf4j.value;

/**
 * ServiceLoader marker for a class annotated with {@code @ValueCodecRegistry}.
 *
 * <p>This interface is intentionally empty. A provider must be a concrete
 * {@code @ValueCodecRegistry} class with a public no-argument constructor.
 * Its codecs are declared through static fields or factory methods; member
 * {@code @NodeValue} annotations are optional for named formats.
 * {@link ValueRegistry#registerAnnotated(Class)} is used for both explicit
 * registration and ServiceLoader discovery.
 */
public interface ValueCodecProvider {
}
