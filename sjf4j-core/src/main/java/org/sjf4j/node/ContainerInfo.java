package org.sjf4j.node;

import org.sjf4j.NodeKind;
import org.sjf4j.exception.BindingException;
import org.sjf4j.external.ExternalNode;

import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;

/**
 * Cached classification and optional construction metadata for a Map/List/Set.
 *
 * <p>A null constructor means the type is still a valid container, but cannot
 * be instantiated using the standard zero-argument construction path.
 */
public final class ContainerInfo {
    public final Class<?> clazz;
    public final NodeKind kind;
    public final MethodHandle noArgsCtorHandle;
    public final Supplier<?> noArgsCtorLambda;
    /** Optional native construction/copy support when the Java container cannot be instantiated. */
    public final ExternalNode<Object> externalNode;

    /**
     * Creates metadata for a supported container type.
     */
    public ContainerInfo(Class<?> clazz, NodeKind kind,
                         MethodHandle noArgsCtorHandle, Supplier<?> noArgsCtorLambda) {
        this(clazz, kind, noArgsCtorHandle, noArgsCtorLambda, null);
    }

    @SuppressWarnings("unchecked")
    public ContainerInfo(Class<?> clazz, NodeKind kind,
                         MethodHandle noArgsCtorHandle, Supplier<?> noArgsCtorLambda,
                         ExternalNode<?> externalNode) {
        if (kind != NodeKind.OBJECT_MAP && kind != NodeKind.ARRAY_LIST && kind != NodeKind.ARRAY_SET) {
            throw new BindingException("invalid container kind '" + kind + "' for " + clazz.getName());
        }
        this.clazz = clazz;
        this.kind = kind;
        this.noArgsCtorHandle = noArgsCtorHandle;
        this.noArgsCtorLambda = noArgsCtorLambda;
        this.externalNode = (ExternalNode<Object>) externalNode;
    }

    /**
     * Creates a container instance using its registered construction path.
     */
    public Object newContainer() {
        if (noArgsCtorLambda != null) {
            return noArgsCtorLambda.get();
        }
        if (noArgsCtorHandle != null) {
            try {
                return noArgsCtorHandle.invoke();
            } catch (BindingException e) {
                throw e;
            } catch (Throwable e) {
                throw new BindingException("failed to create container instance of " + clazz.getName(), e);
            }
        }
        throw new BindingException("failed to create container instance of " + clazz.getName());
    }
}
