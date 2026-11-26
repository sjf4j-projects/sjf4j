package org.sjf4j.processor.mapping;

import org.sjf4j.NodeKind;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.NodeReadEmitter;
import org.sjf4j.processor.access.NodeWriteEmitter;
import org.sjf4j.processor.access.ResolvedStep;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.type.TypeSystem;

import java.util.List;
import javax.lang.model.type.TypeMirror;

/** Emits direct target access and strict/ensure path writes. */
final class MappingWriteEmitter {

    private final TypeSystem types;
    private final NodeReadEmitter reads;

    MappingWriteEmitter(TypeSystem types) {
        this.types = types;
        this.reads = new NodeReadEmitter(types);
    }

    void emitPathWrite(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Target target,
            String root,
            String value) {

        List<ResolvedStep> steps =
                target.steps();

        String current =
                root;

        int opened =
                0;

        for (int i = 0;
             i < steps.size() - 1;
             i++) {

            ResolvedStep step =
                    steps.get(i);

            NodeAccess access =
                    step.access();

            PathSegment segment =
                    step.segment();

            String child =
                    names.newName(
                            "parent");

            String index =
                    null;

            if (segment instanceof
                    PathSegment.Index) {

                index =
                        emitIndex(
                                out,
                                names,
                                current,
                                access,
                                ((PathSegment.Index) segment)
                                        .index,
                                target.mode());
            }

            out.line(
                    JavaExpressions.boxedType(types,
                            access.readType()) +
                            " " +
                            child +
                            " = " +
                            readTargetExpression(
                                    current,
                                    segment,
                                    index,
                                    access) +
                            ";");

            switch (target.mode()) {
                case STRICT:
                    out.line(
                            "org.sjf4j.util.Asserts.notNull(" +
                                    child +
                                    ", " +
                                    JavaWriter.stringLiteral(
                                            "missing target path parent") +
                                    ");");
                    break;

                case IF_PARENT_PRESENT:
                    out.beginBlock(
                            "if (" +
                                    child +
                                    " != null)");

                    opened++;
                    break;

                case ENSURE:
                    out.beginBlock(
                            "if (" +
                                    child +
                                    " == null)");

                    String created =
                            ensureExpression(
                                    access.writeType(),
                                    steps.get(i + 1)
                                            .segment());

                    out.line(
                            child +
                                    " = " +
                                    created +
                                    ";");

                    emitNodeWrite(
                            out,
                            current,
                            segment,
                            index,
                            access,
                            child);

                    out.endBlock();
                    break;

                default:
                    throw new AssertionError(
                            target.mode());
            }

            current =
                    child;
        }

        ResolvedStep tail =
                steps.get(
                        steps.size() - 1);

        PathSegment segment =
                tail.segment();

        String index =
                null;

        if (segment instanceof
                PathSegment.Index) {

            index =
                    emitFinalIndex(
                            out,
                            names,
                            current,
                            tail.access(),
                            ((PathSegment.Index) segment)
                                    .index,
                            target.mode());
        }

        emitNodeWrite(
                out,
                current,
                segment,
                index,
                tail.access(),
                value);

        while (opened-- > 0) {
            out.endBlock();
        }
    }

    private String emitIndex(
            JavaWriter out,
            NameAllocator names,
            String owner,
            NodeAccess access,
            int rawIndex,
            MappingPlan.WriteMode mode) {

        String size =
                MappingEmissionSupport.sizeExpression(
                        owner,
                        access.kind());

        if (size == null) {
            return Integer.toString(
                    rawIndex);
        }

        String index =
                names.newName("index");

        out.line(
                "int " +
                        index +
                        " = " +
                        rawIndex +
                        ";");

        out.line(
                "if (" +
                        index +
                        " < 0) " +
                        index +
                        " += " +
                        size +
                        ";");

        if (mode ==
                MappingPlan.WriteMode.IF_PARENT_PRESENT) {

            out.beginBlock(
                    "if (" +
                            index +
                            " >= 0 && " +
                            index +
                            " < " +
                            size +
                            ")");

            /*
             * The caller opens a second parent-present block. Keeping the
             * index scope open would complicate close tracking, so indexed
             * IF_PARENT traversal is emitted by a normal guarded expression.
             */
            out.endBlock();

        } else {
            out.beginBlock(
                    "if (" +
                            index +
                            " < 0 || " +
                            index +
                            " >= " +
                            size +
                            ")");

            out.line(
                    "throw new IndexOutOfBoundsException(\"target path index: \" + " +
                            index +
                            ");");

            out.endBlock();
        }

        return index;
    }

