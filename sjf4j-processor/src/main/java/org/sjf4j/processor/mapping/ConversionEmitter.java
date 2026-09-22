package org.sjf4j.processor.mapping;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Emits Java expressions and statements for fully compiled value conversions.
 *
 * <p>All recursive conversion analysis happens during {@link #prepare}. The
 * actual emission phase performs no converter, creator or property discovery.</p>
 */
public final class ConversionEmitter
        implements MappingEmitter.ConversionWriter {

    private static final String BINDER_FIELD =
            "_sjf4j_node_binder";

    private final ProcessorContext context;
    private final TypeSystem types;

    private final ConversionCompiler compiler;

    private final IdentityHashMap<
            ConverterResolver.Conversion,
            ConversionCompiler.CompiledConversion> compiled =
            new IdentityHashMap<>();

    private final Map<String, String> importedMapperFields =
            new LinkedHashMap<>();

    private boolean binderRequired;


    public ConversionEmitter(
            ProcessorContext context,
            TypeElement mapper,
            List<MappingPlan> plans) {

        this.context = context;
        this.types = context.types;

        this.compiler =
                new ConversionCompiler(
                        context,
                        mapper,
                        plans);
    }


    // -------------------------------------------------------------------------
    // Prepare
    // -------------------------------------------------------------------------

    /**
     * Compiles all conversions referenced by mapper methods and registers
     * generated fields required by imported mappers and runtime-only fallback
     * conversions.
     */
    public boolean prepare(
            List<MappingCompiler.CompiledMethod> methods,
            GeneratedClass generated) {

        for (MappingCompiler.CompiledMethod method :
                methods) {

            if (!prepare(
                    method,
                    generated)) {

                return false;
            }
        }

        registerFields(
                generated);

        return true;
    }


    private boolean prepare(
            MappingCompiler.CompiledMethod method,
            GeneratedClass generated) {

        if (method.kind() ==
                MappingCompiler.CompiledMethod.Kind.ROOT) {

            return prepare(
                    method.plan(),
                    method.rootConversion(),
                    generated);
        }

        for (MappingCompiler.ConstructorArgument argument :
                method.constructorArguments()) {

            if (!prepare(
                    method.plan(),
                    argument.value(),
                    generated)) {

                return false;
            }
        }

        for (MappingCompiler.Assignment assignment :
                method.assignments()) {

            if (!prepare(
                    method.plan(),
                    assignment.value(),
                    generated)) {

                return false;
            }
        }

        return true;
    }


    private boolean prepare(
            MappingPlan plan,
            MappingCompiler.Value value,
            GeneratedClass generated) {

        if (value.kind() !=
                MappingCompiler.Value.Kind.READ) {

            return true;
        }

        return prepare(
                plan,
                value.conversion(),
                generated);
    }


    private boolean prepare(
            MappingPlan plan,
            ConverterResolver.Conversion conversion,
            GeneratedClass generated) {

        if (conversion == null) {
            return false;
        }

        if (compiled.containsKey(
                conversion)) {

            return true;
        }

        ConversionCompiler.CompiledConversion resolved =
                compiler.compile(
                        plan,
                        conversion,
                        generated);

        if (resolved == null) {
            return false;
        }

        compiled.put(
                conversion,
                resolved);

        inspect(
                resolved);

        return true;
    }


    private void inspect(
            ConversionCompiler.CompiledConversion conversion) {

        switch (conversion.kind()) {
            case METHOD:
                if (conversion.mapperType() != null) {
                    registerImportedMapper(
                            conversion.mapperType());
                }
                return;

            case SCALAR:
            case ONE_OF:
                /*
                 * Built-in scalar fast paths are emitted directly, but scalar
                 * @NodeValue and OneOf may need the normal runtime binder.
                 */
                binderRequired = true;
                return;

            case CONTAINER:
                inspect(
                        conversion.elementConversion());
                return;

            case MAP:
                inspect(
                        conversion.keyConversion());

                inspect(
                        conversion.valueConversion());
                return;

            case STRUCTURAL:
                for (ConversionCompiler.StructuralArgument argument :
                        conversion.arguments()) {

                    inspect(
                            argument.conversion());
                }

                for (ConversionCompiler.StructuralProperty property :
                        conversion.properties()) {

                    inspect(
                            property.conversion());
                }

                ConversionCompiler.DynamicObjectPlan dynamic =
                        conversion.dynamicObject();

                if (dynamic != null) {
                    if (dynamic.valueConversion() != null) {
                        inspect(
                                dynamic.valueConversion());
                    }

                    for (ConversionCompiler.DynamicProperty property :
                            dynamic.properties()) {

                        inspect(
                                property.conversion());
                    }
                }

                return;

            case DIRECT:
                return;

            default:
                throw new AssertionError(
                        conversion.kind());
        }
    }


    private void registerImportedMapper(
            TypeElement mapper) {

        String type =
                mapper.getQualifiedName()
                        .toString();

        if (importedMapperFields.containsKey(
                type)) {

            return;
        }

        String base =
                "_sjf4j_mapper_" +
                        mapper.getSimpleName();

        String name =
                base;

        int index = 2;

        while (importedMapperFields
                .containsValue(name)) {

            name =
                    base +
                            index++;
        }

        importedMapperFields.put(
                type,
                name);
    }


    private void registerFields(
            GeneratedClass generated) {

        for (Map.Entry<String, String> entry :
                importedMapperFields.entrySet()) {

            String type =
                    entry.getKey();

            String field =
                    entry.getValue();

            generated.addField(out ->
                    out.line(
                            "private static final " +
                                    type +
                                    " " +
                                    field +
                                    " = org.sjf4j.CompiledNodes.of(" +
                                    type +
                                    ".class);"));
        }

        if (binderRequired) {
            generated.addField(out ->
                    out.line(
                            "private static final org.sjf4j.binding.SimpleNodeBinder " +
                                    BINDER_FIELD +
                                    " = new org.sjf4j.binding.SimpleNodeBinder();"));
        }
    }


    // -------------------------------------------------------------------------
    // MappingEmitter boundary
    // -------------------------------------------------------------------------

    @Override
    public String emit(
            JavaWriter out,
            NameAllocator names,
            MappingPlan plan,
            ConverterResolver.Conversion conversion,
            String source,
            TypeMirror sourceType,
            TypeMirror targetType,
            GeneratedClass generated) {

        ConversionCompiler.CompiledConversion resolved =
                compiled.get(
                        conversion);

        if (resolved == null) {
            throw new IllegalStateException(
                    "conversion was not prepared: " +
                            sourceType +
                            " -> " +
                            targetType);
        }

        return emit(
                out,
                names,
                resolved,
                source);
    }


    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    private String emit(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source) {

        switch (conversion.kind()) {
            case DIRECT:
                return source;

            case METHOD:
                return emitMethod(
                        conversion,
                        source);

            case SCALAR:
                return emitScalar(
                        out,
                        names,
                        conversion,
                        source);

            case ONE_OF:
                return emitRuntimeBinding(
                        out,
                        names,
                        conversion.targetType(),
                        source,
                        "oneOf");

            case CONTAINER:
                return emitContainer(
                        out,
                        names,
                        conversion,
                        source);

            case MAP:
                return emitMap(
                        out,
                        names,
                        conversion,
                        source);

            case STRUCTURAL:
                return emitStructural(
                        out,
                        names,
                        conversion,
                        source);

            default:
                throw new AssertionError(
                        conversion.kind());
        }
    }


    // -------------------------------------------------------------------------
    // Method
    // -------------------------------------------------------------------------

    private String emitMethod(
            ConversionCompiler.CompiledConversion conversion,
            String source) {

        ExecutableElement method =
                conversion.method();

        if (conversion.mapperType() ==
                null) {

            return method.getSimpleName() +
                    "(" +
                    source +
                    ")";
        }

        String type =
                conversion.mapperType()
                        .getQualifiedName()
                        .toString();

        String field =
                importedMapperFields.get(
                        type);

        if (field == null) {
            throw new IllegalStateException(
                    "imported mapper was not prepared: " +
                            type);
        }

        return field +
                "." +
                method.getSimpleName() +
                "(" +
                source +
                ")";
    }


    // -------------------------------------------------------------------------
    // Scalar
    // -------------------------------------------------------------------------

    private String emitScalar(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source) {

        TypeMirror sourceType =
                types.boxed(
                        conversion.sourceType());

        TypeMirror targetType =
                types.boxed(
                        conversion.targetType());

        String sourceName =
                rawName(sourceType);

        String targetName =
                rawName(targetType);

        /*
         * Number -> Number
         */
        if (isNumber(sourceName) &&
                isNumber(targetName)) {

            String value =
                    names.newName(
                            "converted");

            out.line(
                    localType(targetType) +
                            " " +
                            value +
                            " = " +
                            source +
                            " == null ? null : (" +
                            localType(targetType) +
                            ") org.sjf4j.node.Numbers.to((java.lang.Number) " +
                            source +
                            ", " +
                            classLiteral(targetType) +
                            ");");

            return value;
        }

        /*
         * char / Character -> String
         */
        if ("java.lang.String".equals(
                targetName) &&
                "java.lang.Character".equals(
                        sourceName)) {

            String value =
                    names.newName(
                            "converted");

            out.line(
                    "java.lang.String " +
                            value +
                            " = " +
                            source +
                            " == null ? null : java.lang.String.valueOf(" +
                            source +
                            ");");

            return value;
        }

        /*
         * enum -> String
         */
        if ("java.lang.String".equals(
                targetName) &&
                isEnum(sourceType)) {

            String value =
                    names.newName(
                            "converted");

            out.line(
                    "java.lang.String " +
                            value +
                            " = " +
                            source +
                            " == null ? null : " +
                            source +
                            ".name();");

            return value;
        }

        /*
         * String -> Character
         */
        if ("java.lang.String".equals(
                sourceName) &&
                "java.lang.Character".equals(
                        targetName)) {

            String value =
                    names.newName(
                            "converted");

            out.line(
                    "java.lang.Character " +
                            value +
                            " = " +
                            source +
                            " == null ? null : " +
                            source +
                            ".charAt(0);");

            return value;
        }

        /*
         * String -> enum
         */
        if ("java.lang.String".equals(
                sourceName) &&
                isEnum(targetType)) {

            String value =
                    names.newName(
                            "converted");

            out.line(
                    localType(targetType) +
                            " " +
                            value +
                            " = " +
                            source +
                            " == null ? null : " +
                            localType(targetType) +
                            ".valueOf(" +
                            source +
                            ");");

            return value;
        }

        /*
         * Boolean -> Boolean should normally have resolved as DIRECT.
         */
        if ("java.lang.Boolean".equals(
                sourceName) &&
                "java.lang.Boolean".equals(
                        targetName)) {

            return source;
        }

        /*
         * @NodeValue / registered scalar codecs.
         */
        return emitRuntimeBinding(
                out,
                names,
                conversion.targetType(),
                source,
                "converted");
    }


    // -------------------------------------------------------------------------
    // Container
    // -------------------------------------------------------------------------

    private String emitContainer(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source) {

        TypeMirror sourceType =
                conversion.sourceType();

        TypeMirror targetType =
                conversion.targetType();

        NodeKind sourceKind =
                types.nodeKind(
                        sourceType);

        NodeKind targetKind =
                types.nodeKind(
                        targetType);

        String result =
                names.newName(
                        "converted");

        String targetLocalType =
                localType(targetType);

        out.line(
                targetLocalType +
                        " " +
                        result +
                        " = null;");

        out.beginBlock(
                "if (" +
                        source +
                        " != null)");

        String size =
                names.newName("size");

        out.line(
                "int " +
                        size +
                        " = " +
                        sizeExpression(
                                source,
                                sourceKind) +
                        ";");

        if (targetKind ==
                NodeKind.ARRAY_ARRAY) {

            TypeMirror component =
                    ((ArrayType)
                            types.concrete(
                                    targetType))
                            .getComponentType();

            out.line(
                    result +
                            " = new " +
                            component +
                            "[" +
                            size +
                            "];");

        } else {
            out.line(
                    result +
                            " = " +
                            newContainerExpression(
                                    targetType,
                                    targetKind,
                                    size) +
                            ";");
        }

        if (sourceKind ==
                NodeKind.ARRAY_SET) {

            emitSetLoop(
                    out,
                    names,
                    conversion,
                    source,
                    result,
                    targetKind);

        } else {
            emitIndexedContainerLoop(
                    out,
                    names,
                    conversion,
                    source,
                    result,
                    sourceKind,
                    targetKind,
                    size);
        }

        out.endBlock();

        return result;
    }


    private void emitIndexedContainerLoop(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source,
            String result,
            NodeKind sourceKind,
            NodeKind targetKind,
            String size) {

        String index =
                names.newName("i");

        out.line(
                "for (int " +
                        index +
                        " = 0; " +
                        index +
                        " < " +
                        size +
                        "; " +
                        index +
                        "++) {");

        out.indent();

        String item =
                names.newName("item");

        out.line(
                localType(
                        conversion.sourceElementType()) +
                        " " +
                        item +
                        " = " +
                        sourceElementExpression(
                                source,
                                index,
                                sourceKind,
                                conversion.sourceElementType()) +
                        ";");

        String converted =
                emit(
                        out,
                        names,
                        conversion.elementConversion(),
                        item);

        emitContainerAdd(
                out,
                result,
                targetKind,
                index,
                converted);

        out.dedent();
        out.line("}");
    }


    private void emitSetLoop(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source,
            String result,
            NodeKind targetKind) {

        String item =
                names.newName("item");

        String index =
                names.newName("i");

        out.line(
                "int " +
                        index +
                        " = 0;");

        out.line(
                "for (" +
                        localType(
                                conversion.sourceElementType()) +
                        " " +
                        item +
                        " : " +
                        source +
                        ") {");

        out.indent();

        String converted =
                emit(
                        out,
                        names,
                        conversion.elementConversion(),
                        item);

        emitContainerAdd(
                out,
                result,
                targetKind,
                index,
                converted);

        out.line(
                index +
                        "++;");

        out.dedent();
        out.line("}");
    }


    private void emitContainerAdd(
            JavaWriter out,
            String result,
            NodeKind targetKind,
            String index,
            String value) {

        if (targetKind ==
                NodeKind.ARRAY_ARRAY) {

            out.line(
                    result +
                            "[" +
                            index +
                            "] = " +
                            value +
                            ";");

            return;
        }

        switch (targetKind) {
            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                out.line(
                        result +
                                ".add(" +
                                value +
                                ");");
                return;

            default:
                throw new IllegalStateException(
                        "unsupported target container " +
                                targetKind);
        }
    }


    // -------------------------------------------------------------------------
    // Map
    // -------------------------------------------------------------------------

    private String emitMap(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source) {

        String result =
                names.newName(
                        "converted");

        out.line(
                localType(
                        conversion.targetType()) +
                        " " +
                        result +
                        " = null;");

        out.beginBlock(
                "if (" +
                        source +
                        " != null)");

        out.line(
                result +
                        " = " +
                        newMapExpression(
                                conversion.targetType()) +
                        ";");

        String entry =
                names.newName(
                        "entry");

        out.line(
                "for (java.util.Map.Entry<" +
                        localType(
                                conversion.sourceKeyType()) +
                        ", " +
                        localType(
                                conversion.sourceValueType()) +
                        "> " +
                        entry +
                        " : " +
                        source +
                        ".entrySet()) {");

        out.indent();

        String key =
                emit(
                        out,
                        names,
                        conversion.keyConversion(),
                        entry +
                                ".getKey()");

        String value =
                emit(
                        out,
                        names,
                        conversion.valueConversion(),
                        entry +
                                ".getValue()");

        out.line(
                result +
                        ".put(" +
                        key +
                        ", " +
                        value +
                        ");");

        out.dedent();
        out.line("}");

        out.endBlock();

        return result;
    }


    // -------------------------------------------------------------------------
    // Structural
    // -------------------------------------------------------------------------

    private String emitStructural(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source) {

        ConversionCompiler.DynamicObjectPlan dynamic =
                conversion.dynamicObject();

        if (dynamic != null) {
            return emitDynamicObject(
                    out,
                    names,
                    conversion,
                    dynamic,
                    source);
        }

        return emitTypedObject(
                out,
                names,
                conversion,
                source);
    }


    // -------------------------------------------------------------------------
    // Structural: typed target
    // -------------------------------------------------------------------------

    private String emitTypedObject(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source) {

        String result =
                names.newName(
                        "converted");

        TypeMirror workingType =
                conversion.workingTargetType();

        out.line(
                localType(
                        workingType) +
                        " " +
                        result +
                        " = null;");

        out.beginBlock(
                "if (" +
                        source +
                        " != null)");

        CreatorResolver.Creation creation =
                conversion.creation();

        if (creation == null) {
            throw new IllegalStateException(
                    "typed structural conversion has no creator");
        }

        switch (creation.kind()) {
            case NO_ARGS:
                out.line(
                        result +
                                " = new " +
                                workingType +
                                "();");
                break;

            case FACTORY:
                out.line(
                        result +
                                " = " +
                                factoryExpression(
                                        creation) +
                                ";");
                break;

            case CONSTRUCTOR:
                emitStructuralConstructor(
                        out,
                        names,
                        conversion,
                        source,
                        result);
                break;

            default:
                throw new AssertionError(
                        creation.kind());
        }

        for (ConversionCompiler.StructuralProperty property :
                conversion.properties()) {

            String raw =
                    emitAccessRead(
                            out,
                            names,
                            source,
                            property.name(),
                            property.source());

            String value =
                    emit(
                            out,
                            names,
                            property.conversion(),
                            raw);

            emitAccessWrite(
                    out,
                    result,
                    property.name(),
                    property.target(),
                    value);
        }

        out.endBlock();

        return result;
    }


    private void emitStructuralConstructor(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            String source,
            String result) {

        List<ConversionCompiler.StructuralArgument> arguments =
                conversion.arguments();

        List<String> values =
                new ArrayList<>(
                        arguments.size());

        for (ConversionCompiler.StructuralArgument argument :
                arguments) {

            String raw =
                    emitAccessRead(
                            out,
                            names,
                            source,
                            argument.name(),
                            argument.source());

            values.add(
                    emit(
                            out,
                            names,
                            argument.conversion(),
                            raw));
        }

        StringBuilder expression =
                new StringBuilder();

        expression.append(
                        "new ")
                .append(
                        conversion.workingTargetType())
                .append('(');

        for (int i = 0;
             i < values.size();
             i++) {

            if (i != 0) {
                expression.append(", ");
            }

            expression.append(
                    values.get(i));
        }

        expression.append(')');

        out.line(
                result +
                        " = " +
                        expression +
                        ";");
    }


    // -------------------------------------------------------------------------
    // Structural: dynamic target
    // -------------------------------------------------------------------------

    private String emitDynamicObject(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.CompiledConversion conversion,
            ConversionCompiler.DynamicObjectPlan dynamic,
            String source) {

        String result =
                names.newName(
                        "converted");

        out.line(
                localType(
                        conversion.targetType()) +
                        " " +
                        result +
                        " = null;");

        out.beginBlock(
                "if (" +
                        source +
                        " != null)");

        out.line(
                result +
                        " = " +
                        newObjectExpression(
                                conversion.targetType()) +
                        ";");

        if (dynamic.kind() ==
                ConversionCompiler.DynamicObjectPlan.Kind.STATIC_PROPERTIES) {

            for (ConversionCompiler.DynamicProperty property :
                    dynamic.properties()) {

                String raw =
                        emitAccessRead(
                                out,
                                names,
                                source,
                                property.name(),
                                property.source());

                String value =
                        emit(
                                out,
                                names,
                                property.conversion(),
                                raw);

                out.line(
                        result +
                                ".put(" +
                                JavaWriter.stringLiteral(
                                        property.name()) +
                                ", " +
                                value +
                                ");");
            }

        } else {
            emitDynamicEntries(
                    out,
                    names,
                    dynamic,
                    source,
                    result);
        }

        out.endBlock();

        return result;
    }


    private void emitDynamicEntries(
            JavaWriter out,
            NameAllocator names,
            ConversionCompiler.DynamicObjectPlan dynamic,
            String source,
            String result) {

        String entry =
                names.newName(
                        "entry");

        out.line(
                "for (java.util.Map.Entry<String, ?> " +
                        entry +
                        " : ((java.util.Map<String, ?>) " +
                        source +
                        ").entrySet()) {");

        out.indent();

        String value =
                emit(
                        out,
                        names,
                        dynamic.valueConversion(),
                        entry +
                                ".getValue()");

        out.line(
                result +
                        ".put(" +
                        entry +
                        ".getKey(), " +
                        value +
                        ");");

        out.dedent();
        out.line("}");
    }


    // -------------------------------------------------------------------------
    // Runtime fallback
    // -------------------------------------------------------------------------

    private String emitRuntimeBinding(
            JavaWriter out,
            NameAllocator names,
            TypeMirror targetType,
            String source,
            String hint) {

        String result =
                names.newName(
                        hint);

        out.line(
                localType(
                        targetType) +
                        " " +
                        result +
                        " = (" +
                        localType(
                                targetType) +
                        ") " +
                        BINDER_FIELD +
                        ".readNode(" +
                        source +
                        ", " +
                        classLiteral(
                                targetType) +
                        ", false);");

        return result;
    }


    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    private String emitAccessRead(
            JavaWriter out,
            NameAllocator names,
            String owner,
            String name,
            NodeAccess access) {

        String result =
                names.newName(
                        name);

        out.line(
                localType(
                        access.readType()) +
                        " " +
                        result +
                        " = " +
                        accessReadExpression(
                                owner,
                                name,
                                access) +
                        ";");

        return result;
    }


    private String accessReadExpression(
            String owner,
            String name,
            NodeAccess access) {

        String key =
                JavaWriter.stringLiteral(
                        name);

        switch (access.kind()) {
            case PROPERTY: {
                PropertyAccess read =
                        access.property()
                                .read();

                return read.isMethod()
                        ? owner +
                        "." +
                        read.memberName() +
                        "()"
                        : owner +
                        "." +
                        read.memberName();
            }

            case MAP:
                return cast(
                        access.readType(),
                        owner +
                                ".get(" +
                                key +
                                ")");

            case JSON_OBJECT:
                return cast(
                        access.readType(),
                        owner +
                                ".getNode(" +
                                key +
                                ")");

            case DYNAMIC:
            case EXTERNAL:
                return cast(
                        access.readType(),
                        "org.sjf4j.Nodes.getInObject(" +
                                owner +
                                ", " +
                                key +
                                ")");

            default:
                throw new IllegalStateException(
                        "unsupported structural read: " +
                                access.kind());
        }
    }


    private void emitAccessWrite(
            JavaWriter out,
            String owner,
            String name,
            NodeAccess access,
            String value) {

        String key =
                JavaWriter.stringLiteral(
                        name);

        switch (access.kind()) {
            case PROPERTY: {
                PropertyAccess write =
                        access.property()
                                .write();

                if (write.isMethod()) {
                    out.line(
                            owner +
                                    "." +
                                    write.memberName() +
                                    "(" +
                                    value +
                                    ");");

                } else {
                    out.line(
                            owner +
                                    "." +
                                    write.memberName() +
                                    " = " +
                                    value +
                                    ";");
                }

                return;
            }

            case MAP:
            case JSON_OBJECT:
                out.line(
                        owner +
                                ".put(" +
                                key +
                                ", " +
                                value +
                                ");");
                return;

            case DYNAMIC:
            case EXTERNAL:
                out.line(
                        "org.sjf4j.Nodes.putInObject(" +
                                owner +
                                ", " +
                                key +
                                ", " +
                                value +
                                ");");
                return;

            default:
                throw new IllegalStateException(
                        "unsupported structural write: " +
                                access.kind());
        }
    }


    // -------------------------------------------------------------------------
    // Container expressions
    // -------------------------------------------------------------------------

    private String sourceElementExpression(
            String source,
            String index,
            NodeKind kind,
            TypeMirror elementType) {

        switch (kind) {
            case ARRAY_ARRAY:
                return source +
                        "[" +
                        index +
                        "]";

            case ARRAY_LIST:
                return source +
                        ".get(" +
                        index +
                        ")";

            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return cast(
                        elementType,
                        source +
                                ".getNode(" +
                                index +
                                ")");

            default:
                throw new IllegalStateException(
                        "unsupported indexed source container " +
                                kind);
        }
    }


    private String sizeExpression(
            String source,
            NodeKind kind) {

        switch (kind) {
            case ARRAY_ARRAY:
                return source +
                        ".length";

            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return source +
                        ".size()";

            default:
                throw new IllegalStateException(
                        "unsupported source container " +
                                kind);
        }
    }


    private String newContainerExpression(
            TypeMirror type,
            NodeKind kind,
            String size) {

        switch (kind) {
            case ARRAY_LIST:
                if (types.isSameErasure(
                        type,
                        types.listType())) {

                    return "new java.util.ArrayList<>(" +
                            size +
                            ")";
                }

                if (isInterfaceOrAbstract(
                        type)) {

                    return "new java.util.ArrayList<>(" +
                            size +
                            ")";
                }

                return "new " +
                        type +
                        "()";

            case ARRAY_SET:
                if (isInterfaceOrAbstract(
                        type)) {

                    return "new java.util.LinkedHashSet<>(" +
                            size +
                            ")";
                }

                return "new " +
                        type +
                        "()";

            case ARRAY_JSON_ARRAY:
                return "new org.sjf4j.JsonArray()";

            case ARRAY_JAJO:
                return "new " +
                        type +
                        "()";

            default:
                throw new IllegalStateException(
                        "unsupported target container " +
                                kind);
        }
    }


    private String newMapExpression(
            TypeMirror type) {

        if (types.isSameErasure(
                type,
                types.mapType()) ||
                isInterfaceOrAbstract(type)) {

            return "new java.util.LinkedHashMap<>()";
        }

        return "new " +
                type +
                "()";
    }


    private String newObjectExpression(
            TypeMirror type) {

        NodeKind kind =
                types.nodeKind(type);

        switch (kind) {
            case OBJECT_MAP:
                return newMapExpression(type);

            case OBJECT_JSON_OBJECT:
                return "new org.sjf4j.JsonObject()";

            default:
                throw new IllegalStateException(
                        "unsupported dynamic object target " +
                                type);
        }
    }


    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    private String factoryExpression(
            CreatorResolver.Creation creation) {

        ExecutableElement factory =
                creation.factory();

        if (creation.factoryOwner() ==
                null) {

            return factory.getSimpleName() +
                    "()";
        }

        return creation.factoryOwner()
                .getQualifiedName() +
                "." +
                factory.getSimpleName() +
                "()";
    }


    // -------------------------------------------------------------------------
    // Type helpers
    // -------------------------------------------------------------------------

    private String localType(
            TypeMirror type) {

        return types.boxed(
                        types.concrete(type))
                .toString();
    }


    private String classLiteral(
            TypeMirror type) {

        TypeMirror boxed =
                types.boxed(
                        types.concrete(type));

        return context.typeUtils
                .erasure(boxed)
                .toString() +
                ".class";
    }


    private String cast(
            TypeMirror type,
            String expression) {

        TypeMirror concrete =
                types.concrete(type);

        if (types.isObject(concrete)) {
            return expression;
        }

        return "(" +
                localType(concrete) +
                ") " +
                expression;
    }


    private String rawName(
            TypeMirror type) {

        TypeMirror boxed =
                types.boxed(
                        types.concrete(type));

        return context.typeUtils
                .erasure(boxed)
                .toString();
    }


    private boolean isEnum(
            TypeMirror type) {

        TypeElement element =
                types.typeElement(
                        types.concrete(type));

        return element != null &&
                "ENUM".equals(
                        element.getKind()
                                .name());
    }


    private boolean isNumber(
            String name) {

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


    private boolean isInterfaceOrAbstract(
            TypeMirror type) {

        TypeElement element =
                types.typeElement(
                        types.concrete(type));

        return element == null ||
                element.getKind()
                        .isInterface() ||
                element.getModifiers()
                        .contains(
                                Modifier.ABSTRACT);
    }
}