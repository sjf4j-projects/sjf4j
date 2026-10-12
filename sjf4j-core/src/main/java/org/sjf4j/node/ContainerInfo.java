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
 * The optional external adapter supports complete-node construction in mapping
 * only; streaming binding requires a directly populatable Java container.
 */
public final class ContainerInfo {
    public final Class<?> clazz;
    public final NodeKind kind;
    public final MethodHandle noArgsCtorHandle;
    public final Supplier<?> noArgsCtorLambda;
    /** Optional mapping/copy support for native containers; not a streaming binding constructor. */
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
     * Creates a Java container for direct put/add population.
     * The external adapter cannot replace this constructor: it builds a
     * complete target from an existing source node in NodeMapper.
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
