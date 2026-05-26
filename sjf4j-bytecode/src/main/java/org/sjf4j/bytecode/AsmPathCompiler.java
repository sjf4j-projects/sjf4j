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
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Strict ASM-backed {@link PathCompiler} for single-target {@link CompiledPath#get(Object)} reads
 * and {@link CompiledPath#put(Object, Object)} writes.
 *
 * <p>This compiler does not perform fallback-style terminal value coercion. Primitive leaves are
 * boxed when needed (for example {@code int -> Integer}), but incompatible reference-typed leaves
 * are rejected only when the mismatch is statically known during compilation.
 * Dynamically typed leaves reached through {@code Object}/{@link JsonObject}/{@link JsonArray}
 * access are returned as-is.
 *
 * <p>Unsupported shapes fail fast instead of silently delegating back to the reflective fallback
 * implementation.
 */
public class AsmPathCompiler implements PathCompiler {

    @Override
    public CompiledPath<?, ?> compilePath(JsonPath path, Type rootType, Type valueType) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(rootType, "rootType");
        Objects.requireNonNull(valueType, "valueType");
        Class<?> rootClazz = Types.rawClazz(rootType);
        Class<?> valueClazz = Types.rawClazz(valueType);

        if (rootClazz == Object.class) {
            throw new JsonException("ASM CompiledPath does not support Object root for '" + path.toExpr() +
                    "'; use FallbackCompiledPath for fully dynamic roots");
        }
        if (path.length() < 2) {
            throw new JsonException("ASM CompiledPath requires a non-root target path: '" + path.toExpr() + "'");
        }
        if (!path.isSinglePut()) {
            throw new JsonException("ASM CompiledPath supports only a single target path with Name/Index/Append segments: '" +
                    path.toExpr() + "'");
        }

        String compiledClassName = AsmUtil.generateClassName(CompiledPath.class, rootClazz.getSimpleName());
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        _writeClassHead(cw, compiledClassName, rootClazz, valueClazz);
        _writeMethodExpr(cw, path);
        _writeMethodGet(cw, path, rootType, rootClazz, valueClazz);
        _writeMethodPut(cw, path, rootType, rootClazz, valueClazz);
        _writeMethodEnsurePut(cw, path, rootType, rootClazz);

        AsmClassLoader acl = AsmClassLoader.of(rootClazz.getClassLoader());
        Class<?> compiledClass = acl.defineClazz(compiledClassName, cw.toByteArray());
        try {
            return (CompiledPath<?, ?>) compiledClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new JsonException("failed to instantiate ASM CompiledPath for '" + path.toExpr() +
                    "' (rootType=" + rootClazz.getName() + ", valueType=" + valueClazz.getName() + ")", e);
        }
    }

    private void _writeClassHead(ClassWriter cw, String compiledClassName, Class<?> rootClazz, Class<?> valueClazz) {
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
        // String expr()
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
        // Object get(Object root)
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null);
        mv.visitCode();

        if (path.hasAppend()) {
            _emitThrow(mv, "cannot execute get() on append path '" + path.toExpr() +
                    "' because append segments ('/-' or '[+]') are write-only");
        } else {
            // if (root == null) return null;
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            Label notNullRoot = new Label();
            mv.visitJumpInsn(Opcodes.IFNONNULL, notNullRoot);
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(notNullRoot);

            // root = (Root) root;
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.CHECKCAST, AsmUtil.toInternalName(rootClazz));
            mv.visitVarInsn(Opcodes.ASTORE, 1);

            _emitGetChain(mv, path, rootType, rootClazz, valueClazz);
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
    private void _emitGetChain(MethodVisitor mv, JsonPath path, Type rootType,
                               Class<?> rootClazz, Class<?> valueClazz) {
        PathSegment[] segments = path.segments();
        String expr = path.toExpr();
        Type currentType = rootType;
        Class<?> currentClazz = rootClazz;
        Class<?> boxedValueClazz = Types.box(valueClazz);
        int i = 1;
        for (; i < segments.length; i++) {
            currentType = _emitGetChild(mv, i, segments[i], currentType, currentClazz, expr);
            currentClazz = Types.rawClazz(currentType);

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

    private Type _emitGetChild(MethodVisitor mv, int currentLocal, PathSegment ps,
                               Type currentType, Class<?> currentClazz, String expr) {
        if (ps instanceof PathSegment.Name) {
            String name = ((PathSegment.Name) ps).name;
            if (Object.class == currentClazz) {
                // Object _2 = Nodes.getInObject(_1, name);
                mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                        "getInObject",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                        false);
                mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                return Object.class;
            } else if (Map.class.isAssignableFrom(currentClazz)) {
                // V _2 = (V) _1.get(name);
                Type vvt = Types.resolveTypeArgument(currentType, Map.class, 1);
                Class<?> vvc = Types.rawClazz(vvt);
                mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map",
                        "get",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        true);
                vvc = AsmUtil.emitCastOrBox(mv, vvc);
                mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                return vvt;
            } else if (JsonObject.class == currentClazz) {
                // Object _2 = _1.getNode(name);
                mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_OBJECT,
                        "getNode",
                        "(Ljava/lang/String;)Ljava/lang/Object;",
                        false);
                mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                return Object.class;
            } else {
                NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(currentClazz).pojoInfo;
                if (pi == null) {
                    throw new JsonException("cannot read property '" + name +
                            "' from " + currentClazz.getName() + " at '" + expr + "'");
                }

                NodeRegistry.PropertyInfo propInfo = pi.readableProperties.get(name);
                if (propInfo != null) {
                    Type vvt = propInfo.type;
                    Class<?> vvc = Types.rawClazz(vvt);
                    if (propInfo.publicField != null) {
                        // V _2 = _1.xxx;
                        mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                        mv.visitFieldInsn(Opcodes.GETFIELD, AsmUtil.toInternalName(currentClazz),
                                propInfo.publicField.getName(),
                                org.objectweb.asm.Type.getDescriptor(vvc));
                        vvc = AsmUtil.emitCastOrBox(mv, vvc);
                        mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                    } else if (propInfo.publicGetter != null) {
                        // V _2 = _1.getXxx();
                        mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.toInternalName(currentClazz),
                                propInfo.publicGetter.getName(),
                                "()" + org.objectweb.asm.Type.getDescriptor(vvc),
                                false);
                        vvc = AsmUtil.emitCastOrBox(mv, vvc);
                        mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                    } else {
                        throw new JsonException("property '" + name + "' on " +
                                currentClazz.getName() + " is not readable through a public field or public getter" +
                                " at '" + expr + "'");
                    }
                    return vvt;
                } else if (pi.isJojo) {
                    // Object _2 = _1.getNode(name);
                    mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                    mv.visitLdcInsn(name);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_OBJECT,
                            "getNode",
                            "(Ljava/lang/String;)Ljava/lang/Object;",
                            false);
                    mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                    return Object.class;
                } else {
                    throw new JsonException("cannot resolve property '" + name +
                            "' on " + currentClazz.getName() +
                            " at '" + expr +
                            "': no readable property found and target is not a JOJO dynamic object");
                }
            }
        } else if (ps instanceof PathSegment.Index) {
            int idx = ((PathSegment.Index) ps).index;
            if (idx < 0) {
                throw new JsonException("ASM CompiledPath does not support negative array index " + idx +
                        " at '" + expr + "'");
            }
            if (Object.class == currentClazz) {
                // Object _2 = Nodes.getInArray(_1, idx);
                mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                mv.visitLdcInsn(idx);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                        "getInArray",
                        "(Ljava/lang/Object;I)Ljava/lang/Object;",
                        false);
                mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                return Object.class;
            } else if (List.class.isAssignableFrom(currentClazz)) {
                // V _2 = (V) _1.get(idx);
                Type vvt = Types.resolveTypeArgument(currentType, List.class, 0);
                Class<?> vvc = Types.rawClazz(vvt);
                mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                mv.visitLdcInsn(idx);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List",
                        "get",
                        "(I)Ljava/lang/Object;",
                        true);
                vvc = AsmUtil.emitCastOrBox(mv, vvc);
                mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                return vvt;
            } else if (JsonArray.class.isAssignableFrom(currentClazz)) {
                // Object _2 = _1.getNode(idx);
                mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                mv.visitLdcInsn(idx);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_ARRAY,
                        "getNode",
                        "(I)Ljava/lang/Object;",
                        false);
                mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                return Object.class;
            } else if (currentClazz.isArray()) {
                // V _2 = (V) _1[idx];
                Class<?> vvc = currentClazz.getComponentType();
                mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                mv.visitLdcInsn(idx);
                AsmUtil.emitArrayLoad(mv, vvc);
                vvc = AsmUtil.emitCastOrBox(mv, vvc);
                mv.visitVarInsn(Opcodes.ASTORE, currentLocal + 1);
                return vvc;
            } else if (Set.class.isAssignableFrom(currentClazz)) {
                throw new JsonException("cannot read by index from unordered Set type " +
                        currentClazz.getName() + " at '" + expr + "'");
            } else {
                throw new JsonException("expected array-like target before index [" + idx +
                        "] at '" + expr + "', but was " + currentClazz.getName());
            }
        } else {
            throw new JsonException("unsupported path token '" + ps + "' at '" + expr + "'");
        }
    }


    private void _writeMethodPut(ClassWriter cw, JsonPath path, Type rootType, Class<?> rootClazz, Class<?> valueClazz) {
        // Object put(Object root, Object value) {...}
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null);
        mv.visitCode();

        // Objects.requireNonNull(root, "root");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitLdcInsn("root");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects",
                "requireNonNull", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
        // root = (Root) root;
        mv.visitTypeInsn(Opcodes.CHECKCAST, AsmUtil.toInternalName(rootClazz));
        mv.visitVarInsn(Opcodes.ASTORE, 1);

        if (path.hasAppend() && !(path.tail() instanceof PathSegment.Append)) {
            _emitThrow(mv, "cannot execute put() on path '" + path.toExpr() +
                    "' because append segments before the leaf require ensurePut()");
        } else {
            Type parentType = _emitPutChain(mv, path, rootType, rootClazz);
            _emitPutLast(mv, path, Types.rawClazz(parentType));
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void _writeMethodEnsurePut(ClassWriter cw, JsonPath path, Type rootType, Class<?> rootClazz) {
        // Object ensurePut(Object root, Object value) {...}
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "ensurePut",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null);
        mv.visitCode();

        // Objects.requireNonNull(root, "root");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitLdcInsn("root");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects",
                "requireNonNull", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
        // root = (Root) root;
        mv.visitTypeInsn(Opcodes.CHECKCAST, AsmUtil.toInternalName(rootClazz));
        mv.visitVarInsn(Opcodes.ASTORE, 1);

        Type parentType = _emitEnsureChain(mv, path, rootType, rootClazz);
        _emitPutLast(mv, path, Types.rawClazz(parentType));

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }


    private void _emitPutLast(MethodVisitor mv, JsonPath path, Class<?> parentClazz) {
        int pathLength = path.length();
        int parentLocal = pathLength == 2 ? 1 : pathLength + 1;
        int scratchLocal = pathLength == 2 ? 3 : pathLength + 2;
        PathSegment last = path.tail();
        if (last instanceof PathSegment.Name) {
            _emitPutChildByName(mv, path, (PathSegment.Name) last, parentClazz, parentLocal, 2, true);
        } else if (last instanceof PathSegment.Index) {
            _emitPutChildByIndex(mv, path, (PathSegment.Index) last, parentClazz, parentLocal, 2, scratchLocal, true);
        } else if (last instanceof PathSegment.Append) {
            _emitPutChildByAppend(mv, path, (PathSegment.Append) last, parentClazz, parentLocal, 2, true);
        } else {
            throw new JsonException("unsupported last path token '" + last +
                    "'; put() expected Name, Index, or Append token");
        }
    }


    private Type _emitPutChain(MethodVisitor mv, JsonPath path, Type rootType, Class<?> rootClazz) {
        // Root _3 = root; ... Parent _n = _n_1.child; if (_n == null) throw; return parentType;
        PathSegment[] segments = path.segments();
        String expr = path.toExpr();
        Type currentType = rootType;
        Class<?> currentClazz = rootClazz;
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        for (int i = 1; i < segments.length - 1; i++) {
            currentType = _emitGetChild(mv, i + 2, segments[i], currentType, currentClazz, expr);
            currentClazz = Types.rawClazz(currentType);

            // if (_n == null) throw parent-missing;
            mv.visitVarInsn(Opcodes.ALOAD, i + 3);
            Label notNull = new Label();
            mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
            _emitThrow(mv, "cannot put value at path '" + expr + "': parent container does not exist");
            mv.visitLabel(notNull);
        }
        return currentType;
    }


    private Type _emitEnsureChain(MethodVisitor mv, JsonPath path, Type rootType, Class<?> rootClazz) {
        // Root _3 = root; ... if (_child == null) { _child = JsonPath.createXxxContainer(...); parent.child = _child; }
        PathSegment[] segments = path.segments();
        String expr = path.toExpr();
        Type currentType = rootType;
        Class<?> currentClazz = rootClazz;
        int currentLocal = 3;
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ASTORE, currentLocal);

        for (int i = 1; i < segments.length - 1; i++) {
            PathSegment ps = segments[i];
            Type childType;
            int childLocal = currentLocal + 1;

            if (ps instanceof PathSegment.Name) {
                childType = _emitEnsureChildByName(mv, path, (PathSegment.Name) ps, segments[i + 1],
                        currentType, currentClazz, currentLocal, childLocal);
            } else if (ps instanceof PathSegment.Index) {
                childType = _emitEnsureChildByIndex(mv, path, (PathSegment.Index) ps, segments[i + 1],
                        currentType, currentClazz, currentLocal, childLocal);
            } else if (ps instanceof PathSegment.Append) {
                childType = _emitEnsureChildByAppend(mv, path, (PathSegment.Append) ps, segments[i + 1],
                        currentType, currentClazz, currentLocal, childLocal);
            } else {
                throw new JsonException("unsupported path token '" + ps + "' at '" + expr + "'");
            }

            Class<?> childClazz = Types.rawClazz(childType);
            if (childClazz != Object.class && !childClazz.isPrimitive()) {
                mv.visitVarInsn(Opcodes.ALOAD, childLocal);
                mv.visitTypeInsn(Opcodes.CHECKCAST, AsmUtil.toInternalName(childClazz));
                mv.visitVarInsn(Opcodes.ASTORE, childLocal);
            }

            currentType = childType;
            currentClazz = Types.box(childClazz);
            currentLocal = childLocal;
        }
        return currentType;
    }

    private Type _emitEnsureChildByName(MethodVisitor mv, JsonPath path, PathSegment.Name ps, PathSegment next,
                                        Type currentType, Class<?> currentClazz,
                                        int currentLocal, int childLocal) {
        // Child _next = parent.name; if null, create a container matching the next segment and write it back.
        String expr = path.toExpr();
        String name = ps.name;
        Type childType;
        if (Object.class == currentClazz) {
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                    "getInObject", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);
            childType = Object.class;
        } else if (Map.class.isAssignableFrom(currentClazz)) {
            Type vvt = Types.resolveTypeArgument(currentType, Map.class, 1);
            Class<?> vvc = Types.rawClazz(vvt);
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map",
                    "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            AsmUtil.emitCastOrBox(mv, vvc);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);
            childType = vvt;
        } else if (JsonObject.class == currentClazz) {
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitLdcInsn(name);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_OBJECT,
                    "getNode", "(Ljava/lang/String;)Ljava/lang/Object;", false);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);
            childType = Object.class;
        } else {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(currentClazz).pojoInfo;
            if (pi == null) {
                throw new JsonException("cannot resolve property '" + name +
                        "' on " + currentClazz.getName() + " at '" + expr + "'");
            }

            NodeRegistry.PropertyInfo propInfo = pi.properties.get(name);
            if (propInfo != null) {
                Type vvt = propInfo.type;
                Class<?> vvc = Types.rawClazz(vvt);
                if (propInfo.publicField != null) {
                    mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                    mv.visitFieldInsn(Opcodes.GETFIELD, AsmUtil.toInternalName(currentClazz),
                            propInfo.publicField.getName(), org.objectweb.asm.Type.getDescriptor(vvc));
                    AsmUtil.emitCastOrBox(mv, vvc);
                } else if (propInfo.publicGetter != null) {
                    mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.toInternalName(currentClazz),
                            propInfo.publicGetter.getName(), "()" + org.objectweb.asm.Type.getDescriptor(vvc), false);
                    AsmUtil.emitCastOrBox(mv, vvc);
                } else if (propInfo.publicSetter != null) {
                    throw new JsonException("property '" + name + "' on " + currentClazz.getName() +
                            " is not readable through a public field or public getter at '" + expr + "'");
                } else {
                    throw new JsonException("property '" + name + "' on " + currentClazz.getName() +
                            " is not writable through a public field or public setter at '" + expr + "'");
                }
                mv.visitVarInsn(Opcodes.ASTORE, childLocal);
                childType = vvt;
            } else if (pi.isJojo) {
                mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_OBJECT,
                        "getNode", "(Ljava/lang/String;)Ljava/lang/Object;", false);
                mv.visitVarInsn(Opcodes.ASTORE, childLocal);
                childType = Object.class;
            } else {
                throw new JsonException("cannot resolve property '" + name + "' on " + currentClazz.getName() +
                        " at '" + expr + "': no property found and target is not a JOJO dynamic object");
            }
        }

        mv.visitVarInsn(Opcodes.ALOAD, childLocal);
        Label notNull = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);

        Class<?> childClazz = Types.rawClazz(childType);
        String methodName = next instanceof PathSegment.Name ? "createObjectContainer" : "createArrayContainer";
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(childClazz));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES, methodName,
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        mv.visitVarInsn(Opcodes.ASTORE, childLocal);
        _emitPutChildByName(mv, path, ps, currentClazz, currentLocal, childLocal, false);

        mv.visitLabel(notNull);
        return childType;
    }

    private Type _emitEnsureChildByIndex(MethodVisitor mv, JsonPath path, PathSegment.Index ps, PathSegment next,
                                         Type currentType, Class<?> currentClazz,
                                         int currentLocal, int childLocal) {
        String expr = path.toExpr();
        int idx = ps.index;
        if (idx < 0) {
            throw new JsonException("ASM CompiledPath does not support negative array index " + idx +
                    " at '" + expr + "'");
        }
        Type childType;
        if (Object.class == currentClazz) {
            childType = Object.class;
        } else if (List.class.isAssignableFrom(currentClazz)) {
            childType = Types.resolveTypeArgument(currentType, List.class, 0);
        } else if (JsonArray.class.isAssignableFrom(currentClazz)) {
            childType = Object.class;
        } else if (currentClazz.isArray()) {
            childType = currentClazz.getComponentType();
        } else if (Set.class.isAssignableFrom(currentClazz)) {
            throw new JsonException("cannot read by index from unordered Set type " +
                    currentClazz.getName() + " at '" + expr + "'");
        } else {
            throw new JsonException("expected array-like target before index at '" + expr +
                    "', but was " + currentClazz.getName());
        }

        Class<?> childClazz = Types.rawClazz(childType);
        if (Object.class == currentClazz) {
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitLdcInsn(idx);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                    "getInArray", "(Ljava/lang/Object;I)Ljava/lang/Object;", false);
            AsmUtil.emitCastOrBox(mv, childClazz);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);
        } else if (List.class.isAssignableFrom(currentClazz)) {
            int sizeLocal = childLocal + 1;
            Label exists = new Label();
            Label missing = new Label();
            Label done = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List",
                    "size", "()I", true);
            mv.visitVarInsn(Opcodes.ISTORE, sizeLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, sizeLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, exists);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, sizeLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, missing);
            _emitThrow(mv, "cannot ensure path segment at index " + idx + " at '" + expr +
                    "': indexed array access requires an existing element; use append path syntax instead");

            mv.visitLabel(exists);
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitLdcInsn(idx);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List",
                    "get", "(I)Ljava/lang/Object;", true);
            AsmUtil.emitCastOrBox(mv, childClazz);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);
            mv.visitJumpInsn(Opcodes.GOTO, done);

            mv.visitLabel(missing);
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);

            mv.visitLabel(done);
        } else if (JsonArray.class.isAssignableFrom(currentClazz)) {
            int sizeLocal = childLocal + 1;
            Label exists = new Label();
            Label missing = new Label();
            Label done = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_ARRAY,
                    "size", "()I", false);
            mv.visitVarInsn(Opcodes.ISTORE, sizeLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, sizeLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, exists);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, sizeLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, missing);
            _emitThrow(mv, "cannot ensure path segment at index " + idx + " at '" + expr +
                    "': indexed array access requires an existing element; use append path syntax instead");

            mv.visitLabel(exists);
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitLdcInsn(idx);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_ARRAY,
                    "getNode", "(I)Ljava/lang/Object;", false);
            AsmUtil.emitCastOrBox(mv, childClazz);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);
            mv.visitJumpInsn(Opcodes.GOTO, done);

            mv.visitLabel(missing);
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);

            mv.visitLabel(done);
        } else if (currentClazz.isArray()) {
            Label exists = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitLdcInsn(idx);
            mv.visitJumpInsn(Opcodes.IF_ICMPGT, exists);
            _emitThrow(mv, "cannot ensure path segment at index " + idx + " at '" + expr +
                    "': indexed array access requires an existing element; use append path syntax instead");

            mv.visitLabel(exists);
            mv.visitVarInsn(Opcodes.ALOAD, currentLocal);
            mv.visitLdcInsn(idx);
            AsmUtil.emitArrayLoad(mv, childClazz);
            AsmUtil.emitCastOrBox(mv, childClazz);
            mv.visitVarInsn(Opcodes.ASTORE, childLocal);
        }

        mv.visitVarInsn(Opcodes.ALOAD, childLocal);
        Label notNull = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);

        String methodName = next instanceof PathSegment.Name ? "createObjectContainer" : "createArrayContainer";
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(childClazz));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES, methodName,
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        mv.visitVarInsn(Opcodes.ASTORE, childLocal);
        _emitPutChildByIndex(mv, path, ps, currentClazz, currentLocal, childLocal, childLocal + 1, false);

        mv.visitLabel(notNull);
        return childType;
    }

    private Type _emitEnsureChildByAppend(MethodVisitor mv, JsonPath path, PathSegment.Append ps, PathSegment next,
                                          Type currentType, Class<?> currentClazz,
                                          int currentLocal, int childLocal) {
        String expr = path.toExpr();
        Type childType;
        if (Object.class == currentClazz) {
            childType = Object.class;
        } else if (List.class.isAssignableFrom(currentClazz)) {
            childType = Types.resolveTypeArgument(currentType, List.class, 0);
        } else if (JsonArray.class.isAssignableFrom(currentClazz)) {
            childType = Object.class;
        } else if (Set.class.isAssignableFrom(currentClazz)) {
            childType = Types.resolveTypeArgument(currentType, Set.class, 0);
        } else if (currentClazz.isArray()) {
            childType = currentClazz.getComponentType();
        } else {
            throw new JsonException("expected array-like target before append at '" + expr +
                    "', but was " + currentClazz.getName());
        }

        Class<?> childClazz = Types.rawClazz(childType);
        String methodName = next instanceof PathSegment.Name ? "createObjectContainer" : "createArrayContainer";
        mv.visitLdcInsn(org.objectweb.asm.Type.getType(childClazz));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES, methodName,
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        mv.visitVarInsn(Opcodes.ASTORE, childLocal);
        _emitPutChildByAppend(mv, path, ps, currentClazz, currentLocal, childLocal, false);
        return childType;
    }


    private void _emitPutChildByName(MethodVisitor mv, JsonPath path, PathSegment.Name segment,
                                     Class<?> parentClazz, int parentLocal, int valueLocal,
                                     boolean _emitReturn) {
        String expr = path.toExpr();
        String name = segment.name;
        if (Object.class == parentClazz) {
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitLdcInsn(name);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                    "putInObject", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitInsn(_emitReturn ? Opcodes.ARETURN : Opcodes.POP);
        } else if (Map.class.isAssignableFrom(parentClazz)) {
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitLdcInsn(name);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map",
                    "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitInsn(_emitReturn ? Opcodes.ARETURN : Opcodes.POP);
        } else if (JsonObject.class.isAssignableFrom(parentClazz)) {
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitTypeInsn(Opcodes.CHECKCAST, AsmUtil.INAME_JSON_OBJECT);
            mv.visitLdcInsn(name);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_OBJECT,
                    "put", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitInsn(_emitReturn ? Opcodes.ARETURN : Opcodes.POP);
        } else {
            NodeRegistry.PojoInfo pi = NodeRegistry.registerTypeInfo(parentClazz).pojoInfo;
            NodeRegistry.PropertyInfo propInfo = pi == null ? null : pi.properties.get(name);
            if (propInfo != null && propInfo.publicField != null) {
                Class<?> fieldClazz = propInfo.publicField.getType();
                mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
                mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
                AsmUtil.emitCastOrUnbox(mv, fieldClazz);
                mv.visitFieldInsn(Opcodes.PUTFIELD, AsmUtil.toInternalName(parentClazz),
                        propInfo.publicField.getName(), org.objectweb.asm.Type.getDescriptor(fieldClazz));
            } else if (propInfo != null && propInfo.publicSetter != null) {
                Method setter = propInfo.publicSetter;
                Class<?> returnClazz = setter.getReturnType();
                mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
                mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
                AsmUtil.emitCastOrUnbox(mv, setter.getParameterTypes()[0]);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.toInternalName(parentClazz),
                        setter.getName(), org.objectweb.asm.Type.getMethodDescriptor(setter), false);
                if (returnClazz != void.class) {
                    mv.visitInsn(returnClazz == long.class || returnClazz == double.class ? Opcodes.POP2 : Opcodes.POP);
                }
            } else {
                if (pi == null) {
                    throw new JsonException("cannot write property '" + name +
                            "' on " + parentClazz.getName() + " at '" + expr + "'");
                }
                throw new JsonException("property '" + name + "' on " + parentClazz.getName() +
                        " is not writable through a public field or public setter at '" + expr + "'");
            }
            if (_emitReturn) {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
    }

    private void _emitPutChildByIndex(MethodVisitor mv, JsonPath path, PathSegment.Index segment,
                                      Class<?> parentClazz, int parentLocal, int valueLocal,
                                      int scratchLocal, boolean _emitReturn) {
        String expr = path.toExpr();
        int idx = segment.index;
        if (idx < 0) {
            throw new JsonException("ASM CompiledPath does not support negative array index " + idx +
                    " at '" + expr + "'");
        }
        if (Object.class == parentClazz) {
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                    "putInArray", "(Ljava/lang/Object;ILjava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitInsn(_emitReturn ? Opcodes.ARETURN : Opcodes.POP);
        } else if (List.class.isAssignableFrom(parentClazz)) {
            Label append = new Label();
            Label replace = new Label();
            Label done = new Label();

            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List",
                    "size", "()I", true);
            mv.visitVarInsn(Opcodes.ISTORE, scratchLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, scratchLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, append);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, scratchLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, replace);
            _emitThrow(mv, "cannot set at index " + idx + " in List at '" + expr + "'");

            mv.visitLabel(append);
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List",
                    "add", "(Ljava/lang/Object;)Z", true);
            mv.visitInsn(Opcodes.POP);
            if (_emitReturn) {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                mv.visitJumpInsn(Opcodes.GOTO, done);
            }

            mv.visitLabel(replace);
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List",
                    "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true);
            if (_emitReturn) {
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                mv.visitInsn(Opcodes.POP);
                mv.visitLabel(done);
            }
        } else if (JsonArray.class.isAssignableFrom(parentClazz)) {
            Label append = new Label();
            Label replace = new Label();
            Label done = new Label();

            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_ARRAY,
                    "size", "()I", false);
            mv.visitVarInsn(Opcodes.ISTORE, scratchLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, scratchLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, append);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, scratchLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, replace);
            _emitThrow(mv, "cannot set at index " + idx + " in JsonArray at '" + expr + "'");

            mv.visitLabel(append);
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_ARRAY,
                    "add", "(Ljava/lang/Object;)V", false);
            if (_emitReturn) {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                mv.visitJumpInsn(Opcodes.GOTO, done);
            }

            mv.visitLabel(replace);
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_ARRAY,
                    "set", "(ILjava/lang/Object;)Ljava/lang/Object;", false);
            if (_emitReturn) {
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                mv.visitInsn(Opcodes.POP);
                mv.visitLabel(done);
            }
        } else if (parentClazz.isArray()) {
            Class<?> componentClazz = parentClazz.getComponentType();
            Label replace = new Label();

            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitVarInsn(Opcodes.ISTORE, scratchLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ILOAD, scratchLocal);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, replace);
            _emitThrow(mv, "cannot set at index " + idx + " in Array at '" + expr + "'");

            mv.visitLabel(replace);
            if (_emitReturn) {
                mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
                mv.visitLdcInsn(idx);
                AsmUtil.emitArrayLoad(mv, componentClazz);
                AsmUtil.emitCastOrBox(mv, componentClazz);
            }
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitLdcInsn(idx);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            AsmUtil.emitCastOrUnbox(mv, componentClazz);
            AsmUtil.emitArrayStore(mv, componentClazz);
            if (_emitReturn) {
                mv.visitInsn(Opcodes.ARETURN);
            }
        } else if (Set.class.isAssignableFrom(parentClazz)) {
            throw new JsonException("cannot set by index on unordered Set type " +
                    parentClazz.getName() + " at '" + expr + "'");
        } else {
            throw new JsonException("expected array-like target before index [" + idx +
                    "] at '" + expr + "', but was " + parentClazz.getName());
        }
    }

    private void _emitPutChildByAppend(MethodVisitor mv, JsonPath path, PathSegment.Append segment,
                                       Class<?> parentClazz, int parentLocal, int valueLocal,
                                       boolean _emitReturn) {
        String expr = path.toExpr();
        if (Object.class == parentClazz) {
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, AsmUtil.INAME_NODES,
                    "addInArray", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
        } else if (List.class.isAssignableFrom(parentClazz)) {
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List",
                    "add", "(Ljava/lang/Object;)Z", true);
            mv.visitInsn(Opcodes.POP);
        } else if (JsonArray.class.isAssignableFrom(parentClazz)) {
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitTypeInsn(Opcodes.CHECKCAST, AsmUtil.INAME_JSON_ARRAY);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AsmUtil.INAME_JSON_ARRAY,
                    "add", "(Ljava/lang/Object;)V", false);
        } else if (Set.class.isAssignableFrom(parentClazz)) {
            mv.visitVarInsn(Opcodes.ALOAD, parentLocal);
            mv.visitVarInsn(Opcodes.ALOAD, valueLocal);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set",
                    "add", "(Ljava/lang/Object;)Z", true);
            mv.visitInsn(Opcodes.POP);
        } else if (parentClazz.isArray()) {
            throw new JsonException("cannot append to Java array type " +
                    parentClazz.getName() + " at '" + expr + "'");
        } else {
            throw new JsonException("expected array-like target before append at '" +
                    expr + "', but was " + parentClazz.getName());
        }
        if (_emitReturn) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ARETURN);
        }
    }



    private void _emitThrow(MethodVisitor mv, String message) {
        // throw new JsonException(message);
        mv.visitTypeInsn(Opcodes.NEW, AsmUtil.toInternalName(JsonException.class));
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(message);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, AsmUtil.toInternalName(JsonException.class),
                "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
    }

}
