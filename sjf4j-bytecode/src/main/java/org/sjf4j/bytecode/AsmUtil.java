package org.sjf4j.bytecode;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.Types;

import java.util.concurrent.atomic.AtomicInteger;

public final class AsmUtil {

    private static final AtomicInteger COUNTER = new AtomicInteger(1000);

    public static final String INAME_COMPILED_PATH = AsmUtil.toInternalName(CompiledPath.class);
    public static final String INAME_NODES = AsmUtil.toInternalName(Nodes.class);
    public static final String INAME_JSON_OBJECT = AsmUtil.toInternalName(JsonObject.class);
    public static final String INAME_JSON_ARRAY = AsmUtil.toInternalName(JsonArray.class);


    public static String generateClassName(Class<?> compiledClazz, String feature) {
        return "org.sjf4j.bytecode.generated." + compiledClazz.getSimpleName() +
                "_" + feature + "_" + COUNTER.getAndIncrement();
    }

    public static String toInternalName(String clazzName) {
        return clazzName.replace('.', '/');
    }

    public static String toInternalName(Class<?> clazz) {
        return toInternalName(clazz.getName());
    }


    /**
     * Emits either a reference CHECKCAST or primitive boxing, and returns the
     * resulting reference type that can be stored in an Object local.
     */
    public static Class<?> emitCastOrBox(MethodVisitor mv, Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return Object.class;
        if (clazz.isPrimitive()) {
            emitBox(mv, clazz);
            return Types.box(clazz);
        }
        mv.visitTypeInsn(Opcodes.CHECKCAST, toInternalName(clazz));
        return clazz;
    }

    /**
     * Emits either a reference CHECKCAST or primitive unboxing from Object.
     */
    public static void emitCastOrUnbox(MethodVisitor mv, Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return;
        if (!clazz.isPrimitive()) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, toInternalName(clazz));
            return;
        }
        if (clazz == boolean.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else if (clazz == byte.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
        } else if (clazz == char.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
        } else if (clazz == short.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Short");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
        } else if (clazz == int.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        } else if (clazz == long.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else if (clazz == float.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
        } else if (clazz == double.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
        } else {
            throw new AssertionError(clazz);
        }
    }

    /**
     * Emits primitive boxing through the matching wrapper valueOf method.
     */
    public static void emitBox(MethodVisitor mv, Class<?> primitiveClazz) {
        if (primitiveClazz == boolean.class) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        } else if (primitiveClazz == byte.class) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        } else if (primitiveClazz == char.class) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
        } else if (primitiveClazz == short.class) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
        } else if (primitiveClazz == int.class) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (primitiveClazz == long.class) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        } else if (primitiveClazz == float.class) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
        } else if (primitiveClazz == double.class) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        } else {
            throw new AssertionError(primitiveClazz);
        }
    }

    /**
     * Emits the correct array-load opcode for the component type.
     */
    public static void emitArrayLoad(MethodVisitor mv, Class<?> componentClazz) {
        if (!componentClazz.isPrimitive()) {
            mv.visitInsn(Opcodes.AALOAD);
        } else if (componentClazz == boolean.class || componentClazz == byte.class) {
            mv.visitInsn(Opcodes.BALOAD);
        } else if (componentClazz == char.class) {
            mv.visitInsn(Opcodes.CALOAD);
        } else if (componentClazz == short.class) {
            mv.visitInsn(Opcodes.SALOAD);
        } else if (componentClazz == int.class) {
            mv.visitInsn(Opcodes.IALOAD);
        } else if (componentClazz == long.class) {
            mv.visitInsn(Opcodes.LALOAD);
        } else if (componentClazz == float.class) {
            mv.visitInsn(Opcodes.FALOAD);
        } else if (componentClazz == double.class) {
            mv.visitInsn(Opcodes.DALOAD);
        } else {
            throw new AssertionError(componentClazz);
        }
    }

    /**
     * Emits the correct array-store opcode for the component type.
     */
    public static void emitArrayStore(MethodVisitor mv, Class<?> componentClazz) {
        if (!componentClazz.isPrimitive()) {
            mv.visitInsn(Opcodes.AASTORE);
        } else if (componentClazz == boolean.class || componentClazz == byte.class) {
            mv.visitInsn(Opcodes.BASTORE);
        } else if (componentClazz == char.class) {
            mv.visitInsn(Opcodes.CASTORE);
        } else if (componentClazz == short.class) {
            mv.visitInsn(Opcodes.SASTORE);
        } else if (componentClazz == int.class) {
            mv.visitInsn(Opcodes.IASTORE);
        } else if (componentClazz == long.class) {
            mv.visitInsn(Opcodes.LASTORE);
        } else if (componentClazz == float.class) {
            mv.visitInsn(Opcodes.FASTORE);
        } else if (componentClazz == double.class) {
            mv.visitInsn(Opcodes.DASTORE);
        } else {
            throw new AssertionError(componentClazz);
        }
    }



}
