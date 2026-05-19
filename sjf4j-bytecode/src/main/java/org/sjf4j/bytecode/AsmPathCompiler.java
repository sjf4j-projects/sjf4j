package org.sjf4j.bytecode;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.compiled.PathCompiler;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Types;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Strict ASM-backed {@link PathCompiler} for single-target {@link CompiledPath#get(Object)} reads.
 *
 * <p>This compiler does not perform fallback-style terminal value coercion. Primitive leaves are
 * boxed when needed (for example {@code int -> Integer}), but incompatible reference-typed leaves
 * are rejected only when the mismatch is statically known during compilation.
 * Dynamically typed leaves reached through {@code Object}/{@link JsonObject}/{@link JsonArray}
 * access are returned as-is.
 *
 * <p>Once this compiler accepts a path, unsupported shapes also fail fast instead of silently
 * delegating back to the reflective fallback implementation.
 */
public class AsmPathCompiler implements PathCompiler {


    @Override
    public CompiledPath<?, ?> compilePath(JsonPath path, Type rootType, Type valueType) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(rootType, "rootType");
        Objects.requireNonNull(valueType, "valueType");
        Class<?> rootClazz = Types.rawClazz(rootType);
        Class<?> valueClazz = Types.rawClazz(valueType);

        if (rootClazz == Object.class) return null;
        if (path.length() < 2) return null;
        if (!path.isSingle()) {
            throw new JsonException("ASM CompiledPath only supports a single target path with Name/Index/Append segments: '" +
                    path.toExpr() + "'");
        }

        String compiledClassName = AsmUtil.generateClassName(CompiledPath.class, rootClazz.getSimpleName());
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        _writeClassHeader(cw, compiledClassName, rootClazz, valueClazz);
        _writeMethodExpr(cw, path);
        _writeMethodGet(cw, path, rootType, rootClazz, valueClazz);

        AsmClassLoader acl = AsmClassLoader.of(rootClazz.getClassLoader());
        Class<?> compiledClass = acl.defineClazz(compiledClassName, cw.toByteArray());
        try {
            return (CompiledPath<?, ?>) compiledClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new JsonException("Failed to instantiate ASM CompiledPath for '" + path.toExpr() +
                    "' (rootType=" + rootClazz.getName() + ", valueType=" + valueClazz.getName() + ")", e);
        }
    }


    private void _writeClassHeader(ClassWriter cw, String compiledClassName, Class<?> rootClazz, Class<?> valueClazz) {
        String compiledClassSig = "Ljava/lang/Object;L" + AsmUtil.INAME_COMPILED_PATH +
                "<L" + AsmUtil.toInternalName(rootClazz) +
                ";L" + AsmUtil.toInternalName(valueClazz) + ";>;";
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                AsmUtil.toInternalName(compiledClassName), compiledClassSig,
                "java/lang/Object", new String[]{AsmUtil.INAME_COMPILED_PATH});

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // load 'this'
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private void _writeMethodExpr(ClassWriter cw, JsonPath path) {
        // get(Object)Object
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "expr",
                "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitLdcInsn(path.toExpr());
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void _writeMethodGet(ClassWriter cw, JsonPath path, Type rootType,
                                 Class<?> rootClazz, Class<?> valueClazz) {
        // Object get(Object root) {...}
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null);
        mv.visitCode();

        if (path.hasAppend()) {
            // throw new JsonException();
            mv.visitTypeInsn(Opcodes.NEW, AsmUtil.toInternalName(JsonException.class));
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn("Cannot execute get() on a path containing append segments ('/-' or '[+]')");
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, AsmUtil.toInternalName(JsonException.class),
                    "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.ATHROW);
        } else {
            // if (root == null) return null;
            mv.visitVarInsn(Opcodes.ALOAD, 1); // load root
            Label notNullRoot = new Label();
            mv.visitJumpInsn(Opcodes.IFNONNULL, notNullRoot);
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(notNullRoot);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.CHECKCAST, AsmUtil.toInternalName(rootClazz));
            mv.visitVarInsn(Opcodes.ASTORE, 1);

            _writeGetterChain(mv, path, rootType, rootClazz, valueClazz);
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }


    // public Integer get(User root) {
    //  if (root == null) return null;
    //
    //  Profile _2 = root.getProfile();
    //  if (_2 == null) return null;
    //
    //  Map _3 = _2.scores;
    //  if (_3 == null) return null;
    //
    //  JsonObject _4 = (JsonObject) _3.get("good");
    //  if (_4 == null) return null;
    //
    //  Object _5 = _4.getNode("bad");
    //  if (_5 == null) return null;
    //
    //  Object _6 = Nodes.getInObject(_5, "none")
    //  if (_6 == null) return null;
    //
    //  return Nodes.getInArray(_6, 3);
    // }
    private void _writeGetterChain(MethodVisitor mv, JsonPath path, Type rootType,
                                   Class<?> rootClazz, Class<?> valueClazz) {
        PathSegment[] segments = path.segments();
        String expr = path.toExpr();
        Type currentType = rootType;
        Class<?> currentClazz = rootClazz;
        Class<?> boxedValueClazz = Types.box(valueClazz);
        int i = 1;
        for (; i < segments.length; i++) {
            PathSegment ps = segments[i];
            if (ps instanceof PathSegment.Name) {
                String name = ((PathSegment.Name) ps).name;
                if (Object.class == currentClazz) {
                    // Object _2 = Nodes.getInObject(_1, name);
                    mv.visitVarInsn(Opcodes.ALOAD, i);
                    mv.visitLdcInsn(name);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                            "getInObject",
                            "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                            false);
                    mv.visitVarInsn(Opcodes.ASTORE, i + 1);

                } else if (Map.class.isAssignableFrom(currentClazz)) {
                    // V _2 = (V) _1.get(name);
                    Type vvt = Types.resolveTypeArgument(currentType, Map.class, 1);
                    Class<?> vvc = Types.rawClazz(vvt);
                    mv.visitVarInsn(Opcodes.ALOAD, i);
                    mv.visitLdcInsn(name);
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map",
                            "get",
                            "(Ljava/lang/Object;)Ljava/lang/Object;",
                            true);
                    vvc = AsmUtil.emitCastOrBox(mv, vvc);
                    mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                    currentType = vvt;
                    currentClazz = vvc;
                } else if (JsonObject.class == currentClazz) {
                    // Object _2 = _1.getNode(name);
                    mv.visitVarInsn(Opcodes.ALOAD, i);
                    mv.visitLdcInsn(name);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_OBJECT,
                            "getNode",
                            "(Ljava/lang/String;)Ljava/lang/Object;",
                            false);
                    mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                    currentType = Object.class;
                    currentClazz = Object.class;
                } else {
                    NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(currentClazz).pojoInfo;
                    if (pi == null) {
                        throw new JsonException("ASM CompiledPath cannot read property '" + name +
                                "' from " + currentClazz.getName() + " at '" + expr + "'");
                    }

                    NodeRegistry.PropertyInfo propInfo = pi.readableProperties.get(name);
                    if (propInfo != null) {
                        Type vvt = propInfo.type;
                        Class<?> vvc = Types.rawClazz(vvt);
                        if (propInfo.publicField != null) {
                            // V _2 = _1.xxx;
                            mv.visitVarInsn(Opcodes.ALOAD, i);
                            mv.visitFieldInsn(Opcodes.GETFIELD, AsmUtil.toInternalName(currentClazz),
                                    propInfo.publicField.getName(),
                                    org.objectweb.asm.Type.getDescriptor(vvc));
                            vvc = AsmUtil.emitCastOrBox(mv, vvc);
                            mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                        } else if (propInfo.publicGetter != null) {
                            // V _2 = _1.getXxx();
                            mv.visitVarInsn(Opcodes.ALOAD, i);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.toInternalName(currentClazz),
                                    propInfo.publicGetter.getName(),
                                    "()" + org.objectweb.asm.Type.getDescriptor(vvc),
                                    false);
                            vvc = AsmUtil.emitCastOrBox(mv, vvc);
                            mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                        } else {
                            throw new JsonException("ASM CompiledPath property '" + name + "' on " +
                                    currentClazz.getName() + " is not readable through a public field or public getter");
                        }
                        currentType = vvt;
                        currentClazz = vvc;
                    } else if (pi.isJojo) {
                        // Object _2 = _1.getNode(name);
                        mv.visitVarInsn(Opcodes.ALOAD, i);
                        mv.visitLdcInsn(name);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_OBJECT,
                                "getNode",
                                "(Ljava/lang/String;)Ljava/lang/Object;",
                                false);
                        mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                        currentType = Object.class;
                        currentClazz = Object.class;

                    } else {
                        throw new JsonException("ASM CompiledPath cannot resolve property '" + name +
                                "' on " + currentClazz.getName() +
                                ": no readable property found and target is not a JOJO dynamic object");
                    }
                }
            } else if (ps instanceof PathSegment.Index) {
                int idx = ((PathSegment.Index) ps).index;
                if (Object.class == currentClazz) {
                    // Object _2 = Nodes.getInArray(_1, idx);
                    mv.visitVarInsn(Opcodes.ALOAD, i);
                    mv.visitLdcInsn(idx);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                            "getInArray",
                            "(Ljava/lang/Object;I;)Ljava/lang/Object;",
                            false);
                    mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                } else if (List.class.isAssignableFrom(currentClazz)) {
                    // V _2 = (V) _1.get(idx);
                    Type vvt = Types.resolveTypeArgument(currentType, List.class, 0);
                    Class<?> vvc = Types.rawClazz(vvt);
                    mv.visitVarInsn(Opcodes.ALOAD, i);
                    mv.visitLdcInsn(idx);
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List",
                            "get",
                            "(I)Ljava/lang/Object;",
                            true);
                    vvc = AsmUtil.emitCastOrBox(mv, vvc);
                    mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                    currentType = vvt;
                    currentClazz = vvc;
                } else if (JsonArray.class.isAssignableFrom(currentClazz)) {
                    // Object _2 = _1.get(idx);
                    mv.visitVarInsn(Opcodes.ALOAD, i);
                    mv.visitLdcInsn(idx);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_ARRAY,
                            "getNode",
                            "(I)Ljava/lang/Object;",
                            false);
                    mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                    currentType = Object.class;
                    currentClazz = Object.class;
                } else if (currentClazz.isArray()) {
                    // V _2 = (V) _1[idx];
                    Class<?> vvc = currentClazz.getComponentType();
                    mv.visitVarInsn(Opcodes.ALOAD, i);
                    mv.visitLdcInsn(idx);
                    AsmUtil.emitArrayLoad(mv, vvc);
                    vvc = AsmUtil.emitCastOrBox(mv, vvc);
                    mv.visitVarInsn(Opcodes.ASTORE, i + 1);
                    currentType = vvc;
                    currentClazz = vvc;
                } else if (Set.class.isAssignableFrom(currentClazz)) {
                    throw new JsonException("ASM CompiledPath does not support indexed reads on unordered Set type " +
                            currentClazz.getName() + " at '" + expr + "'");
                } else {
                    throw new JsonException("ASM CompiledPath expected an array-like target before index [" + idx +
                            "] at '" + expr + "', but found " + currentClazz.getName());
                }
            } else {
                throw new AssertionError(AsmPathCompiler.class);
            }

            if (i < segments.length - 1) {
                // if (_2 == null) return null;
                mv.visitVarInsn(Opcodes.ALOAD, i + 1);
                Label notNull = new Label();
                mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitLabel(notNull);
            } else {
                Class<?> boxedCurrentClazz = Types.box(currentClazz);
                if (boxedCurrentClazz != Object.class && boxedValueClazz != Object.class &&
                        !boxedValueClazz.isAssignableFrom(boxedCurrentClazz)) {
                    throw new JsonException("ASM CompiledPath '" + expr +
                            "' does not coerce terminal type " + boxedCurrentClazz.getName() +
                            " to requested valueType " + boxedValueClazz.getName());
                }

                // return _2;
                mv.visitVarInsn(Opcodes.ALOAD, i + 1);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }

    }


    /// static

}
