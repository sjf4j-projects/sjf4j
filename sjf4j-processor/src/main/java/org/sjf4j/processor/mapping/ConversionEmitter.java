package org.sjf4j.processor.mapping;

import org.sjf4j.processor.code.NameHints;

import org.sjf4j.processor.mapping.target.TargetCreationEmitter;
import org.sjf4j.annotation.mapping.ObjectPolicy;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.NodeKind;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.access.NodeReadEmitter;
import org.sjf4j.processor.access.NodeWriteEmitter;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits Java expressions and statements for fully compiled value conversions.
 *
 * <p>Consumes the compiled conversion graph. Registration allocates imported
 * mapper names and shared source-equivalent helpers; it does not compile conversions.</p>
 */
public final class ConversionEmitter
        implements MappingEmitter.ConversionWriter {

    private final CompilationContext context;
    private final TypeSystem types;

    private final ConversionPlan plan;
    private final Map<String, String> importedMapperFields = new LinkedHashMap<>();
    private final IdentityHashMap<ConversionCompiler.CompiledConversion, String> signatures =
            new IdentityHashMap<>();
    private final Map<String, SharedConversion> sharedConversions = new LinkedHashMap<>();
    private String emittingShared;

    ConversionEmitter(CompilationContext context, ConversionPlan plan) {
        this.context = context;
        this.types = context.types;
        this.plan = plan;
    }

    /*
     * --------------------------------------------------------------
     * Prepare
     * --------------------------------------------------------------
     */

    /** Registers source-level fields and helpers after semantic compilation. */
    void registerMembers(GeneratedClass generated) {
        for (TypeElement mapper : plan.importedMappers()) {
            String type = mapper.getQualifiedName().toString();
            String simple = mapper.getSimpleName().toString();
            String hint = Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
            if (!simple.endsWith("Mapper")) hint += "Mapper";
            importedMapperFields.put(type, generated.names().field(hint));
        }
        registerFields(generated);
        prepareSharedHelpers(generated);
    }

    private void registerFields(GeneratedClass generated) {
        for (Map.Entry<String, String> entry : importedMapperFields.entrySet()) {
            String type = entry.getKey();
            String field = entry.getValue();

            generated.addField(out -> out.line("private final " + type + " " + field + ";"));
            generated.addConstructorInitializer(out ->
                    out.line("this." + field + " = org.sjf4j.CompiledInstances.create(" +
                            type + ".class);"));
        }
    }

    /*
     * --------------------------------------------------------------
     * Shared structural conversions
     * --------------------------------------------------------------
     */

    /**
     * Share only repeated structural conversions in this generated mapper.
     * Comparing their canonical emitted bodies, in addition to source and
     * target types, preserves method-specific creator and converter choices.
     * Trivial scalars and container loops remain inlined.
     */
    private void prepareSharedHelpers(GeneratedClass generated) {
        Map<String, SharedConversion> candidates = new LinkedHashMap<>();

        for (ConversionCompiler.CompiledConversion conversion : plan.ordered()) {
            collectShared(conversion, candidates);
        }

        for (SharedConversion candidate : candidates.values()) {
            if (candidate.occurrences < 2) continue;
            candidate.name = generated.names().helperMethod("map"
                    + NameHints.type(candidate.conversion.sourceType()) + "To"
                    + NameHints.type(candidate.conversion.targetType()));
            sharedConversions.put(candidate.signature, candidate);
        }

        for (SharedConversion helper : sharedConversions.values()) {
            generated.addHelper(out -> {
                out.beginBlock("private " + JavaExpressions.boxedType(types, helper.conversion.targetType())
                        + " " + helper.name + "(" + JavaExpressions.boxedType(types, helper.conversion.sourceType())
                        + " source)");
                NameAllocator locals = generated.names().helperLocals("source");
                String previous = emittingShared;
                emittingShared = helper.signature;
                try {
                    String result = emitStructural(out, locals, helper.conversion, "source", false);
                    out.line("return " + result + ";");
                } finally {
                    emittingShared = previous;
                }
                out.endBlock();
            });
        }
    }

    private void collectShared(ConversionCompiler.CompiledConversion conversion,
                               Map<String, SharedConversion> candidates) {
        if (conversion == null) return;

        if (conversion.kind() == ConversionCompiler.CompiledConversion.Kind.STRUCTURAL) {
            String signature = semanticSignature(conversion);
            SharedConversion shared = candidates.get(signature);
            if (shared == null) {
                shared = new SharedConversion(signature, conversion);
                candidates.put(signature, shared);
            }
            shared.occurrences++;
        }

        switch (conversion.kind()) {
            case CONTAINER:
                collectShared(conversion.elementConversion(), candidates);
                break;
            case MAP:
                collectShared(conversion.keyConversion(), candidates);
                collectShared(conversion.valueConversion(), candidates);
                break;
            case STRUCTURAL:
                for (ConversionCompiler.StructuralArgument argument : conversion.arguments()) {
                    collectShared(argument.conversion(), candidates);
                }
                for (ConversionCompiler.StructuralProperty property : conversion.properties()) {
                    collectShared(property.conversion(), candidates);
                }
                ConversionCompiler.DynamicObjectPlan dynamic = conversion.dynamicObject();
                if (dynamic != null) {
                    collectShared(dynamic.valueConversion(), candidates);
                    for (ConversionCompiler.DynamicProperty property : dynamic.properties()) {
                        collectShared(property.conversion(), candidates);
                    }
                }
                break;
            default:
                break;
        }
    }

    /** Exact source equivalence is stricter than a type-pair cache key. */
    private String semanticSignature(ConversionCompiler.CompiledConversion conversion) {
        String existing = signatures.get(conversion);
        if (existing != null) return existing;

        StringWriter source = new StringWriter();
        try (JavaWriter out = new JavaWriter(source, "", "_Signature")) {
            NameAllocator locals = NameAllocator.forParameters("source");
            String result = emitStructural(out, locals, conversion, "source", false);
            out.line("return " + result + ";");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot render structural conversion signature", e);
        }

        String signature = conversion.sourceType() + " -> "
                + conversion.targetType() + "\n" + source;
        signatures.put(conversion, signature);
        return signature;
    }

    private static final class SharedConversion {
        final String signature;
        final ConversionCompiler.CompiledConversion conversion;
        int occurrences;
        String name;

        SharedConversion(String signature, ConversionCompiler.CompiledConversion conversion) {
            this.signature = signature;
            this.conversion = conversion;
        }
    }

    /*
     * --------------------------------------------------------------
     * Emit
     * --------------------------------------------------------------
     */

    @Override
    public String emit(JavaWriter out, NameAllocator names, MappingPlan plan,
                       ConverterResolver.Conversion conversion, String source,
                       TypeMirror sourceType, TypeMirror targetType, boolean sourceKnownNonNull,
                       GeneratedClass generated) {
        ConversionCompiler.CompiledConversion resolved = this.plan.get(conversion);

        if (resolved == null) throw new IllegalStateException("conversion was not prepared: " + sourceType + " -> " + targetType);

        return emit(out, names, resolved, source, sourceKnownNonNull);
    }

    @Override
    public void emitMapUpdate(JavaWriter out, NameAllocator names, MappingPlan plan,
                              ConverterResolver.Conversion conversion, String source, TypeMirror sourceType,
                              TypeMirror targetType, String target, ObjectPolicy policy, boolean sourceKnownNonNull,
                              GeneratedClass generated) {
        ConversionCompiler.CompiledConversion resolved = this.plan.get(conversion);

        if (resolved == null) throw new IllegalStateException("conversion was not prepared: " + sourceType + " -> " + targetType);

        if (resolved.kind() != ConversionCompiler.CompiledConversion.Kind.MAP) throw new IllegalArgumentException("root map update requires a map conversion");

        if (!sourceKnownNonNull) out.beginBlock("if (" + source + " != null)");
        String entry = names.newName("entry");

        if (types.isCompileTimeUnknown(resolved.sourceKeyType()) &&
                types.isCompileTimeUnknown(resolved.sourceValueType())) {
            String entryObject = names.newName("entryObject");
            out.line("for (Object " + entryObject + " : " + source + ".entrySet()) {");

            out.indent();
            out.line("java.util.Map.Entry<?, ?> " + entry +
                    " = (java.util.Map.Entry<?, ?>) " + entryObject + ";");

        } else {
            out.line("for (java.util.Map.Entry<?, ?> " + entry + " : " + source + ".entrySet()) {");

            out.indent();
        }

        String rawKey = names.newName("key");
        out.line(JavaExpressions.boxedType(types, resolved.sourceKeyType()) + " " + rawKey + " = " +
                cast(resolved.sourceKeyType(), entry + ".getKey()") + ";");
        String key = emit(out, names, resolved.keyConversion(), rawKey);

        if (policy == ObjectPolicy.PUT_IF_ABSENT) {
            out.beginBlock("if (!" + target + ".containsKey(" + key + ") || " +
                    target + ".get(" + key + ") == null)");

        } else if (policy == ObjectPolicy.PUT_IF_PRESENT) {
            out.beginBlock("if (" + target + ".containsKey(" + key + ") && " +
                    target + ".get(" + key + ") != null)");
        }

        String rawValue = names.newName("value");
        out.line(JavaExpressions.boxedType(types, resolved.sourceValueType()) + " " + rawValue + " = " +
                cast(resolved.sourceValueType(), entry + ".getValue()") + ";");

        String value = emit(out, names, resolved.valueConversion(), rawValue);
        out.line(target + ".put(" + key + ", " + value + ");");

        if (policy != ObjectPolicy.PUT) {
            out.endBlock();
        }

        out.dedent();
        out.line("}");
        if (!sourceKnownNonNull) out.endBlock();
    }

    /*
     * --------------------------------------------------------------
     * Dispatch
     * --------------------------------------------------------------
     */

    private String emit(JavaWriter out, NameAllocator names,
                        ConversionCompiler.CompiledConversion conversion, String source) {
        // Nested values and shared helpers retain their independent null contract.
        return emit(out, names, conversion, source, false);
    }

    private String emit(JavaWriter out, NameAllocator names,
                        ConversionCompiler.CompiledConversion conversion, String source,
                        boolean sourceKnownNonNull) {

        if (conversion.kind() == ConversionCompiler.CompiledConversion.Kind.STRUCTURAL) {
            String signature = signatures.get(conversion);
            SharedConversion helper = signature == null ? null : sharedConversions.get(signature);
            if (helper != null && !signature.equals(emittingShared)) {
                // Preserve the original single-evaluation semantics: callers may
                // use the returned expression in both guards and assignments.
                String result = names.newName("converted");
                out.line(JavaExpressions.boxedType(types, conversion.targetType()) + " " + result
                        + " = " + helper.name + "(" + source + ");");
                return result;
            }
        }

        switch (conversion.kind()) {
            case DIRECT:
                return source;

            case METHOD:
                return emitMethod(conversion, source);

            case RAW_CAST:
                return emitRawCast(out, names, conversion.sourceType(), conversion.targetType(), source);

            case SCALAR:
                return emitScalar(out, names, conversion, source);

            case LENIENT_SCALAR:
                return emitNodeMapperAs(out, names, conversion.targetType(), source);

            case ENUM:
                return emitEnum(out, names, conversion, source);

            case VALUE_CODEC:
                return emitNodeMapperTo(out, names, conversion.targetType(), source, "codec");

            case CODEC_ENCODE:
                return emitCodecRaw(out, names, conversion.targetType(), source);

            case ONE_OF:
                return emitNodeMapperTo(out, names, conversion.targetType(), source, "oneOf");

            case CONTAINER:
                return emitContainer(out, names, conversion, source, sourceKnownNonNull);

            case MAP:
                return emitMap(out, names, conversion, source, sourceKnownNonNull);

            case STRUCTURAL:
                return emitStructural(out, names, conversion, source, sourceKnownNonNull);

            default:
                throw new AssertionError(conversion.kind());
        }
    }

    private String emitMethod(ConversionCompiler.CompiledConversion conversion, String source) {
        ExecutableElement method = conversion.method();
        if (conversion.mapperType() == null) return method.getSimpleName() + "(" + source + ")";

        String type = conversion.mapperType().getQualifiedName().toString();
        String field = importedMapperFields.get(type);

        if (field == null) throw new IllegalStateException("imported mapper was not prepared: " + type);

        return field + "." + method.getSimpleName() + "(" + source + ")";
    }

    /*
     * --------------------------------------------------------------
     * Enum Convert
     * --------------------------------------------------------------
     */

    private String emitEnum(JavaWriter out, NameAllocator names,
                            ConversionCompiler.CompiledConversion conversion, String source) {
        String input = names.newName("enumSource");
        String result = names.newName("converted");
        String target = JavaExpressions.boxedType(types, conversion.targetType());

        // Evaluate the source once, including when it is a property getter.
        out.line(JavaExpressions.boxedType(types, conversion.sourceType()) + " " + input + " = " + source + ";");
        out.line(target + " " + result + " = null;");
        out.beginBlock("if (" + input + " != null)");
        out.beginBlock("switch (" + input + ")");

        for (String name : conversion.enumNames()) {
            out.line("case " + name + ":");
            out.indent();
            out.line(result + " = " + target + "." + name + ";");
            out.line("break;");
            out.dedent();
        }

        // Defensive default if the enum changed after this mapper was compiled.
        out.line("default:");
        out.indent();
        out.line("throw new java.lang.IllegalArgumentException(" +
                JavaWriter.stringLiteral("unmapped enum constant for " +
                        conversion.sourceType() + " -> " + conversion.targetType() + ": ") +
                " + " + input + ".name());");
        out.dedent();
        out.endBlock();
        out.endBlock();
        return result;
    }

    /*
     * --------------------------------------------------------------
     * Scalar Convert
     * --------------------------------------------------------------
     */

    /**
     * A CAST conversion is intentionally shallow: it does not traverse an
     * Object/Map/List value or apply any OBNT coercion. Java's generic casts
     * validate only the erased container type, so suppress the unavoidable
     * unchecked diagnostic on this generated local alone, not on the method
     * or generated class. This keeps -Xlint:unchecked -Werror builds usable.
     */
    private String emitRawCast(JavaWriter out, NameAllocator names, TypeMirror sourceType,
                               TypeMirror targetType, String source) {
        String result = names.newName("raw");
        String type = JavaExpressions.boxedType(types, targetType);
        if (type.indexOf('<') >= 0) {
            out.line("@SuppressWarnings(\"unchecked\")");
        }
        // Object-valued reads need no widening: (Object) on an Object local
        // would trigger -Xlint:cast. Some external JSON sources have final
        // declared classes, however, and require widening before a Java cast
        // to an unrelated target type.
        String value = types.isObject(sourceType) ? source : "(java.lang.Object) " + source;
        out.line(type + " " + result + " = (" + type + ") " + value + ";");
        return result;
    }

    /**
     * Scalar leniency is a leaf operation. The caller has already resolved
     * nested Map/List/POJO structure, so each raw value can be treated
     * independently without claiming NodeMapper.as() recursively coerces
     * an entire object graph.
     */
    private String emitNodeMapperAs(JavaWriter out, NameAllocator names, TypeMirror targetType,
                                    String source) {
        String result = names.newName("converted");
        String target = JavaExpressions.boxedRawType(types, context.typeUtils, targetType);
        String expr;
        if ("java.lang.String".equals(target)) {
            expr = "org.sjf4j.node.NodeMapper.asString(" + source + ", org.sjf4j.NodeConfig.DEFAULT)";
        } else if ("java.lang.Boolean".equals(target)) {
            expr = "org.sjf4j.node.NodeMapper.asBoolean(" + source + ", org.sjf4j.NodeConfig.DEFAULT)";
        } else if ("java.lang.Character".equals(target)) {
            expr = "org.sjf4j.node.NodeMapper.asChar(" + source + ", org.sjf4j.NodeConfig.DEFAULT)";
        } else if (isEnum(targetType)) {
            expr = "org.sjf4j.node.NodeMapper.asEnum(" + source + ", "
                    + JavaExpressions.boxedClassLiteral(types, context.typeUtils, targetType) + ", org.sjf4j.NodeConfig.DEFAULT)";
        } else {
            // Numeric targets are normalized and range-checked by NodeMapper.as.
            expr = "org.sjf4j.node.NodeMapper.as(" + source + ", "
                    + JavaExpressions.boxedClassLiteral(types, context.typeUtils, targetType) + ", org.sjf4j.NodeConfig.DEFAULT)";
        }
        out.line(JavaExpressions.boxedType(types, targetType) + " " + result + " = " + expr + ";");
        return result;
    }


    private String emitScalar(JavaWriter out, NameAllocator names,
                              ConversionCompiler.CompiledConversion conversion, String source) {
        TypeMirror sourceType = types.boxed(conversion.sourceType());
        TypeMirror targetType = types.boxed(conversion.targetType());
        String sourceName = JavaExpressions.boxedRawType(types, context.typeUtils, sourceType);
        String targetName = JavaExpressions.boxedRawType(types, context.typeUtils, targetType);

        /*
         * A value declared as Object has no compile-time NodeKind. Keep the
         * generated fallback inside the OBNT layer instead of routing through
         * a binder: NodeMapper performs strict runtime value-kind conversion.
         */
        if (types.nodeKind(conversion.sourceType()) == NodeKind.COMPILE_TIME_UNKNOWN) {
            return emitNodeMapperTo(out, names, conversion.targetType(), source, "converted");
        }

        /*
         * Number -> Number
         */
        if (isNumber(sourceName) && isNumber(targetName)) {
            String value = names.newName("converted");
            out.line(JavaExpressions.boxedType(types, targetType) + " " + value + " = " + source
                    + " == null ? null : org.sjf4j.util.Numbers.to(" + source
                    + ", " + JavaExpressions.boxedClassLiteral(types, context.typeUtils, targetType) + ");");

            return value;
        }

        /*
         * char / Character -> String
         */
        if ("java.lang.String".equals(targetName) && "java.lang.Character".equals(sourceName)) {
            String value = names.newName("converted");
            out.line("java.lang.String " + value + " = " + source +
                    " == null ? null : java.lang.String.valueOf(" + source + ");");

            return value;
        }

        /*
         * enum -> String
         */
        if ("java.lang.String".equals(targetName) && isEnum(sourceType)) {
            String value = names.newName("converted");
            out.line("java.lang.String " + value + " = " + source + " == null ? null : " + source + ".name();");

            return value;
        }

        /*
         * String -> Character
         */
        if ("java.lang.String".equals(sourceName) && "java.lang.Character".equals(targetName)) {
            String value = names.newName("converted");
            out.line("java.lang.Character " + value + " = " + source +
                    " == null ? null : " + source + ".charAt(0);");

            return value;
        }

        /*
         * String -> enum
         */
        if ("java.lang.String".equals(sourceName) && isEnum(targetType)) {
            String value = names.newName("converted");
            out.line(JavaExpressions.boxedType(types, targetType) + " " + value + " = " + source + " == null ? null : " +
                    JavaExpressions.boxedType(types, targetType) + ".valueOf(" + source + ");");

            return value;
        }

        /*
         * Boolean -> Boolean should normally have resolved as DIRECT.
         */
        if ("java.lang.Boolean".equals(sourceName) && "java.lang.Boolean".equals(targetName)) return source;

        /*
         * @NodeValue / registered scalar codecs.
         */
        return emitNodeMapperTo(out, names, conversion.targetType(), source, "converted");
    }

    /*
     * --------------------------------------------------------------
     * Container Convert
     * --------------------------------------------------------------
     */

    private String emitContainer(JavaWriter out, NameAllocator names,
                                 ConversionCompiler.CompiledConversion conversion, String source,
                                 boolean sourceKnownNonNull) {
        TypeMirror sourceType = conversion.sourceType();
        TypeMirror targetType = conversion.targetType();
        NodeKind sourceKind = types.nodeKind(sourceType);
        NodeKind targetKind = types.nodeKind(targetType);
        String result = names.newName("converted");
        String targetLocalType = JavaExpressions.boxedType(types, targetType);

        if (!sourceKnownNonNull) {
            out.line(targetLocalType + " " + result + " = null;");
            out.beginBlock("if (" + source + " != null)");
        }

        String size = names.newName("size");
        out.line("int " + size + " = " + sizeExpression(source, sourceKind) + ";");

        String assignment = (sourceKnownNonNull ? targetLocalType + " " : "") + result + " = ";
        if (targetKind == NodeKind.ARRAY_ARRAY) {
            TypeMirror component = ((ArrayType) types.concrete(targetType)).getComponentType();
            out.line(assignment + "new " + component + "[" + size + "];");

        } else {
            out.line(assignment + newContainerExpression(targetType, targetKind, size) + ";");
        }

        if (sourceKind == NodeKind.ARRAY_SET) {
            emitSetLoop(out, names, conversion, source, result, targetKind);

        } else if (sourceKind == NodeKind.ARRAY_LIST) {
            // RandomAccess supports efficient get(i); sequential Lists must use an Iterator.
            out.beginBlock("if (((java.util.List<?>) " + source + ") instanceof java.util.RandomAccess)");
            emitIndexedContainerLoop(out, names, conversion, source, result, sourceKind, targetKind, size);
            out.endBlock(" else {");
            out.indent();
            emitSetLoop(out, names, conversion, source, result, targetKind);
            out.endBlock();
        } else {
            emitIndexedContainerLoop(out, names, conversion, source, result, sourceKind, targetKind, size);
        }

        if (!sourceKnownNonNull) out.endBlock();

        return result;
    }

    private void emitIndexedContainerLoop(JavaWriter out, NameAllocator names,
                                          ConversionCompiler.CompiledConversion conversion,
                                          String source, String result, NodeKind sourceKind,
                                          NodeKind targetKind, String size) {
        String index = names.newName("i");
        out.line("for (int " + index + " = 0; " + index + " < " + size + "; " + index + "++) {");

        out.indent();

        String item = names.newName("item");

        out.line(JavaExpressions.boxedType(types, conversion.sourceElementType()) + " " + item + " = " +
                sourceElementExpression(source, index, sourceKind, conversion.sourceElementType()) + ";");

        String converted = emit(out, names, conversion.elementConversion(), item);
        emitContainerAdd(out, result, targetKind, index, converted);

        out.dedent();
        out.line("}");
    }

    private void emitSetLoop(JavaWriter out, NameAllocator names,
                             ConversionCompiler.CompiledConversion conversion, String source,
                             String result, NodeKind targetKind) {
        String item = names.newName("item");
        String index = names.newName("i");

        out.line("int " + index + " = 0;");

        out.line("for (" + JavaExpressions.boxedType(types, conversion.sourceElementType()) + " " + item + " : " + source + ") {");

        out.indent();

        String converted = emit(out, names, conversion.elementConversion(), item);
        emitContainerAdd(out, result, targetKind, index, converted);
        out.line(index + "++;");

        out.dedent();
        out.line("}");
    }

    private void emitContainerAdd(JavaWriter out, String result, NodeKind targetKind,
                                  String index, String value) {
        if (targetKind == NodeKind.ARRAY_ARRAY) {
            out.line(result + "[" + index + "] = " + value + ";");

            return;
        }

        switch (targetKind) {
            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                out.line(result + ".add(" + value + ");");
                return;

            default:
                throw new IllegalStateException("unsupported target container " + targetKind);
        }
    }

    /*
     * --------------------------------------------------------------
     * Map Convert
     * --------------------------------------------------------------
     */

    private String emitMap(JavaWriter out, NameAllocator names,
                           ConversionCompiler.CompiledConversion conversion, String source,
                           boolean sourceKnownNonNull) {
        String result = names.newName("converted");

        String targetType = JavaExpressions.boxedType(types, conversion.targetType());
        if (!sourceKnownNonNull) {
            out.line(targetType + " " + result + " = null;");
            out.beginBlock("if (" + source + " != null)");
        }
        // Unlike an unsized LinkedHashMap, this avoids repeated table growth for large maps.
        // Keep user-supplied concrete Map implementations on their existing constructor path.
        out.line((sourceKnownNonNull ? targetType + " " : "") + result + " = " + newMapExpression(conversion.targetType(), source) + ";");

        String entry = names.newName("entry");
        if (types.isCompileTimeUnknown(conversion.sourceKeyType()) &&
                types.isCompileTimeUnknown(conversion.sourceValueType())) {
            String entryObject = names.newName("entryObject");
            out.line("for (Object " + entryObject + " : " + source + ".entrySet()) {");

            out.indent();

            out.line("java.util.Map.Entry<?, ?> " + entry +
                    " = (java.util.Map.Entry<?, ?>) " + entryObject + ";");

        } else {
            out.line("for (java.util.Map.Entry<?, ?> " + entry + " : " + source + ".entrySet()) {");

            out.indent();
        }

        String rawKey = names.newName("key");
        out.line(JavaExpressions.boxedType(types, conversion.sourceKeyType()) + " " + rawKey + " = " +
                cast(conversion.sourceKeyType(), entry + ".getKey()") + ";");
        String rawValue = names.newName("value");
        out.line(JavaExpressions.boxedType(types, conversion.sourceValueType()) + " " + rawValue + " = " +
                cast(conversion.sourceValueType(), entry + ".getValue()") + ";");

        String key = emit(out, names, conversion.keyConversion(), rawKey);
        String value = emit(out, names, conversion.valueConversion(), rawValue);
        out.line(result + ".put(" + key + ", " + value + ");");

        out.dedent();
        out.line("}");

        if (!sourceKnownNonNull) out.endBlock();

        return result;
    }

    /*
     * --------------------------------------------------------------
     * Object Convert
     * --------------------------------------------------------------
     */

    private String emitStructural(JavaWriter out, NameAllocator names,
                                  ConversionCompiler.CompiledConversion conversion, String source,
                                  boolean sourceKnownNonNull) {
        ConversionCompiler.DynamicObjectPlan dynamic = conversion.dynamicObject();

        if (conversion.creation() != null) return emitTypedObject(out, names, conversion, source, sourceKnownNonNull);

        if (dynamic != null) return emitDynamicObject(out, names, conversion, dynamic, source, sourceKnownNonNull);

        throw new IllegalStateException("structural conversion has neither creator nor dynamic plan");
    }

    /*
     * --------------------------------------------------------------
     * Typed Object Convert
     * --------------------------------------------------------------
     */

    private String emitTypedObject(JavaWriter out, NameAllocator names,
                                   ConversionCompiler.CompiledConversion conversion, String source,
                                   boolean sourceKnownNonNull) {
        String result = names.newName("converted");
        TypeMirror workingType = conversion.workingTargetType();

        out.line(JavaExpressions.boxedType(types, workingType) + " " + result + (sourceKnownNonNull ? ";" : " = null;"));
        if (!sourceKnownNonNull) out.beginBlock("if (" + source + " != null)");
        boolean runtimeSource = types.nodeKind(conversion.sourceType()) == NodeKind.COMPILE_TIME_UNKNOWN;

        if (runtimeSource) {
            out.beginBlock("if (" + source + " instanceof " + JavaExpressions.boxedRawType(types, context.typeUtils, workingType) + ")");
            out.line(result + " = (" + JavaExpressions.boxedType(types, workingType) + ") " + source + ";");

            out.endBlock();
            out.beginBlock("else");
        }

        CreatorResolver.Creation creation = conversion.creation();

        if (creation == null) throw new IllegalStateException("typed structural conversion has no creator");

        switch (creation.kind()) {
            case NO_ARGS:
                out.line(result + " = " + TargetCreationEmitter.constructor(workingType.toString(), java.util.Collections.emptyList()) + ";");
                break;

            case FACTORY:
                out.line(result + " = " + TargetCreationEmitter.factory(creation, false) + ";");
                break;

            case CONSTRUCTOR:
                emitStructuralConstructor(out, names, conversion, source, result);
                break;

            default:
                throw new AssertionError(creation.kind());
        }

        for (ConversionCompiler.StructuralProperty property : conversion.properties()) {
            String value = emitStructuralValue(out, names, source, property.name(),
                    property.source(), property.conversion());
            emitAccessWrite(out, result, property.name(), property.target(), value);
        }

        if (conversion.dynamicObject() != null) {
            emitDynamicMembers(out, names, conversion.dynamicObject(), source, result);
        }

        if (runtimeSource) {
            out.endBlock();
        }

        if (!sourceKnownNonNull) out.endBlock();

        return result;
    }

    private void emitStructuralConstructor(JavaWriter out, NameAllocator names,
                                           ConversionCompiler.CompiledConversion conversion,
                                           String source, String result) {
        List<ConversionCompiler.StructuralArgument> arguments = conversion.arguments();
        List<String> values = new ArrayList<>(arguments.size());

        for (ConversionCompiler.StructuralArgument argument : arguments) {
            values.add(emitStructuralValue(out, names, source, argument.name(),
                    argument.source(), argument.conversion()));
        }

        out.line(result + " = " +
                TargetCreationEmitter.constructor(conversion.workingTargetType().toString(), values) + ";");
    }

    /*
     * --------------------------------------------------------------
     * Dynamic Object Convert
     * --------------------------------------------------------------
     */

    private String emitDynamicObject(JavaWriter out, NameAllocator names,
                                     ConversionCompiler.CompiledConversion conversion,
                                     ConversionCompiler.DynamicObjectPlan dynamic, String source,
                                     boolean sourceKnownNonNull) {
        String result = names.newName("converted");
        String targetType = JavaExpressions.boxedType(types, conversion.targetType());
        if (!sourceKnownNonNull) {
            out.line(targetType + " " + result + " = null;");
            out.beginBlock("if (" + source + " != null)");
        }
        out.line((sourceKnownNonNull ? targetType + " " : "") + result
                + " = " + newObjectExpression(conversion.targetType()) + ";");

        if (dynamic.kind() == ConversionCompiler.DynamicObjectPlan.Kind.STATIC_PROPERTIES) {
            for (ConversionCompiler.DynamicProperty property : dynamic.properties()) {
                String raw = emitAccessRead(out, names, source, property.name(),
                        property.source(), property.conversion());
                String value = emit(out, names, property.conversion(), raw);
                out.line(result + ".put(" + JavaWriter.stringLiteral(property.name()) + ", " + value + ");");
            }

        } else {
            emitDynamicMembers(out, names, dynamic, source, result);
        }

        if (!sourceKnownNonNull) out.endBlock();

        return result;
    }

    private void emitDynamicMembers(JavaWriter out, NameAllocator names,
                                    ConversionCompiler.DynamicObjectPlan dynamic, String source, String result) {
        switch (dynamic.kind()) {
            case ENTRIES:
                emitDirectDynamicMembers(out, names, dynamic, source, result);
                return;

            case DYNAMIC_MEMBERS:
                emitJojoDynamicMembers(out, names, dynamic, source, result);
                return;

            case RUNTIME_ENTRIES:
                emitRuntimeDynamicMembers(out, names, dynamic, source, result);
                return;

            default:
                throw new IllegalStateException("unsupported dynamic member plan " + dynamic.kind());
        }
    }

    private void emitDirectDynamicMembers(JavaWriter out, NameAllocator names,
                                          ConversionCompiler.DynamicObjectPlan dynamic,
                                          String source, String result) {
        String entry = names.newName("entry");
        if (dynamic.sourceKind() == NodeKind.OBJECT_MAP) {
            out.line("for (java.util.Map.Entry<?, ?> " + entry + " : " + source + ".entrySet()) {");

        } else {
            out.line("for (java.util.Map.Entry<String, Object> " + entry + " : " + source + ".entrySet()) {");
        }

        out.indent();

        emitDynamicMember(out, names, dynamic,
                dynamic.sourceKind() == NodeKind.OBJECT_MAP
                        ? "(String) " + entry + ".getKey()" : entry + ".getKey()",
                dynamic.sourceKind() == NodeKind.OBJECT_MAP
                        ? cast(dynamic.valueConversion().sourceType(), entry + ".getValue()")
                        : entry + ".getValue()", result);

        out.dedent();
        out.line("}");
    }

    private void emitJojoDynamicMembers(JavaWriter out, NameAllocator names,
                                        ConversionCompiler.DynamicObjectPlan dynamic,
                                        String source, String result) {
        out.beginBlock("if (" + "org.sjf4j.InternalAccess.dynamicMembers(" + source + ") != null)");
        String entry = names.newName("entry");
        out.line("for (java.util.Map.Entry<String, Object> " + entry + " : " +
                "org.sjf4j.InternalAccess.dynamicMembers(" + source + ").entrySet()) {");

        out.indent();

        emitDynamicMember(out, names, dynamic, entry + ".getKey()", entry + ".getValue()", result);

        out.dedent();
        out.line("}");

        out.endBlock();
    }

    private void emitRuntimeDynamicMembers(JavaWriter out, NameAllocator names,
                                           ConversionCompiler.DynamicObjectPlan dynamic,
                                           String source, String result) {
        String entry = names.newName("entry");
        out.line("for (java.util.Map.Entry<String, Object> " + entry +
                " : org.sjf4j.node.NodeWalker.entrySetInObject(" + source + ")) {");

        out.indent();

        emitDynamicMember(out, names, dynamic, entry + ".getKey()", entry + ".getValue()", result);

        out.dedent();
        out.line("}");
    }

    private void emitDynamicMember(JavaWriter out, NameAllocator names,
                                   ConversionCompiler.DynamicObjectPlan dynamic,
                                   String keyExpression, String valueExpression, String result) {
        String key = names.newName("key");
        out.line("String " + key + " = " + keyExpression + ";");

        if (!dynamic.excludedNames().isEmpty()) {
            StringBuilder condition = new StringBuilder();
            for (String excluded : dynamic.excludedNames()) {

                if (condition.length() != 0) {
                    condition.append(" || ");
                }

                condition.append(JavaWriter.stringLiteral(excluded))
                        .append(".equals(")
                        .append(key)
                        .append(')');
            }

            out.line("if (" + condition + ") continue;");
        }

        String value = emit(out, names, dynamic.valueConversion(), valueExpression);
        out.line(result + ".put(" + key + ", " + value + ");");
    }

    /*
     * --------------------------------------------------------------
     * Runtime Convert
     * --------------------------------------------------------------
     */

    /** Encode a logical value before projecting its Map/List raw container. */
    private String emitCodecRaw(JavaWriter out, NameAllocator names, TypeMirror targetType, String source) {

        String result = names.newName("raw");
        out.line(JavaExpressions.boxedType(types, targetType) + " " + result + " = "
                + cast(targetType, "org.sjf4j.node.NodeMapper.convertToRaw("
                        + source + ", org.sjf4j.NodeConfig.DEFAULT)") + ";");
        return result;
    }

    private String emitNodeMapperTo(JavaWriter out, NameAllocator names, TypeMirror targetType,
                                    String source, String hint) {
        String result = names.newName(hint);
        if ("java.lang.String".equals(JavaExpressions.boxedRawType(types, context.typeUtils, targetType))) {
            // Same strict semantics as NodeMapper.to(node, String.class, context),
            // but avoid target-class dispatch for a statically known String.
            out.line("java.lang.String " + result + " = org.sjf4j.node.NodeMapper.toString("
                    + source + ", org.sjf4j.NodeConfig.DEFAULT);");
        } else {
            out.line(JavaExpressions.boxedType(types, targetType) + " " + result + " = " + mapperToCast(targetType) +
                    "org.sjf4j.node.NodeMapper.to(" + source + ", " + JavaExpressions.boxedClassLiteral(types, context.typeUtils, targetType) +
                    ", org.sjf4j.NodeConfig.DEFAULT);");
        }

        return result;
    }

    private String mapperToCast(TypeMirror targetType) {
        if (!(targetType instanceof DeclaredType) || ((DeclaredType) targetType).getTypeArguments().isEmpty()) return "";
        return "(" + JavaExpressions.boxedType(types, targetType) + ") ";
    }

    /*
     * --------------------------------------------------------------
     * Access
     * --------------------------------------------------------------
     */

    // Structural reads occur inside a non-null owner guard. A declared
    // primitive property cannot yield null. Integral widening is lossless and
    // needs neither boxing nor the range checks used by narrowing conversions.
    private String emitStructuralValue(JavaWriter out, NameAllocator names, String owner,
                                       String name, NodeAccess access,
                                       ConversionCompiler.CompiledConversion conversion) {
        TypeKind source = access.readType().getKind();
        TypeKind target = conversion.targetType().getKind();
        if (access.kind() == NodeAccess.Kind.PROPERTY
                && conversion.kind() == ConversionCompiler.CompiledConversion.Kind.SCALAR
                && isIntegralWidening(source, target)) {
            String result = names.newName("converted");
            out.line(conversion.targetType() + " " + result + " = "
                    + accessReadExpression(owner, name, access) + ";");
            return result;
        }
        String raw = emitAccessRead(out, names, owner, name, access, conversion);
        return emit(out, names, conversion, raw);
    }

    private boolean isIntegralWidening(TypeKind source, TypeKind target) {
        switch (source) {
            case BYTE:
                return target == TypeKind.SHORT || target == TypeKind.INT || target == TypeKind.LONG;
            case SHORT:
                return target == TypeKind.INT || target == TypeKind.LONG;
            case INT:
                return target == TypeKind.LONG;
            default:
                return false;
        }
    }

    private String emitAccessRead(JavaWriter out, NameAllocator names, String owner,
                                  String name, NodeAccess access,
                                  ConversionCompiler.CompiledConversion conversion) {
        String result = names.newName(name);
        // Primitive direct assignments cannot be null and need no intermediate box.
        // Scalar conversion code may test for null, so retain boxing in that case.
        boolean primitiveDirect = access.readType().getKind().isPrimitive()
                && conversion.kind() == ConversionCompiler.CompiledConversion.Kind.DIRECT
                && conversion.targetType().getKind().isPrimitive();
        String type = primitiveDirect ? types.concrete(access.readType()).toString()
                : JavaExpressions.boxedType(types, access.readType());
        out.line(type + " " + result + " = " + accessReadExpression(owner, name, access) + ";");

        return result;
    }

    private String accessReadExpression(String owner, String name, NodeAccess access) {
        String expression = NodeReadEmitter.rawName(access, owner, JavaWriter.stringLiteral(name));
        return access.kind() == NodeAccess.Kind.PROPERTY ? expression : cast(access.readType(), expression);
    }

    private void emitAccessWrite(JavaWriter out, String owner, String name, NodeAccess access, String value) {
        out.line(NodeWriteEmitter.name(access, owner, JavaWriter.stringLiteral(name), value) + ";");
    }

    /*
     * --------------------------------------------------------------
     * Container Expressions
     * --------------------------------------------------------------
     */

    private String sourceElementExpression(String source, String index, NodeKind kind, TypeMirror elementType) {

        switch (kind) {
            case ARRAY_ARRAY:
                return source + "[" + index + "]";

            case ARRAY_LIST:
                return source + ".get(" + index + ")";

            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return cast(elementType, source + ".getNode(" + index + ")");

            case COMPILE_TIME_UNKNOWN:
                return cast(elementType, "org.sjf4j.node.NodeWalker.getInArray(" + source + ", " + index + ")");

            default:
                throw new IllegalStateException("unsupported indexed source container " + kind);
        }
    }

    private String sizeExpression(String source, NodeKind kind) {

        switch (kind) {
            case ARRAY_ARRAY:
                return source + ".length";

            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return source + ".size()";

            case COMPILE_TIME_UNKNOWN:
                return "org.sjf4j.node.NodeWalker.sizeInArray(" + source + ")";

            default:
                throw new IllegalStateException("unsupported source container " + kind);
        }
    }

    private String newContainerExpression(TypeMirror type, NodeKind kind, String size) {

        switch (kind) {
            case ARRAY_LIST:
                if (types.isSameErasure(type, types.listType())) return "new java.util.ArrayList<>(" + size + ")";

                if (isInterfaceOrAbstract(type)) return "new java.util.ArrayList<>(" + size + ")";

                return JavaExpressions.newInstance(type.toString(), false);

            case ARRAY_SET:
                if (isInterfaceOrAbstract(type)) return "new java.util.LinkedHashSet<>(" + size + ")";

                return JavaExpressions.newInstance(type.toString(), false);

            case ARRAY_JSON_ARRAY:
                return "new org.sjf4j.JsonArray()";

            case ARRAY_JAJO:
                return JavaExpressions.newInstance(type.toString(), false);

            default:
                throw new IllegalStateException("unsupported target container " + kind);
        }
    }

    private String newMapExpression(TypeMirror type, String source) {
        if (types.isSameErasure(type, types.mapType()) || isInterfaceOrAbstract(type)) {
            // ceil(size / 0.75) == (4 * size + 2) / 3, without floats or a
            // redundant +1 on exact thresholds (12, 24, 48, ... entries).
            // Use long arithmetic to avoid overflow; HashMap caps table capacity
            // at 1 << 30. Zero and small maps can use fewer than 16 buckets.
            // A custom concrete Map retains its original no-arg construction.
            return "new java.util.LinkedHashMap<>((int) " +
                    "java.lang.Math.min(1073741824L, (((long) " + source +
                    ".size() * 4L + 2L) / 3L)))";
        }
        return newMapExpression(type);
    }

    private String newMapExpression(TypeMirror type) {
        if (types.isSameErasure(type, types.mapType()) || isInterfaceOrAbstract(type)) return "new java.util.LinkedHashMap<>()";
        return JavaExpressions.newInstance(type.toString(), false);
    }

    private String newObjectExpression(TypeMirror type) {
        NodeKind kind = types.nodeKind(type);

        switch (kind) {
            case OBJECT_MAP:
                return newMapExpression(type);

            case OBJECT_JSON_OBJECT:
                return "new org.sjf4j.JsonObject()";

            default:
                throw new IllegalStateException("unsupported dynamic object target " + type);
        }
    }

    private String cast(TypeMirror type, String expression) {
        TypeMirror concrete = types.concrete(type);
        if (types.isObject(concrete)) return expression;
        return JavaExpressions.cast(JavaExpressions.boxedType(types, concrete), expression);
    }

    private boolean isEnum(TypeMirror type) {
        TypeElement element = types.typeElement(types.concrete(type));
        return element != null && "ENUM".equals(element.getKind().name());
    }

    private boolean isNumber(String name) {
        return "java.lang.Byte".equals(name) ||
                "java.lang.Short".equals(name) ||
                "java.lang.Integer".equals(name) ||
                "java.lang.Long".equals(name) ||
                "java.lang.Float".equals(name) ||
                "java.lang.Double".equals(name) ||
                "java.math.BigInteger".equals(name) ||
                "java.math.BigDecimal".equals(name) ||
                "java.lang.Number".equals(name);
    }

    private boolean isInterfaceOrAbstract(TypeMirror type) {
        TypeElement element = types.typeElement(types.concrete(type));
        return element == null || element.getKind().isInterface() ||
                element.getModifiers().contains(Modifier.ABSTRACT);
    }
}
