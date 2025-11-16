package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.PathToken;

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

public class TypeUtil {

    public static String typeName(Object object) {
        return (object == null) ? "[null]" : object.getClass().getName();
    }

    public static boolean isArray(Type type) {
        if (type instanceof TypeReference) {
            type = ((TypeReference<?>) type).getType();
        }
        if (type == null) {
            return false;
        }
        if (type instanceof Class) {
            return ((Class<?>) type).isArray();
        }
        return type instanceof GenericArrayType;
    }

    public static Class<?> getRawClass(Type type) {
        if (type instanceof TypeReference) {
            type = ((TypeReference<?>) type).getType();
        }
        if (type == null) {
            return Object.class;
        }
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            Class<?> rawComponent = getRawClass(componentType);
            return Array.newInstance(rawComponent, 0).getClass();
        }
        if (type instanceof TypeVariable<?>) {
            TypeVariable<?> typeVar = (TypeVariable<?>) type;
            Type[] bounds = typeVar.getBounds();
            return getRawClass(bounds.length > 0 ? bounds[0] : Object.class);
        }
        if (type instanceof WildcardType) {
            WildcardType w = (WildcardType) type;
            Type[] upperBounds = w.getUpperBounds();
            return getRawClass(upperBounds.length > 0 ? upperBounds[0] : Object.class);
        }
        throw new IllegalArgumentException("Cannot get raw class from type: " + type);

    }

    // Support: Map<String, Person>, Wrapper<Person>, Person[]
    public static Type getTypeArgument(Type type, int idx) {
        if (type instanceof TypeReference) {
            type = ((TypeReference<?>) type).getType();
        }

        if (type == null) {
            return Object.class;
        } else if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            return clazz.isArray() ? clazz.getComponentType() : Object.class;
        } else if (type instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) type).getActualTypeArguments();
            if (idx >= 0 && idx < typeArgs.length) {
                return typeArgs[idx];
            }
        } else if (type instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) type;
            Type componentType = gat.getGenericComponentType();
            if (idx == 0) {
                return componentType;
            }
            return Object.class;
        }
        return Object.class;
    }

    public static Type getFieldType(Type type, Field field) {
        if (type instanceof TypeReference<?>) {
            type = ((TypeReference<?>) type).getType();
        }
        if (type == null || field == null) return null;

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

    /// Private

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
