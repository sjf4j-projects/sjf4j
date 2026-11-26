package org.sjf4j.processor.mapping;

import org.sjf4j.annotation.mapping.NullValuePolicy;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.NodeReadEmitter;
import org.sjf4j.processor.access.ResolvedStep;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.type.TypeSystem;

import java.util.List;
import javax.lang.model.type.TypeMirror;

/** Emits source reads, presence guards and direct/adjacent read optimizations. */
final class MappingReadEmitter {

    private final TypeSystem types;
    private final NodeReadEmitter reads;

    MappingReadEmitter(CompilationContext context) {
        this.types = context.types;
        this.reads = new NodeReadEmitter(types);
    }

    boolean emitAutomaticPresenceGuard(
            JavaWriter out,
            MappingPlan plan,
            MappingCompiler.Assignment assignment) {

        if (assignment.rule() != null ||
                assignment.value().kind() !=
                        MappingCompiler.Value.Kind.READ) {

            return false;
        }

        MappingCompiler.Read read =
                assignment.value()
                        .read();

        if (read.steps()
                .size() != 1 ||
                !(read.steps()
                        .get(0)
                        .segment() instanceof
                        PathSegment.Name)) {

            return false;
        }

        NodeAccess access =
                read.steps()
                        .get(0)
                        .access();

        String source =
                read.root()
                        .getSimpleName()
                        .toString();

        String key =
                JavaWriter.stringLiteral(
                        ((PathSegment.Name) read.steps()
                                .get(0)
                                .segment()).name);

        String present;

        switch (access.kind()) {
            case MAP:
                present = source + ".containsKey(" + key + ")";
                break;

            case JSON_OBJECT:
                present = source + ".containsKey(" + key + ")";
                break;

            case DYNAMIC:
                present = "org.sjf4j.node.NodeWalker.containsInObject(" +
                        source + ", " + key + ")";
                break;

            default:
                return false;
        }

        if (!MappingEmissionSupport.rootKnownNonNull(plan)) {
            present = source + " != null && " + present;
        }

        out.beginBlock(
                "if (" +
                        present +
                        ")");

        return true;
    }

    /**
     * Groups only adjacent direct property reads from the same nullable source.
     * Accessors retain their original order; converters and computed values
     * are excluded because their null and evaluation semantics are user-owned.
     */
    int adjacentDirectReadEnd(
            MappingPlan plan,
            List<MappingCompiler.ConstructorArgument> arguments,
            int start) {

        if (plan.sources().size() < 2 ||
                !isAdjacentDirectRead(arguments.get(start).value())) {
            return start + 1;
        }

        MappingCompiler.Read first = arguments.get(start).value().read();
        int end = start + 1;
        while (end < arguments.size() &&
                isAdjacentDirectRead(arguments.get(end).value()) &&
                first.root().equals(arguments.get(end).value().read().root())) {
            end++;
        }
        return end;
    }

    private boolean isAdjacentDirectRead(MappingCompiler.Value value) {
        if (value.kind() != MappingCompiler.Value.Kind.READ ||
                value.conversion() == null ||
                value.conversion().kind() != ConverterResolver.Conversion.Kind.DIRECT) {
            return false;
        }

        MappingCompiler.Read read = value.read();
        return !read.rootValue() &&
                !read.rootType().getKind().isPrimitive() &&
                read.steps().size() == 1 &&
                read.steps().get(0).segment() instanceof PathSegment.Name &&
                read.steps().get(0).access().kind() == NodeAccess.Kind.PROPERTY;
    }

