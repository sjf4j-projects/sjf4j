package org.sjf4j.processor.mapping;

import org.sjf4j.annotation.mapping.ArrayPolicy;
import org.sjf4j.annotation.mapping.ObjectPolicy;
import org.sjf4j.annotation.mapping.NullValuePolicy;
import org.sjf4j.NodeKind;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.type.TypeSystem;

import java.util.Map;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Emits assignment and collection/object update policies, delegating structural writes. */
final class MappingUpdateEmitter {

    private final CompilationContext context;
    private final TypeSystem types;
    private final MappingReadEmitter sourceReads;
    private final MappingWriteEmitter writes;
    private final MappingEmitter.ConversionWriter conversions;

    MappingUpdateEmitter(CompilationContext context, MappingReadEmitter sourceReads,
                         MappingWriteEmitter writes, MappingEmitter.ConversionWriter conversions) {
        this.context = context;
        this.types = context.types;
        this.sourceReads = sourceReads;
        this.writes = writes;
        this.conversions = conversions;
    }

    boolean emitDirectMapUpdate(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            MappingCompiler.Assignment assignment,
            String target,
            GeneratedClass generated) {

        MappingPlan plan =
                compiled.plan();

        MappingCompiler.Target destination =
                assignment.target();

        MappingCompiler.Value mapped =
                assignment.value();

        if (!plan.update() ||
                mapped.kind() !=
                        MappingCompiler.Value.Kind.READ ||
                mapped.conversion().kind() !=
                        ConverterResolver.Conversion.Kind.CONTAINER ||
                types.nodeKind(
                        destination.type()) !=
                        NodeKind.OBJECT_MAP) {

            return false;
        }

        NodeAccess access =
                destination.access();

        if (!access.readable()) {
            MappingEmissionSupport.error(context,
                    plan.method(),
                    generated,
                    "object update target '" +
                            destination.name() +
                            "' must be readable");

            return true;
        }

        String source =
                sourceReads.emitRead(
                        out,
                        names,
                        mapped.read(),
                        MappingEmissionSupport.rootKnownNonNull(plan));

        boolean ignoreNull =
                plan.policy().nulls == NullValuePolicy.IGNORE;

        MappingPlan.Rule rule =
                assignment.rule();

        ObjectPolicy policy = plan.objectPolicy(rule);

        if (ignoreNull) {
            out.beginBlock(
                    "if (" +
                            source +
                            " != null)");
        } else {
            out.beginBlock(
                    "if (" +
                            source +
                            " == null)");

            writes.emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    "null");

            out.endBlock();
            out.beginBlock("else");
        }

        String existing =
                names.newName(
                        "existing");

        out.line(
                JavaExpressions.boxedType(types,
                        access.readType()) +
                        " " +
                        existing +
                        " = " +
                        writes.readTargetExpression(
                                target,
                                destination.name(),
                                access) +
                        ";");

        out.beginBlock(
                "if (" +
                        existing +
                        " == null)");

