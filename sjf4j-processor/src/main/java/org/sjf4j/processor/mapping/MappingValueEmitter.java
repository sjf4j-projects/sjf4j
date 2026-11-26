package org.sjf4j.processor.mapping;

import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.type.TypeSystem;

/** Emits a resolved source value and delegates its conversion or computation. */
final class MappingValueEmitter {

    private final TypeSystem types;
    private final MappingReadEmitter sourceReads;
    private final MappingComputeEmitter computes;
    private final MappingEmitter.ConversionWriter conversions;

    MappingValueEmitter(TypeSystem types, MappingReadEmitter sourceReads,
                        MappingEmitter.ConversionWriter conversions) {
        this.types = types;
        this.sourceReads = sourceReads;
        this.computes = new MappingComputeEmitter(types, sourceReads);
        this.conversions = conversions;
    }

    String emitValue(
            JavaWriter out,
            NameAllocator names,
            MappingPlan plan,
            MappingCompiler.Value value,
            GeneratedClass generated) {
        return emitValue(out, names, plan, value, generated, false);
    }

    String emitValue(
            JavaWriter out,
            NameAllocator names,
            MappingPlan plan,
            MappingCompiler.Value value,
            GeneratedClass generated,
            boolean constructorArgument) {

        switch (value.kind()) {
            case READ: {
                MappingCompiler.Read read =
                        value.read();

                // A direct primitive leaf need not be boxed. For constructor
                // arguments, a missing parent supplies the primitive default;
                // for updates, only a non-null single-source direct read is
                // eligible so null-ignore semantics remain unchanged.
                boolean primitiveDirect =
                        sourceReads.isDirectPrimitiveRead(plan, value, constructorArgument);

                // MapStruct-style parent presence: do not invoke a mapping
                // method when its source parent is absent. A present parent
                // still invokes the method even when the leaf value is null.
                // VALUE_CODEC conversions intentionally retain their existing
                // null contract.
                boolean method = value.conversion() != null &&
                        value.conversion().kind() == ConverterResolver.Conversion.Kind.METHOD;
                boolean nullableParent = method &&
                        !read.rootType().getKind().isPrimitive() &&
                        (!MappingEmissionSupport.rootKnownNonNull(plan) || read.steps().size() > 1);

                if (nullableParent && read.steps().size() <= 1) {
                    // A qualified multi-source root/property read needs only
                    // one guard. Read the leaf and convert in that same block.
                    String converted = names.newName("converted");
                    out.line(JavaExpressions.boxedType(types, value.type()) + " " + converted + " = null;");
                    out.beginBlock("if (" + read.root().getSimpleName() + " != null)");
                    String source = sourceReads.emitRead(out, names, read, true, primitiveDirect);
                    String expression = conversions.emit(out, names, plan,
                            value.conversion(), source, read.type(), value.type(), read.rootValue(), generated);
                    out.line(converted + " = " + expression + ";");
                    out.endBlock();
                    return converted;
                }

                String reached = null;
                if (nullableParent) {
                    // For multi-step reads, a null leaf is different from a
                    // missing intermediate parent. Record whether the final
                    // accessor ran rather than checking its returned value.
                    reached = names.newName("sourcePresent");
                    out.line("boolean " + reached + " = false;");
                }

                String source = sourceReads.emitRead(out, names, read, MappingEmissionSupport.rootKnownNonNull(plan),
                        primitiveDirect, null, reached);

                if (reached != null) {
                    String converted = names.newName("converted");
                    out.line(JavaExpressions.boxedType(types, value.type()) + " " + converted + " = null;");
                    out.beginBlock("if (" + reached + ")");
                    String expression = conversions.emit(out, names, plan,
                            value.conversion(), source, read.type(), value.type(), false, generated);
                    out.line(converted + " = " + expression + ";");
                    out.endBlock();
                    return converted;
                }

                return conversions.emit(out, names, plan, value.conversion(),
                        source, read.type(), value.type(), sourceReads.readKnownNonNull(plan, read), generated);
            }

            case COMPUTE:
                return computes.emitCompute(
                        out,
                        names,
                        plan,
                        value);

            default:
                throw new AssertionError(
                        value.kind());
        }
    }

}
