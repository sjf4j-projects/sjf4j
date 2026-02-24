package org.sjf4j.node;


import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for handling Java reflection types. Provides methods for resolving
 * generic types, getting raw classes, determining array types, and performing type
 * variable substitutions.
 * <p>
 * A core principle of this class is that it should not return null for any of its methods.
 */
public class Types {

    /**
     * Gets the fully qualified class name of an object.
     *
     * @param object the object to get the class name from
     * @return the fully qualified class name, or "null" if the object is null
     */
    public static String name(Object object) {
        return (object == null) ? "null" : object.getClass().getName();
    }

    public static Class<?> box(Class<?> clazz) {
        if (clazz == null) return Object.class;
        if (!clazz.isPrimitive()) return clazz;
        if (clazz == int.class) return Integer.class;
        if (clazz == long.class) return Long.class;
        if (clazz == double.class) return Double.class;
        if (clazz == float.class) return Float.class;
        if (clazz == boolean.class) return Boolean.class;
        if (clazz == char.class) return Character.class;
        if (clazz == byte.class) return Byte.class;
        if (clazz == short.class) return Short.class;
        throw new AssertionError(clazz);
    }

    /**
     * Gets the raw class for a given type, resolving generics as necessary.
     *
     * @param type the type to get the raw class from
     * @return the raw Class object for the given type
     * @throws IllegalArgumentException if the raw class cannot be determined
     */
    public static Class<?> rawClazz(Type type) {
        if (type == null) return Object.class;

        if (type instanceof Class) return (Class<?>) type;

        if (type instanceof ParameterizedType) return (Class<?>) ((ParameterizedType) type).getRawType();

        if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            Class<?> rawComponent = rawClazz(componentType);
            return Array.newInstance(rawComponent, 0).getClass();
        }

        if (type instanceof TypeVariable<?>) {
            TypeVariable<?> typeVar = (TypeVariable<?>) type;
            Type[] bounds = typeVar.getBounds();
            return rawClazz(bounds.length > 0 ? bounds[0] : Object.class);
        }

        if (type instanceof WildcardType) {
            WildcardType w = (WildcardType) type;
            Type[] upperBounds = w.getUpperBounds();
            return rawClazz(upperBounds.length > 0 ? upperBounds[0] : Object.class);
        }

