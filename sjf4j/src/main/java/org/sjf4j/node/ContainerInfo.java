package org.sjf4j.node;

import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;

import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;

/**
 * Cached construction metadata for a supported map, list, or set type.
 */
public final class ContainerInfo {
    public final Class<?> clazz;
    public final NodeKind kind;
    public final MethodHandle noArgsCtorHandle;
    public final Supplier<?> noArgsCtorLambda;

    /**
     * Creates metadata for a supported container type.
     */
    public ContainerInfo(Class<?> clazz, NodeKind kind,
                         MethodHandle noArgsCtorHandle, Supplier<?> noArgsCtorLambda) {
        if (kind != NodeKind.OBJECT_MAP && kind != NodeKind.ARRAY_LIST && kind != NodeKind.ARRAY_SET) {
            throw new JsonException("invalid container kind '" + kind + "' for " + clazz.getName());
        }
        this.clazz = clazz;
        this.kind = kind;
        this.noArgsCtorHandle = noArgsCtorHandle;
        this.noArgsCtorLambda = noArgsCtorLambda;
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
            } catch (Throwable e) {
                throw new BindingException("failed to create container instance of " + clazz.getName(), e);
            }
        }
        throw new BindingException("failed to create container instance of " + clazz.getName());
    }
}