    private String emitFinalIndex(
            JavaWriter out,
            NameAllocator names,
            String owner,
            NodeAccess access,
            int rawIndex,
            MappingPlan.WriteMode mode) {

        String size =
                MappingEmissionSupport.sizeExpression(
                        owner,
                        access.kind());

        if (size == null) {
            return Integer.toString(
                    rawIndex);
        }

        String index =
                names.newName("index");

        out.line(
                "int " +
                        index +
                        " = " +
                        rawIndex +
                        ";");

        out.line(
                "if (" +
                        index +
                        " < 0) " +
                        index +
                        " += " +
                        size +
                        ";");

        return index;
    }

    void emitNodeWrite(
            JavaWriter out,
            String owner,
            String name,
            String index,
            NodeAccess access,
            String value) {

        if (name != null) {
            emitNodeWrite(
                    out,
                    owner,
                    new PathSegment.Name(
                            PathSegment.Root.INSTANCE,
                            name),
                    null,
                    access,
                    value);

            return;
        }

        throw new IllegalArgumentException(
                "name is required");
    }

    void emitNodeWrite(JavaWriter out, String owner, PathSegment segment,
                               String index, NodeAccess access, String value) {
        if (segment instanceof PathSegment.Name) {
            String key = JavaWriter.stringLiteral(((PathSegment.Name) segment).name);
            out.line(NodeWriteEmitter.name(access, owner, key, value) + ";");
            return;
        }

        if (segment instanceof PathSegment.Append) {
            out.line(NodeWriteEmitter.append(access, owner, value) + ";");
            return;
        }

        if (segment instanceof PathSegment.Index) {
            String actualIndex = index != null ? index :
                    Integer.toString(((PathSegment.Index) segment).index);
            if (access.kind() == NodeAccess.Kind.LIST || access.kind() == NodeAccess.Kind.JSON_ARRAY) {
                out.beginBlock("if (" + actualIndex + " == " + owner + ".size())");
                out.line(NodeWriteEmitter.append(access, owner, value) + ";");
                out.endBlock();
                out.beginBlock("else");
                out.line(NodeWriteEmitter.index(access, owner, actualIndex, value) + ";");
                out.endBlock();
            } else {
                out.line(NodeWriteEmitter.index(access, owner, actualIndex, value) + ";");
            }
            return;
        }

        throw new IllegalStateException("unsupported target segment: " + segment);
    }

    String readTargetExpression(
            String owner,
            String name,
            NodeAccess access) {

        return readTargetExpression(
                owner,
                new PathSegment.Name(
                        PathSegment.Root.INSTANCE,
                        name),
                null,
                access);
    }

    String readTargetExpression(String owner, PathSegment segment,
                                        String index, NodeAccess access) {
        if (segment instanceof PathSegment.Name) {
            return reads.name(access, owner,
                    JavaWriter.stringLiteral(((PathSegment.Name) segment).name));
        }
        if (segment instanceof PathSegment.Index) {
            String resolved = index != null ? index :
                    Integer.toString(((PathSegment.Index) segment).index);
            return reads.index(access, owner, resolved);
        }
        throw new IllegalStateException("unsupported target read: " + segment);
    }

    private String ensureExpression(
            TypeMirror type,
            PathSegment next) {

        type =
                types.concrete(type);

        if (type == null ||
                types.isObject(type)) {

            if (next instanceof
                    PathSegment.Index ||
                    next instanceof
                            PathSegment.Append) {

                return "new java.util.ArrayList<>()";
            }

            return "new java.util.LinkedHashMap<>()";
        }

        NodeKind kind =
                types.nodeKind(type);

        switch (kind) {
            case OBJECT_MAP:
                if (types.isSameErasure(
                        type,
                        types.mapType())) {

                    return "new java.util.LinkedHashMap<>()";
                }

                return JavaExpressions.newInstance(type.toString(), false);

            case ARRAY_LIST:
                if (types.isSameErasure(
                        type,
                        types.listType())) {

                    return "new java.util.ArrayList<>()";
                }

                return JavaExpressions.newInstance(type.toString(), false);

            case OBJECT_JSON_OBJECT:
                return "new org.sjf4j.JsonObject()";

            case ARRAY_JSON_ARRAY:
                return "new org.sjf4j.JsonArray()";

            case OBJECT_POJO:
            case OBJECT_JOJO:
            case ARRAY_JAJO:
                return JavaExpressions.newInstance(type.toString(), false);

            default:
                throw new IllegalStateException(
                        "cannot ensure target type " +
                                type);
        }
    }

}