        if (policy != ObjectPolicy.PUT_IF_PRESENT) {
            String value =
                    conversions.emit(
                            out,
                            names,
                            plan,
                            mapped.conversion(),
                            source,
                            mapped.read()
                                    .type(),
                            destination.type(),
                            true,
                            generated);

            writes.emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    value);
        }

        out.endBlock();
        out.beginBlock("else");

        conversions.emitMapUpdate(
                out,
                names,
                plan,
                mapped.conversion(),
                source,
                mapped.read()
                        .type(),
                destination.type(),
                existing,
                policy,
                true,
                generated);

        out.endBlock();
        out.endBlock();

        return true;
    }

    void emitAssignment(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            MappingCompiler.Assignment assignment,
            String target,
            String value,
            GeneratedClass generated) {

        MappingCompiler.Target destination =
                assignment.target();

        if (destination.kind() ==
                MappingCompiler.Target.Kind.PATH) {

            writes.emitPathWrite(
                    out,
                    names,
                    destination,
                    target,
                    value);

            return;
        }

        emitPropertyAssignment(
                out,
                names,
                compiled,
                assignment,
                target,
                value,
                generated);
    }

    private void emitPropertyAssignment(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            MappingCompiler.Assignment assignment,
            String target,
            String value,
            GeneratedClass generated) {

        MappingCompiler.Target destination =
                assignment.target();

        NodeAccess access =
                destination.access();

        TypeMirror targetType =
                destination.type();

        MappingPlan plan =
                compiled.plan();

        MappingPlan.Rule rule =
                assignment.rule();

        boolean ignoreNull = plan.policy().nulls == NullValuePolicy.IGNORE;

        NodeKind kind = types.nodeKind(targetType);

        if (plan.update() && types.isArrayNode(kind)) {

            ArrayPolicy arrayPolicy = plan.arrayPolicy(rule);

            emitArrayUpdate(out, names, destination, target, value,
                    arrayPolicy, ignoreNull, generated, plan);

            return;
        }

        if (plan.update() && MappingEmissionSupport.isMapLike(kind)) {

            ObjectPolicy objectPolicy = plan.objectPolicy(rule);

            if (kind != NodeKind.OBJECT_MAP && objectPolicy != ObjectPolicy.PUT) {
                MappingEmissionSupport.error(context, plan.method(), generated, "ObjectPolicy." + objectPolicy + " requires a Map target");

                return;
            }

            emitObjectUpdate(out, names, destination, target, value,
                    objectPolicy, ignoreNull, generated, plan);

            return;
        }

        // A proven primitive read cannot be null. Guarding it generates
        // unnecessary boxing and an always-true check under IGNORE.
        boolean primitiveValue = sourceReads.isDirectPrimitiveRead(plan, assignment.value(), false);
        if ((ignoreNull || targetType.getKind().isPrimitive()) && !primitiveValue) {
            out.beginBlock("if (" + value + " != null)");
            writes.emitNodeWrite(out, target, destination.name(), null, access, value);

            out.endBlock();

        } else {
            writes.emitNodeWrite(out, target, destination.name(), null, access, value);
        }
    }

    private void emitArrayUpdate(JavaWriter out, NameAllocator names, MappingCompiler.Target destination,
                                 String target, String value, ArrayPolicy policy, boolean ignoreNull,
                                 GeneratedClass generated, MappingPlan plan) {
        NodeAccess access = destination.access();

        if (!access.readable()) {
            MappingEmissionSupport.error(context, plan.method(), generated, "array update target '" + destination.name() + "' must be readable");

            return;
        }

        NodeKind existingKind = types.nodeKind(access.readType());

        if (existingKind ==
                NodeKind.ARRAY_SET &&
                policy == ArrayPolicy.SET) {

            MappingEmissionSupport.error(context,
                    plan.method(),
                    generated,
                    "ArrayPolicy.SET requires an indexed target");

            return;
        }

        if (access.readType()
                .getKind() ==
                TypeKind.ARRAY &&
                policy == ArrayPolicy.ADD) {

            MappingEmissionSupport.error(context,
                    plan.method(),
                    generated,
                    "ArrayPolicy." +
                            policy +
                            " cannot update a Java array in place");

            return;
        }

        if (types.writeElementType(
                access.readType()) == null) {

            MappingEmissionSupport.error(context,
                    plan.method(),
                    generated,
                    "array update target '" +
                            destination.name() +
                            "' element type is not writable");

            return;
        }

        value = materialize(
                out,
                names,
                destination.type(),
                value);

        if (ignoreNull) {
            out.beginBlock(
                    "if (" +
                            value +
                            " != null)");
        } else {
            out.beginBlock(
                    "if (" +
                            value +
                            " == null)");

            writes.emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    "null");

            out.endBlock();

            out.beginBlock("else");
        }

        String existing =
                names.newName(
                        "existing");

        out.line(
                JavaExpressions.boxedType(types,
                        access.readType()) +
                        " " +
                        existing +
                        " = " +
                        writes.readTargetExpression(
                                target,
                                destination.name(),
                                access) +
                        ";");

        out.beginBlock(
                "if (" +
                        existing +
                        " == null)");

        writes.emitNodeWrite(
                out,
                target,
                destination.name(),
                null,
                access,
                value);

        out.endBlock();

        out.beginBlock("else");

        emitArrayContentsUpdate(
                out,
                names,
                existing,
                value,
                existingKind,
                policy,
                plan,
                generated);

        out.endBlock();

        out.endBlock();
    }

    void emitArrayContentsUpdate(
            JavaWriter out,
            NameAllocator names,
            String target,
            String value,
            NodeKind targetKind,
            ArrayPolicy policy,
            MappingPlan plan,
            GeneratedClass generated) {

        if (targetKind ==
                NodeKind.ARRAY_SET &&
                policy == ArrayPolicy.SET) {

            MappingEmissionSupport.error(context,
                    plan.method(),
                    generated,
                    "ArrayPolicy.SET requires an indexed target");

            return;
        }

        if (targetKind ==
                NodeKind.ARRAY_ARRAY &&
                policy == ArrayPolicy.ADD) {

            MappingEmissionSupport.error(context,
                    plan.method(),
                    generated,
                    "ArrayPolicy.ADD cannot grow a Java array");

            return;
        }

        if (policy == ArrayPolicy.ADD) {
            out.line(
                    target +
                            ".addAll(" +
                            value +
                            ");");

            return;
        }

        if (targetKind ==
                NodeKind.ARRAY_ARRAY) {

            out.beginBlock(
                    "if (" +
                            value +
                            ".length > " +
                            target +
                            ".length)");
            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            JavaWriter.stringLiteral(
                                    "ArrayPolicy.SET source length exceeds target array length") +
                            ");");
            out.endBlock();

            String index =
                    names.newName("i");

            out.line(
                    "for (int " +
                            index +
                            " = 0; " +
                            index +
                            " < " +
                            value +
                            ".length; " +
                            index +
                            "++) {");
            out.indent();
            out.line(
                    target +
                            "[" +
                            index +
                            "] = " +
                            value +
                            "[" +
                            index +
                            "];");
            out.dedent();
            out.line("}");

            return;
        }

        String index =
                names.newName("i");

        out.line(
                "for (int " +
                        index +
                        " = 0; " +
                        index +
                        " < " +
                        value +
                        ".size(); " +
                        index +
                        "++) {");
        out.indent();
        out.beginBlock(
                "if (" +
                        index +
                        " < " +
                        target +
                        ".size())");

        if (targetKind ==
                NodeKind.ARRAY_LIST) {
            out.line(
                    target +
                            ".set(" +
                            index +
                            ", " +
                            value +
                            ".get(" +
                            index +
                            "));");
        } else {
            out.line(
                    target +
                            ".set(" +
                            index +
                            ", " +
                            value +
                            ".getNode(" +
                            index +
                            "));");
        }

        out.endBlock();
        out.beginBlock("else");

        if (targetKind ==
                NodeKind.ARRAY_LIST) {
            out.line(
                    target +
                            ".add(" +
                            value +
                            ".get(" +
                            index +
                            "));");
        } else {
            out.line(
                    target +
                            ".add(" +
                            value +
                            ".getNode(" +
                            index +
                            "));");
        }

        out.endBlock();
        out.dedent();
        out.line("}");
    }

    String materialize(
            JavaWriter out,
            NameAllocator names,
            TypeMirror type,
            String value) {

        String result =
                names.newName(
                        "converted");

        out.line(
                JavaExpressions.boxedType(types, type) +
                        " " +
                        result +
                        " = " +
                        value +
                        ";");

        return result;
    }

    private void emitObjectUpdate(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Target destination,
            String target,
            String value,
            ObjectPolicy policy,
            boolean ignoreNull,
            GeneratedClass generated,
            MappingPlan plan) {

        NodeAccess access =
                destination.access();

        if (!access.readable()) {
            MappingEmissionSupport.error(context,
                    plan.method(),
                    generated,
                    "object update target '" +
                            destination.name() +
                            "' must be readable");

            return;
        }

        if (ignoreNull) {
            out.beginBlock(
                    "if (" +
                            value +
                            " != null)");
        } else {
            out.beginBlock(
                    "if (" +
                            value +
                            " == null)");

            writes.emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    "null");

            out.endBlock();

            out.beginBlock("else");
        }

        String existing =
                names.newName(
                        "existing");

        out.line(
                JavaExpressions.boxedType(types,
                        access.readType()) +
                        " " +
                        existing +
                        " = " +
                        writes.readTargetExpression(
                                target,
                                destination.name(),
                                access) +
                        ";");

        out.beginBlock(
                "if (" +
                        existing +
                        " == null)");

        if (policy != ObjectPolicy.PUT_IF_PRESENT) {
            writes.emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    value);
        }

        out.endBlock();

        out.beginBlock("else");

        if (policy == ObjectPolicy.PUT_IF_ABSENT) {

            String entry =
                    names.newName("entry");

            out.line(
                    "for (Object " +
                            entry +
                            " : ((java.util.Map) " +
                            value +
                            ").entrySet()) {");

            out.indent();

            out.beginBlock(
                    "if (!((java.util.Map) " +
                            existing +
                            ").containsKey(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey()) || " +
                            "((java.util.Map) " +
                            existing +
                            ").get(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey()) == null)");

            out.line(
                    "((java.util.Map) " +
                            existing +
                            ").put(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey(), " +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getValue());");

            out.endBlock();

            out.dedent();
            out.line("}");

        } else if (policy == ObjectPolicy.PUT_IF_PRESENT) {

            String entry =
                    names.newName("entry");

            out.line(
                    "for (Object " +
                            entry +
                            " : ((java.util.Map) " +
                            value +
                            ").entrySet()) {");

            out.indent();

            out.beginBlock(
                    "if (((java.util.Map) " +
                            existing +
                            ").containsKey(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey()) && " +
                            "((java.util.Map) " +
                            existing +
                            ").get(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey()) != null)");

            out.line(
                    "((java.util.Map) " +
                            existing +
                            ").put(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey(), " +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getValue());");

            out.endBlock();

            out.dedent();
            out.line("}");

        } else {
            /*
             * PUT
             */
            out.line(
                    existing +
                            ".putAll(" +
                            value +
                            ");");
        }

        out.endBlock();

        out.endBlock();
    }

}