    /**
     * Hoists only the common source null check. Getter calls are emitted in
     * constructor-argument order, with the same missing-source defaults as
     * independent emitRead calls (including primitive-to-boxed assignments).
     */
    void emitAdjacentDirectReads(
            JavaWriter out,
            NameAllocator names,
            MappingPlan plan,
            List<MappingCompiler.ConstructorArgument> arguments,
            int start,
            int end,
            String[] values) {

        MappingCompiler.Read first = arguments.get(start).value().read();
        String source = first.root().getSimpleName().toString();
        String[] locals = new String[end - start];

        for (int i = start; i < end; i++) {
            MappingCompiler.Value value = arguments.get(i).value();
            MappingCompiler.Read read = value.read();
            boolean primitive = isDirectPrimitiveRead(plan, value, true);
            String local = names.newName("sourceValue");
            locals[i - start] = local;

            out.line((primitive ? read.type().toString() : JavaExpressions.boxedType(types, read.type())) +
                    " " + local + " = " +
                    (primitive ? MappingEmissionSupport.primitiveDefault(read.type().getKind()) : "null") + ";");

            if (value.type().getKind().isPrimitive() && !primitive) {
                values[i] = "(" + local + " == null ? " +
                        MappingEmissionSupport.primitiveDefault(value.type().getKind()) + " : " + local + ")";
            } else {
                values[i] = local;
            }
        }

        out.beginBlock("if (" + source + " != null)");
        for (int i = start; i < end; i++) {
            ResolvedStep step =
                    arguments.get(i).value().read().steps().get(0);
            out.line(locals[i - start] + " = " +
                    readExpression(source, step.segment(), step.access()) + ";");
        }
        out.endBlock();
    }

    /**
     * Avoid an intermediate local for a direct, one-step POJO property read
     * that is used exactly once in a property assignment. Unlike null-ignore
     * reference updates, these writes do not need to test the value first.
     *
     * Only single-source roots qualify: emitNullRootGuard has already proved
     * their root non-null. Constructor arguments and nested paths retain the
     * existing evaluation order and null propagation.
     */
    String inlineDirectPropertyRead(MappingPlan plan, MappingCompiler.Assignment assignment) {
        if (assignment.target().kind() != MappingCompiler.Target.Kind.PROPERTY ||
                !MappingEmissionSupport.rootKnownNonNull(plan)) {
            return null;
        }

        MappingCompiler.Value value = assignment.value();
        if (value.kind() != MappingCompiler.Value.Kind.READ ||
                value.conversion() == null ||
                value.conversion().kind() != ConverterResolver.Conversion.Kind.DIRECT) {
            return null;
        }

        boolean primitiveDirect = isDirectPrimitiveRead(plan, value, false);
        if (plan.update()) {
            // Primitive reads cannot be null and need no IGNORE guard.
            if (!primitiveDirect) return null;
        } else {
            // Create mappings may explicitly request null-ignore behavior.
            if (plan.policy().nulls == NullValuePolicy.IGNORE) return null;
            if (assignment.target().type().getKind().isPrimitive() && !primitiveDirect) {
                return null;
            }
        }

        MappingCompiler.Read read = value.read();
        if (read.rootValue() || read.steps().size() != 1) return null;

        ResolvedStep step = read.steps().get(0);
        if (!(step.segment() instanceof PathSegment.Name) ||
                step.access().kind() != NodeAccess.Kind.PROPERTY) {
            return null;
        }

        return readExpression(read.root().getSimpleName().toString(),
                step.segment(), step.access());
    }

    /**
     * A primitive declared by a getter is safe to keep unboxed when it is
     * directly assigned to a primitive target. A nullable parent is allowed
     * only for constructor arguments, which use Java primitive defaults.
     * Indexed reads retain their nullable/out-of-bounds behavior.
     */
    boolean isDirectPrimitiveRead(MappingPlan plan, MappingCompiler.Value value,
                                          boolean constructorArgument) {
        if (value.kind() != MappingCompiler.Value.Kind.READ ||
                !value.type().getKind().isPrimitive() ||
                value.conversion() == null ||
                value.conversion().kind() != ConverterResolver.Conversion.Kind.DIRECT) {
            return false;
        }

        MappingCompiler.Read read = value.read();
        if (!read.type().getKind().isPrimitive()) return false;
        if (read.rootValue()) return true;
        if (read.steps().isEmpty() ||
                read.steps().get(read.steps().size() - 1).segment() instanceof PathSegment.Index) {
            return false;
        }

        return constructorArgument || (MappingEmissionSupport.rootKnownNonNull(plan) && read.steps().size() == 1);
    }

    String emitRead(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Read read,
            boolean rootKnownNonNull) {
        return emitRead(out, names, read, rootKnownNonNull, false);
    }

    String emitRead(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Read read,
            boolean rootKnownNonNull,
            boolean primitiveDirect) {
        return emitRead(out, names, read, rootKnownNonNull, primitiveDirect, null, null);
    }

    String emitRead(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Read read,
            boolean rootKnownNonNull,
            boolean primitiveDirect,
            String finalName) {
        return emitRead(out, names, read, rootKnownNonNull, primitiveDirect, finalName, null);
    }

