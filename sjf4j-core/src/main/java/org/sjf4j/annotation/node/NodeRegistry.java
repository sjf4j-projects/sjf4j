package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an interface containing value codecs and external node adapters.
 * Public static final fields declaring {@code ValueCodec<V, R>} or
 * {@code ExternalNode<N>} are recognized by their generic types. Static factory
 * methods are not registry declarations. {@link NodeValue} is optional for
 * codec formats.
 *
 * <p>For example, given an {@code OrderId} value type:
 * <pre>{@code
 * @NodeRegistry
 * interface AppCodecs {
 *     ValueCodec<OrderId, String> ORDER_ID =
 *             new ValueCodec.SimpleValueCodec<>(
 *                     OrderId.class, String.class, id -> id.value, OrderId::new);
 *
 *     ExternalNode<MyJsonNode> MY_NODE = new MyJsonNodeAdapter();
 *
 *     @NodeValue(format = "uppercase")
 *     ValueCodec<OrderId, String> UPPERCASE =
 *             new ValueCodec.SimpleValueCodec<>(
 *                 OrderId.class, String.class,
 *                 id -> id.value.toUpperCase(java.util.Locale.ROOT), OrderId::new);
 * }
 * }</pre>
 *
 * <p>The annotation processor discovers registries in the current compilation;
 * precompiled registries must be named via {@code registries} on
 * {@link org.sjf4j.annotation.binding.CompiledBinder @CompiledBinder} or
 * {@link org.sjf4j.annotation.mapping.CompiledMapper @CompiledMapper},
 * {@link org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper @CompiledJdbcMapper}
 * or {@link org.sjf4j.annotation.schema.CompiledValidator @CompiledValidator}.
 * <p>Compile-time discovery reads field types without initializing registry
 * fields and does not perform runtime registration. Call
 * {@link org.sjf4j.value.ValueRegistry#registerAnnotated(Class)} during
 * application initialization when runtime ValueCodec resolution is needed,
 * before type metadata is analyzed. Generated JDBC mappers call selected
 * registry codec fields directly and do not require this runtime registration.
 * External node fields are compile-time type declarations only; runtime
 * adapters are provided independently through
 * {@link org.sjf4j.external.ExternalProvider} and {@link java.util.ServiceLoader}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeRegistry {
}