        throw new IllegalArgumentException("Cannot get raw class from type: " + type);
    }

    public static Class<?> rawBox(Type type) {
        return box(rawClazz(type));
    }

    public static Type resolveTypeArgument(Type type, Class<?> target, int index) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = ((ParameterizedType) type);
            if (pt.getRawType() == target) {
                Type[] args = pt.getActualTypeArguments();
                if (index < args.length) {
                    return args[index];
                }
                return Object.class;
            }
        }
        Map<TypeVariable<?>, Type> typeVarMap = new HashMap<>();
        return resolve(type, target, index, typeVarMap);
    }

    private static Type resolve(Type type, Class<?> target, int index, Map<TypeVariable<?>, Type> typeVarMap) {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;

            // 1. Check if current class is the target
            if (clazz == target) {
                TypeVariable<? extends Class<?>>[] vars = clazz.getTypeParameters();
                if (index < vars.length) {
                    return substitute(vars[index], typeVarMap);
                }
                return Object.class;
            }

            // 2. Iterate through interfaces
            for (Type itf : clazz.getGenericInterfaces()) {
                Type result = resolve(itf, target, index, typeVarMap);
                if (result != null) return result;
            }

            // 3. Iterate through superclass
            Type superType = clazz.getGenericSuperclass();
            if (superType != null) {
                return resolve(superType, target, index, typeVarMap);
            }

            return Object.class;
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> raw = (Class<?>) pt.getRawType();

            // Establish substitution relationship for current class generic parameters
            TypeVariable<?>[] vars = raw.getTypeParameters();
            Type[] args = pt.getActualTypeArguments();
            for (int i = 0; i < vars.length; i++) {
                typeVarMap.put(vars[i], args[i]);
            }

            // 1. If target class is matched
            if (raw == target) {
                if (index < args.length) {
                    return substitute(args[index], typeVarMap);
                }
                return Object.class;
            }

            // 2. Search interfaces
            for (Type itf : raw.getGenericInterfaces()) {
                Type result = resolve(itf, target, index, typeVarMap);
                if (result != null) return result;
            }

            // 3. Search superclass
            Type superType = raw.getGenericSuperclass();
            if (superType != null) {
                return resolve(superType, target, index, typeVarMap);
            }

            return Object.class;
        }

        return Object.class;
    }

    private static Type substitute(Type type, Map<TypeVariable<?>, Type> map) {
        if (type instanceof TypeVariable<?>) {
            return map.getOrDefault(type, Object.class);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type[] args = pt.getActualTypeArguments();
            Type[] replaced = new Type[args.length];

            for (int i = 0; i < args.length; i++) {
                replaced[i] = substitute(args[i], map);
            }

            return newResolvedParameterizedType(
                    (Class<?>) pt.getRawType(),
                    replaced,
                    pt.getOwnerType()
            );
        }
        if (type instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) type;
            Type ct = substitute(gat.getGenericComponentType(), map);
            return new GenericArrayTypeImpl(ct);
        }
        return type;
    }

    // Used to construct a new ParameterizedType
    private static ParameterizedType newResolvedParameterizedType(Class<?> raw, Type[] args, Type owner) {
        return new ParameterizedType() {
            @Override public Type[] getActualTypeArguments() { return args; }
            @Override public Type getRawType() { return raw; }
            @Override public Type getOwnerType() { return owner; }
        };
    }


    public static Type fieldType(Type type, Field field) {
        if (type == null || field == null) return Object.class;

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Class<?> rawClass = (Class<?>) parameterizedType.getRawType();
            TypeVariable<?>[] typeVars = rawClass.getTypeParameters();
            Type[] actualArgs = parameterizedType.getActualTypeArguments();

            Map<TypeVariable<?>, Type> typeMap = new HashMap<>();
            for (int i = 0; i < typeVars.length && i < actualArgs.length; i++) {
                typeMap.put(typeVars[i], actualArgs[i]);
            }

            Type fieldType = field.getGenericType();
            return resolveType(fieldType, typeMap);
        }

        return field.getGenericType();
    }

    private static Type resolveType(Type type, Map<TypeVariable<?>, Type> typeMap) {
        if (type instanceof TypeVariable<?>) {
            Type replacement = typeMap.get(type);
            return replacement != null ? replacement : type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type raw = pt.getRawType();
            Type[] args = pt.getActualTypeArguments();
            Type[] resolvedArgs = new Type[args.length];
            for (int i = 0; i < args.length; i++) {
                resolvedArgs[i] = resolveType(args[i], typeMap);
            }
            return new ParameterizedTypeImpl((Class<?>) raw, resolvedArgs, pt.getOwnerType());
        } else if (type instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) type;
            Type component = resolveType(gat.getGenericComponentType(), typeMap);
            return new GenericArrayTypeImpl(component);
        } else if (type instanceof WildcardType) {
            return new WildcardTypeImpl(
                    ((WildcardType) type).getUpperBounds(),
                    ((WildcardType) type).getLowerBounds());
        } else {
            return type;
        }
    }


    /// Class

    public static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type rawType;
        private final Type[] actualTypeArguments;
        private final Type ownerType;

        public ParameterizedTypeImpl(Type rawType, Type[] actualTypeArguments, Type ownerType) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments != null ? actualTypeArguments.clone() : new Type[0];
            this.ownerType = ownerType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ParameterizedType)) return false;
            ParameterizedType that = (ParameterizedType) other;
            return Objects.equals(rawType, that.getRawType())
                    && Objects.equals(ownerType, that.getOwnerType())
                    && Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments)
                    ^ Objects.hashCode(rawType)
                    ^ Objects.hashCode(ownerType);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (ownerType != null) {
                sb.append(ownerType.getTypeName()).append(".");
            }
            sb.append(((Class<?>) rawType).getName());
            if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                sb.append("<");
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(actualTypeArguments[i].getTypeName());
                }
                sb.append(">");
            }
            return sb.toString();
        }
    }

    public static class GenericArrayTypeImpl implements GenericArrayType {
        private final Type componentType;

        public GenericArrayTypeImpl(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof GenericArrayType
                    && Objects.equals(componentType, ((GenericArrayType) other).getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(componentType);
        }

        @Override
        public String toString() {
            return componentType.getTypeName() + "[]";
        }
    }

    public static class WildcardTypeImpl implements WildcardType {
        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        public WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
            this.upperBounds = upperBounds != null ? upperBounds.clone() : new Type[]{Object.class};
            this.lowerBounds = lowerBounds != null ? lowerBounds.clone() : new Type[0];
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WildcardType)) return false;
            WildcardType that = (WildcardType) other;
            return Arrays.equals(upperBounds, that.getUpperBounds())
                    && Arrays.equals(lowerBounds, that.getLowerBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
        }

        @Override
        public String toString() {
            if (lowerBounds.length > 0) {
                return "? super " + lowerBounds[0].getTypeName();
            } else if (upperBounds.length == 1 && upperBounds[0] == Object.class) {
                return "?";
            } else {
                return "? extends " + upperBounds[0].getTypeName();
            }
        }
    }

}
