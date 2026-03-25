package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypesCoverageTest {

    interface Box<T> {}

    static class StringBox implements Box<String> {}

    static class GenericHolder<T> {
        T value;
        List<T> list;
        T[] array;
        List<? extends Number> upperBound;
        List<? super Integer> lowerBound;
    }

    static class Owner<T> {
        List<T> member;
        T[] arrayMember;
        T direct;
    }

    @Test
    void testRawTypeHelpers() throws NoSuchFieldException {
        assertEquals(Object.class, Types.rawClazz(null));
        assertEquals(String.class, Types.rawClazz(String.class));
        assertEquals(List.class, Types.rawClazz(new TypeReference<List<String>>() {}.getType()));

        TypeVariableCapture capture = new TypeVariableCapture();
        assertEquals(Object.class, Types.rawClazz(capture.typeVariable()));
        assertEquals(Object[].class, Types.rawClazz(GenericHolder.class.getDeclaredField("array").getGenericType()));

        ParameterizedType upperList = (ParameterizedType) GenericHolder.class.getDeclaredField("upperBound").getGenericType();
        WildcardType upperWildcard = (WildcardType) upperList.getActualTypeArguments()[0];
        assertEquals(Number.class, Types.rawClazz(upperWildcard));

        assertEquals(Integer.class, Types.rawBox(int.class));
        assertEquals(List.class, Types.rawBox(new TypeReference<List<String>>() {}.getType()));

        Type unsupported = new Type() {
            @Override
            public String getTypeName() {
                return "unsupported";
            }
        };
        assertThrows(JsonException.class, () -> Types.rawClazz(unsupported));
    }

    @Test
    void testResolveTypeArgumentAndFieldType() throws NoSuchFieldException {
        Type listType = new TypeReference<List<Float>>() {}.getType();
        assertEquals(Float.class, Types.resolveTypeArgument(listType, List.class, 0));
        assertEquals(Object.class, Types.resolveTypeArgument(listType, List.class, 1));
        assertEquals(String.class, Types.resolveTypeArgument(StringBox.class, Box.class, 0));

        Field valueField = GenericHolder.class.getDeclaredField("value");
        Field listField = GenericHolder.class.getDeclaredField("list");
        Field arrayField = GenericHolder.class.getDeclaredField("array");
        Type holderType = new TypeReference<GenericHolder<String>>() {}.getType();

        assertEquals(String.class, Types.fieldType(holderType, valueField));
        Type resolvedList = Types.fieldType(holderType, listField);
        assertInstanceOf(ParameterizedType.class, resolvedList);
        assertEquals("java.util.List<java.lang.String>", resolvedList.getTypeName());
        assertEquals(String[].class, Types.rawClazz(Types.fieldType(holderType, arrayField)));
        assertEquals(Object.class, Types.fieldType(null, valueField));
    }

    @Test
    void testResolveMemberType() throws NoSuchFieldException {
        Field memberField = Owner.class.getDeclaredField("member");
        Field arrayField = Owner.class.getDeclaredField("arrayMember");
        Field directField = Owner.class.getDeclaredField("direct");
        Field upperField = GenericHolder.class.getDeclaredField("upperBound");
        Field lowerField = GenericHolder.class.getDeclaredField("lowerBound");

        assertEquals(Object.class, Types.resolveMemberType(null, Owner.class, null));
        assertEquals(String.class, Types.resolveMemberType(null, Owner.class, String.class));
        assertEquals(memberField.getGenericType(), Types.resolveMemberType(null, Owner.class, memberField.getGenericType()));
        assertEquals(Object.class, Types.resolveMemberType(Owner.class, Owner.class, directField.getGenericType()));

        Type ownerType = new TypeReference<Owner<Integer>>() {}.getType();
        assertEquals("java.util.List<java.lang.Integer>", Types.resolveMemberType(ownerType, Owner.class, memberField.getGenericType()).getTypeName());
        assertEquals(Integer[].class, Types.rawClazz(Types.resolveMemberType(ownerType, Owner.class, arrayField.getGenericType())));
        assertEquals(Integer.class, Types.resolveMemberType(ownerType, Owner.class, directField.getGenericType()));

        Type genericHolder = new TypeReference<GenericHolder<Long>>() {}.getType();
        assertEquals("java.util.List<? extends java.lang.Number>", Types.resolveMemberType(genericHolder, GenericHolder.class, upperField.getGenericType()).getTypeName());
        assertEquals("java.util.List<? super java.lang.Integer>", Types.resolveMemberType(genericHolder, GenericHolder.class, lowerField.getGenericType()).getTypeName());
    }

    @Test
    void testTypeImplementations() {
        Types.ParameterizedTypeImpl listOfString = new Types.ParameterizedTypeImpl(List.class, new Type[]{String.class}, null);
        Types.ParameterizedTypeImpl sameListOfString = new Types.ParameterizedTypeImpl(List.class, new Type[]{String.class}, null);
        Types.ParameterizedTypeImpl listOfInteger = new Types.ParameterizedTypeImpl(List.class, new Type[]{Integer.class}, null);
        assertEquals(listOfString, sameListOfString);
        assertEquals(listOfString.hashCode(), sameListOfString.hashCode());
        assertNotEquals(listOfString, listOfInteger);
        assertFalse(listOfString.equals("x"));
        assertEquals("java.util.List<java.lang.String>", listOfString.toString());
        assertArrayEquals(new Type[]{String.class}, listOfString.getActualTypeArguments());
        assertEquals(List.class, listOfString.getRawType());

        Types.GenericArrayTypeImpl stringArray = new Types.GenericArrayTypeImpl(String.class);
        Types.GenericArrayTypeImpl sameStringArray = new Types.GenericArrayTypeImpl(String.class);
        assertEquals(stringArray, sameStringArray);
        assertEquals(stringArray.hashCode(), sameStringArray.hashCode());
        assertEquals("java.lang.String[]", stringArray.toString());
        assertEquals(String.class, stringArray.getGenericComponentType());

        Types.WildcardTypeImpl any = new Types.WildcardTypeImpl(null, null);
        Types.WildcardTypeImpl upper = new Types.WildcardTypeImpl(new Type[]{Number.class}, new Type[0]);
        Types.WildcardTypeImpl lower = new Types.WildcardTypeImpl(new Type[]{Object.class}, new Type[]{Integer.class});
        assertEquals("?", any.toString());
        assertEquals("? extends java.lang.Number", upper.toString());
        assertEquals("? super java.lang.Integer", lower.toString());
        assertEquals(upper, new Types.WildcardTypeImpl(new Type[]{Number.class}, new Type[0]));
        assertNotEquals(upper, lower);
        assertFalse(upper.equals("x"));
        assertArrayEquals(new Type[]{Number.class}, upper.getUpperBounds());
        assertArrayEquals(new Type[]{Integer.class}, lower.getLowerBounds());
    }

    static class TypeVariableCapture<T> {
        Type typeVariable() {
            return getClass().getTypeParameters()[0];
        }
    }
}
