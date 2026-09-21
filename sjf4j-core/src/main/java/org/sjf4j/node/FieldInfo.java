package org.sjf4j.node;

import org.sjf4j.binding.FieldReader;
import org.sjf4j.value.ValueInfo;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Cached binding metadata and accessors for one object property.
 */
public class FieldInfo {

    public enum ContainerKind {
        NONE,
        LIST,
        SET,
        MAP,
        ARRAY
    }

    public final String name;
    public final Field publicField;

    public final Type type;
    public final boolean genericDependent;
    public final Class<?> boxed;

    public final ContainerKind containerKind;
    public final Type argType;
    public final Class<?> argClazz;
    public final Class<?> argBoxed;
    public final OneOfInfo argOneOfInfo;

    public final Method publicGetter;
    public final MethodHandle getterHandle;
    public final Function<Object, Object> getterLambda;

    public final Method publicSetter;
    public final MethodHandle setterHandle;
    public final BiConsumer<Object, Object> setterLambda;

    public final OneOfInfo oneOfInfo;
    public final String valueFormat;
    public final ValueInfo valueInfo;

    public final FieldReader binder;

    /**
     * Creates property binding metadata and resolves its container element type.
     */
    public FieldInfo(String name, Field publicField, Type type, boolean genericDependent, Class<?> boxed,
                     Method publicGetter, MethodHandle getterHandle, Function<Object, Object> getterLambda,
                     Method publicSetter, MethodHandle setterHandle, BiConsumer<Object, Object> setterLambda,
                     OneOfInfo oneOfInfo, String valueFormat, ValueInfo valueInfo,
                     FieldReader binder) {
        this.name = name;
        this.publicField = publicField;
        this.type = type;
        this.genericDependent = genericDependent;
        this.boxed = boxed;

        ContainerKind kind = ContainerKind.NONE;
        Type argType = null;
        Class<?> argClazz = null;
        if (List.class.isAssignableFrom(this.boxed)) {
            kind = ContainerKind.LIST;
            argType = Types.resolveTypeArgument(type, List.class, 0);
            argClazz = Types.rawBox(argType);
        } else if (Set.class.isAssignableFrom(this.boxed)) {
            kind = ContainerKind.SET;
            argType = Types.resolveTypeArgument(type, Set.class, 0);
            argClazz = Types.rawBox(argType);
        } else if (Map.class.isAssignableFrom(this.boxed)) {
            kind = ContainerKind.MAP;
            argType = Types.resolveTypeArgument(type, Map.class, 1);
            argClazz = Types.rawBox(argType);
        } else if (this.boxed.isArray()) {
            kind = ContainerKind.ARRAY;
            argType = this.boxed.getComponentType();
            argClazz = Types.box((Class<?>) argType);
        }
        this.containerKind = kind;
        this.argType = argType;
        this.argClazz = argClazz;
        this.argBoxed = Types.rawBox(argClazz);
        this.argOneOfInfo = argBoxed == null ? null : ReflectUtil.resolveOneOfInfo(argBoxed);

        this.publicGetter = publicGetter;
        this.getterHandle = getterHandle;
        this.getterLambda = getterLambda;

        this.publicSetter = publicSetter;
        this.setterHandle = setterHandle;
        this.setterLambda = setterLambda;

        this.oneOfInfo = oneOfInfo;
        this.valueFormat = valueFormat;
        this.valueInfo = valueInfo;
        this.binder = binder;
    }

    /**
     * Returns true when a getter is available.
     */
    public boolean hasGetter() {
        return getterHandle != null || getterLambda != null;
    }

    /**
     * Returns true when a setter is available.
     */
    public boolean hasSetter() {
        return setterHandle != null || setterLambda != null;
    }


    /**
     * Invokes this property's getter.
     */
    public Object invokeGetter(Object receiver) {
        Objects.requireNonNull(receiver, "receiver");
        return PojoAccess.invokeGetter(name, getterHandle, getterLambda, receiver);
    }

    /**
     * Invokes this property's setter when present and reports success.
     */
    public boolean invokeSetterIfPresent(Object receiver, Object value) {
        if (setterHandle == null && setterLambda == null) return false;
        invokeSetter(receiver, value);
        return true;
    }

    /**
     * Invokes this property's setter.
     */
    public void invokeSetter(Object receiver, Object value) {
        Objects.requireNonNull(receiver, "receiver");
        PojoAccess.invokeSetter(name, setterHandle, setterLambda, receiver, value);
    }

}