    String emitRead(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Read read,
            boolean rootKnownNonNull,
            boolean primitiveDirect,
            String finalName,
            String finalReadFlag) {

        String current =
                read.root()
                        .getSimpleName()
                        .toString();

        if (read.rootValue()) return current;

        TypeMirror currentType =
                read.rootType();

        boolean first = true;

        for (ResolvedStep step :
                read.steps()) {

            NodeAccess access =
                    step.access();

            PathSegment segment =
                    step.segment();

            TypeMirror valueType =
                    access.readType();

            boolean last = step == read.steps().get(read.steps().size() - 1);
            String value = last && finalName != null
                    ? finalName : names.newName("sourceValue");

            boolean parentPrimitive =
                    currentType.getKind()
                            .isPrimitive();

            boolean guardParent =
                    !parentPrimitive &&
                            !(first &&
                                    rootKnownNonNull);
            boolean primitiveLast = primitiveDirect &&
                    step == read.steps().get(read.steps().size() - 1) &&
                    valueType.getKind().isPrimitive() &&
                    !(segment instanceof PathSegment.Index);

            if (guardParent) {
                // An optional primitive constructor argument defaults to zero
                // (or false), while a boxed/reference result propagates null.
                out.line(
                        (primitiveLast ? valueType.toString() : JavaExpressions.boxedType(types, valueType)) +
                                " " +
                                value +
                                " = " +
                                (primitiveLast ? MappingEmissionSupport.primitiveDefault(valueType.getKind()) : "null") +
                                ";");

                out.beginBlock(
                        "if (" +
                                current +
                                " != null)");

            } else if (segment instanceof
                    PathSegment.Index) {

                out.line(
                        JavaExpressions.boxedType(types, valueType) +
                                " " +
                                value +
                                " = null;");
            }

            if (segment instanceof
                    PathSegment.Index) {

                emitIndexedRead(
                        out,
                        names,
                        current,
                        value,
                        valueType,
                        access,
                        ((PathSegment.Index) segment)
                                .index);

            } else {
                String expression =
                        readExpression(current, segment, access);

                if (guardParent) {
                    out.line(
                            value +
                                    " = " +
                                    expression +
                                    ";");
                } else {
                    out.line(
                            (primitiveLast ? valueType.toString() : JavaExpressions.boxedType(types, valueType)) +
                                    " " +
                                    value +
                                    " = " +
                                    expression +
                                    ";");
                }
            }

            if (last && finalReadFlag != null) {
                out.line(finalReadFlag + " = true;");
            }

            if (guardParent) {
                out.endBlock();
            }

            current =
                    value;

            currentType =
                    valueType;

            first = false;
        }

        return current;
    }

    /** The root guard proves only the root itself, never a property or indexed read. */
    boolean readKnownNonNull(MappingPlan plan, MappingCompiler.Read read) {
        return read.rootValue() && (read.rootType().getKind().isPrimitive() || MappingEmissionSupport.rootKnownNonNull(plan));
    }

    private void emitIndexedRead(
            JavaWriter out,
            NameAllocator names,
            String owner,
            String target,
            TypeMirror valueType,
            NodeAccess access,
            int rawIndex) {

        String size =
                MappingEmissionSupport.sizeExpression(
                        owner,
                        access.kind());

        if (size == null) {
            out.line(
                    target +
                            " = " +
                            readExpression(owner, new PathSegment.Index(PathSegment.Root.INSTANCE, rawIndex), access) +
                            ";");

            return;
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

        out.beginBlock(
                "if (" +
                        index +
                        " >= 0 && " +
                        index +
                        " < " +
                        size +
                        ")");

        out.line(
                target +
                        " = " +
                        indexedReadExpression(owner, index, access) +
                        ";");

        out.endBlock();
    }

    private String readExpression(String owner, PathSegment segment,
                                  NodeAccess access) {
        if (segment instanceof PathSegment.Name) {
            return reads.name(access, owner,
                    JavaWriter.stringLiteral(((PathSegment.Name) segment).name));
        }
        if (segment instanceof PathSegment.Index) {
            return reads.index(access, owner,
                    Integer.toString(((PathSegment.Index) segment).index));
        }
        throw new IllegalStateException("unsupported source segment: " + segment);
    }

    private String indexedReadExpression(String owner, String index,
                                         NodeAccess access) {
        return reads.index(access, owner, index);
    }

}
