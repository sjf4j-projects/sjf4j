package org.sjf4j.value;

/**
 * ServiceLoader marker for a concrete class declaring static value codecs.
 *
 * <p>This interface is intentionally empty. A provider must be a concrete
 * class with a public no-argument constructor.
 * Its codecs are declared through static fields or factory methods; member
 * {@code @NodeValue} annotations are optional for named formats.
 * {@link ValueRegistry#registerAnnotated(Class)} is used for both explicit
 * registration and ServiceLoader discovery.
 */
public interface ValueCodecProvider {
}
