package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an interface containing value codecs and external node adapters.
 * Public static final fields and public static no-argument factory methods
 * returning {@code ValueCodec<V, R>} or {@code ExternalNode<N>} are recognized
 * by their generic types. {@link NodeValue} is optional for codec formats.
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
 *     static ValueCodec<OrderId, String> uppercase() {
 *         return new ValueCodec.SimpleValueCodec<>(
 *                 OrderId.class, String.class,
 *                 id -> id.value.toUpperCase(java.util.Locale.ROOT), OrderId::new);
 *     }
 * }
 * }</pre>
 *
 * <p>The annotation processor discovers registries in the current compilation;
 * precompiled registries must be named via {@code registries} on
 * {@link org.sjf4j.annotation.binding.CompiledBinder @CompiledBinder} or
 * {@link org.sjf4j.annotation.mapping.CompiledMapper @CompiledMapper}.
 * Compile-time discovery does not register adapters at runtime: call
 * {@link org.sjf4j.NodeRegistries#register(Class)
 * NodeRegistries.register(AppCodecs.class)} during initialization,
 * before metadata for the affected types is analyzed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeRegistry {
}
