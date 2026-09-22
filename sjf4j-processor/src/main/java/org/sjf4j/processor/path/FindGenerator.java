package org.sjf4j.processor.path;

import org.sjf4j.NodeKind;
import org.sjf4j.exception.JsonException;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;


/**
 * Generates implementations for {@code @FindByPath} methods.
 *
 * <p>Supported multi-target paths are emitted as direct Java loops. Filters
 * keep their parsed expression as a generated static field and evaluate only
 * the filter condition at runtime. Descendant paths fall back to JsonPath when
 * explicitly enabled by the annotation.</p>
 */
public final class FindGenerator {

    private final ProcessorContext context;
    private final TypeSystem types;

    private int filterSeq;
    private int fallbackSeq;


    public FindGenerator(ProcessorContext context) {
        this.context = context;
        this.types = context.types;
    }


    /**
     * Validates and generates one @FindByPath method.
     */
    public void generate(
            ExecutableElement method,
            GeneratedClass generated,
            String expression,
            boolean allowFallback) {

        List<? extends VariableElement> parameters =
                method.getParameters();

        if (parameters.isEmpty()) {
            error(
                    method,
                    generated,
                    "@FindByPath method must have a root parameter");
            return;
        }

        if (parameters.size() != 1) {
            error(
                    method,
                    generated,
                    "@FindByPath does not support path parameters");
            return;
        }

        TypeMirror returnType =
                method.getReturnType();

        /*
         * Find is intentionally defined as List<T>, rather than arbitrary
         * List implementations. Generated methods internally use ArrayList.
         */
        if (!types.isSameErasure(
                returnType,
                types.listType())) {

            error(
                    method,
                    generated,
                    "@FindByPath return type must be List<T>, but was " +
                            returnType);
            return;
        }

        JsonPath path;

        try {
            path =
                    JsonPath.parse(
                            expression);
        } catch (JsonException e) {
            error(
                    method,
                    generated,
                    "Invalid JSON Path value: " +
                            e.getMessage());
            return;
        }

        PathSegment[] segments =
                path.segments();

        boolean hasFilter = false;
        boolean hasDescendant = false;

        for (int i = 1; i < segments.length; i++) {
            PathSegment segment =
                    segments[i];

            if (segment instanceof PathSegment.Param) {
                error(
                        method,
                        generated,
                        "@FindByPath does not support path parameters");
                return;
            }

            if (segment instanceof PathSegment.Append) {
                error(
                        method,
                        generated,
                        "@FindByPath does not support append segments");
                return;
            }

            if (segment instanceof PathSegment.Filter) {
                hasFilter = true;
            } else if (segment instanceof PathSegment.Descendant) {
                hasDescendant = true;
            }
        }

        if (hasFilter && !allowFallback) {
            error(
                    method,
                    generated,
                    "@FindByPath path '" +
                            expression +
                            "' requires allowFallback=true because filter expressions use runtime evaluation");
            return;
        }

        if (hasDescendant && !allowFallback) {
            error(
                    method,
                    generated,
                    "@FindByPath path '" +
                            expression +
                            "' requires allowFallback=true because descendant is not fully compiled");
            return;
        }

        TypeMirror elementType =
                types.listWriteElementType(
                        returnType);

        if (elementType == null) {
            error(
                    method,
                    generated,
                    "@FindByPath return List element type is not writable: " +
                            returnType);
            return;
        }

        VariableElement root =
                parameters.get(0);

        if (!validateMapPathKeys(
                method,
                generated,
                root.asType(),
                segments)) {

            return;
        }

        if (tryRoot(
                method,
                generated,
                path,
                returnType,
                elementType,
                root)) {
            return;
        }

        if (hasDescendant) {
            emitFallback(
                    method,
                    generated,
                    expression,
                    elementType,
                    root);
            return;
        }

        if (tryWildcard(
                method,
                generated,
                expression,
                path,
                returnType,
                elementType,
                root)) {
            return;
        }

        if (tryUnion(
                method,
                generated,
                path,
                returnType,
                elementType,
                root)) {
            return;
        }

        error(
                method,
                generated,
                "@FindByPath unsupported path '" +
                        expression +
                        "': only root, wildcard/slice/filter, or one name/index union with static name/index segments is supported");
    }


    // -------------------------------------------------------------------------
    // Root
    // -------------------------------------------------------------------------

    private boolean tryRoot(
            ExecutableElement method,
            GeneratedClass generated,
            JsonPath path,
            TypeMirror returnType,
            TypeMirror elementType,
            VariableElement root) {

        if (path.segments().length != 1) {
            return false;
        }

        if (!canAddToResult(
                root.asType(),
                elementType)) {

            error(
                    method,
                    generated,
                    "@FindByPath result type mismatch: cannot add " +
                            root.asType() +
                            " to List<" +
                            elementType +
                            ">");
            return true;
        }

        generated.addMethod(out -> {
            NameAllocator names =
                    names(root);

            String result =
                    names.newName("result");

            out.line("@Override");
            out.beginBlock(
                    methodHeader(
                            method,
                            root));

            if (!root.asType()
                    .getKind()
                    .isPrimitive()) {

                out.line(
                        "java.util.Objects.requireNonNull(" +
                                root.getSimpleName() +
                                ", " +
                                JavaWriter.stringLiteral(
                                        root.getSimpleName()
                                                .toString()) +
                                ");");
            }

            out.line(
                    "java.util.ArrayList<" +
                            localType(elementType) +
                            "> " +
                            result +
                            " = new java.util.ArrayList<>(1);");

            out.line(
                    result +
                            ".add(" +
                            root.getSimpleName() +
                            ");");

            out.line(
                    "return " +
                            result +
                            ";");

            out.endBlock();
        });

        return true;
    }


    // -------------------------------------------------------------------------
    // Wildcard / slice / filter
    // -------------------------------------------------------------------------

    private boolean tryWildcard(
            ExecutableElement method,
            GeneratedClass generated,
            String rawExpression,
            JsonPath path,
            TypeMirror returnType,
            TypeMirror elementType,
            VariableElement root) {

        PathSegment[] segments =
                path.segments();

        boolean hasMulti = false;

        TypeMirror current =
                root.asType();

        for (int i = 1; i < segments.length; i++) {
            PathSegment segment =
                    segments[i];

            if (segment instanceof PathSegment.Wildcard ||
                    segment instanceof PathSegment.Slice) {

                hasMulti = true;

                if (segment instanceof PathSegment.Wildcard &&
                        isMap(current)) {

                    current =
                            types.mapReadValueType(
                                    current);
                } else {
                    if (!isSequence(current)) {
                        return false;
                    }

                    current =
                            sequenceElementType(
                                    current);
                }

                if (current == null) {
                    return false;
                }

                continue;
            }

            if (segment instanceof PathSegment.Filter) {
                hasMulti = true;

                if (isSequence(current)) {
                    current =
                            sequenceElementType(
                                    current);

                } else if (isMap(current)) {
                    current =
                            types.mapReadValueType(
                                    current);

                } else {
                    return false;
                }

                if (current == null) {
                    return false;
                }

                continue;
            }

            if (segment instanceof PathSegment.Name ||
                    segment instanceof PathSegment.Index) {

                current =
                        resolveDirectType(
                                current,
                                segment);

                if (current == null) {
                    return false;
                }

                continue;
            }

            return false;
        }

        if (!hasMulti) {
            return false;
        }

        if (!canAddToResult(
                current,
                elementType)) {

            return false;
        }

        /*
         * Ensure every static segment can actually be emitted before fields or
         * methods are contributed.
         */
        if (!canEmitWildcardPath(
                root.asType(),
                segments,
                1)) {

            return false;
        }

        String[] filterFields =
                addFilterFields(
                        generated,
                        rawExpression,
                        segments);

        generated.addMethod(out -> {
            NameAllocator names =
                    names(root);

            String result =
                    names.newName("result");

            String rootName =
                    root.getSimpleName()
                            .toString();

            out.line("@Override");
            out.beginBlock(
                    methodHeader(
                            method,
                            root));

            out.line(
                    "java.util.ArrayList<" +
                            localType(elementType) +
                            "> " +
                            result +
                            " = new java.util.ArrayList<>();");

            if (!root.asType()
                    .getKind()
                    .isPrimitive()) {

                out.line(
                        "java.util.Objects.requireNonNull(" +
                                rootName +
                                ", " +
                                JavaWriter.stringLiteral(
                                        rootName) +
                                ");");
            }

            emitWildcardSegments(
                    out,
                    names,
                    result,
                    filterFields,
                    segments,
                    1,
                    rootName,
                    rootName,
                    root.asType(),
                    true,
                    true);

            out.line(
                    "return " +
                            result +
                            ";");

            out.endBlock();
        });

        return true;
    }


    /**
     * Verifies that all non-multi segments can be emitted with compile-time
     * access information.
     */
    private boolean canEmitWildcardPath(
            TypeMirror start,
            PathSegment[] segments,
            int index) {

        TypeMirror current =
                start;

        for (int i = index; i < segments.length; i++) {
            PathSegment segment =
                    segments[i];

            if (segment instanceof PathSegment.Wildcard ||
                    segment instanceof PathSegment.Slice) {

                if (segment instanceof PathSegment.Wildcard &&
                        isMap(current)) {

                    current =
                            types.mapReadValueType(
                                    current);
                } else {
                    if (!isSequence(current)) {
                        return false;
                    }

                    current =
                            sequenceElementType(
                                    current);
                }

                continue;
            }

            if (segment instanceof PathSegment.Filter) {
                if (isSequence(current)) {
                    current =
                            sequenceElementType(
                                    current);

                } else if (isMap(current)) {
                    current =
                            types.mapReadValueType(
                                    current);

                } else {
                    return false;
                }

                continue;
            }

            Access access =
                    access(
                            current,
                            "value",
                            segment);

            if (access == null) {
                return false;
            }

            current =
                    access.type;
        }

        return true;
    }


    private void emitWildcardSegments(
            JavaWriter out,
            NameAllocator names,
            String result,
            String[] filterFields,
            PathSegment[] segments,
            int index,
            String rootExpression,
            String expression,
            TypeMirror expressionType,
            boolean topLevel,
            boolean expressionKnownNonNull) {

        if (index == segments.length) {
            out.line(
                    result +
                            ".add(" +
                            expression +
                            ");");
            return;
        }

        PathSegment segment =
                segments[index];

        if (segment instanceof PathSegment.Wildcard ||
                segment instanceof PathSegment.Slice ||
                segment instanceof PathSegment.Filter) {

            if (!expressionKnownNonNull &&
                    !expressionType
                            .getKind()
                            .isPrimitive()) {

                out.line(
                        "if (" +
                                expression +
                                " == null) " +
                                (topLevel
                                        ? "return " + result + ";"
                                        : "continue;"));
            }

            boolean mapValues =
                    isMap(expressionType) &&
                            (segment instanceof PathSegment.Filter ||
                                    segment instanceof PathSegment.Wildcard);

            TypeMirror itemType =
                    mapValues
                            ? types.mapReadValueType(
                            expressionType)
                            : sequenceElementType(
                            expressionType);

            String item =
                    names.newName("item");

            String itemTypeName =
                    localType(itemType);

            if (expressionType.getKind() ==
                    TypeKind.ARRAY) {

                String size =
                        expression +
                                ".length";

                String loopIndex;

                if (segment instanceof PathSegment.Slice) {
                    loopIndex =
                            emitSliceLoop(
                                    out,
                                    names,
                                    (PathSegment.Slice) segment,
                                    size);
                } else {
                    loopIndex =
                            names.newName("i");

                    out.line(
                            "for (int " +
                                    loopIndex +
                                    " = 0; " +
                                    loopIndex +
                                    " < " +
                                    size +
                                    "; " +
                                    loopIndex +
                                    "++) {");

                    out.indent();
                }

                out.line(
                        itemTypeName +
                                " " +
                                item +
                                " = " +
                                expression +
                                "[" +
                                loopIndex +
                                "];");

            } else if (mapValues) {
                out.line(
                        "for (" +
                                itemTypeName +
                                " " +
                                item +
                                " : " +
                                expression +
                                ".values()) {");

                out.indent();

            } else if (isJsonArray(expressionType)) {
                String size =
                        names.newName("size");

                out.line(
                        "int " +
                                size +
                                " = " +
                                expression +
                                ".size();");

                String loopIndex;

                if (segment instanceof PathSegment.Slice) {
                    loopIndex =
                            emitSliceLoop(
                                    out,
                                    names,
                                    (PathSegment.Slice) segment,
                                    size);
                } else {
                    loopIndex =
                            names.newName("i");

                    out.line(
                            "for (int " +
                                    loopIndex +
                                    " = 0; " +
                                    loopIndex +
                                    " < " +
                                    size +
                                    "; " +
                                    loopIndex +
                                    "++) {");

                    out.indent();
                }

                out.line(
                        itemTypeName +
                                " " +
                                item +
                                " = (" +
                                itemTypeName +
                                ") " +
                                expression +
                                ".getNode(" +
                                loopIndex +
                                ");");

            } else {
                /*
                 * List
                 */
                String size =
                        names.newName("size");

                out.line(
                        "int " +
                                size +
                                " = " +
                                expression +
                                ".size();");

                String loopIndex;

                if (segment instanceof PathSegment.Slice) {
                    loopIndex =
                            emitSliceLoop(
                                    out,
                                    names,
                                    (PathSegment.Slice) segment,
                                    size);
                } else {
                    loopIndex =
                            names.newName("i");

                    out.line(
                            "for (int " +
                                    loopIndex +
                                    " = 0; " +
                                    loopIndex +
                                    " < " +
                                    size +
                                    "; " +
                                    loopIndex +
                                    "++) {");

                    out.indent();
                }

                out.line(
                        itemTypeName +
                                " " +
                                item +
                                " = " +
                                expression +
                                ".get(" +
                                loopIndex +
                                ");");
            }

            emitFilterCheck(
                    out,
                    filterFields,
                    index,
                    rootExpression,
                    item);

            emitWildcardSegments(
                    out,
                    names,
                    result,
                    filterFields,
                    segments,
                    index + 1,
                    rootExpression,
                    item,
                    itemType,
                    false,
                    false);

            out.dedent();
            out.line("}");

            return;
        }

        Access access =
                access(
                        expressionType,
                        expression,
                        segment);

        if (access == null) {
            throw new AssertionError(
                    "Unresolved find access at emit time");
        }

        boolean last =
                index ==
                        segments.length - 1;

        String miss =
                topLevel
                        ? "return " + result + ";"
                        : "continue;";

        if (!expressionKnownNonNull &&
                access.needsReceiverNonNull) {

            out.line(
                    "if (" +
                            expression +
                            " == null) " +
                            miss);
        }

        if (access.boundsCheck != null) {
            out.line(
                    "if (!(" +
                            access.boundsCheck +
                            ")) " +
                            miss);
        }

        if (access.presentCheck != null) {
            out.line(
                    "if (!(" +
                            access.presentCheck +
                            ")) " +
                            miss);
        }

        String value =
                names.newName("value");

        out.line(
                localType(access.type) +
                        " " +
                        value +
                        " = " +
                        access.expression +
                        ";");

        if (!last &&
                !access.type
                        .getKind()
                        .isPrimitive()) {

            out.line(
                    "if (" +
                            value +
                            " == null) " +
                            miss);
        }

        emitWildcardSegments(
                out,
                names,
                result,
                filterFields,
                segments,
                index + 1,
                rootExpression,
                value,
                access.type,
                topLevel,
                !last);
    }


    // -------------------------------------------------------------------------
    // Descendant fallback
    // -------------------------------------------------------------------------

    private void emitFallback(
            ExecutableElement method,
            GeneratedClass generated,
            String expression,
            TypeMirror elementType,
            VariableElement root) {

        String field =
                "_sjf4j_find_path_" +
                        fallbackSeq++;

        generated.addField(out ->
                out.line(
                        "private static final org.sjf4j.path.JsonPath " +
                                field +
                                " = org.sjf4j.path.JsonPath.parse(" +
                                JavaWriter.stringLiteral(
                                        expression) +
                                ");"));

        generated.addMethod(out -> {
            String rootName =
                    root.getSimpleName()
                            .toString();

            NameAllocator names =
                    names(root);

            String result =
                    names.newName("result");

            String value =
                    names.newName("value");

            out.line("@Override");

            out.beginBlock(
                    methodHeader(
                            method,
                            root));

            if (!root.asType()
                    .getKind()
                    .isPrimitive()) {

                out.line(
                        "java.util.Objects.requireNonNull(" +
                                rootName +
                                ", " +
                                JavaWriter.stringLiteral(
                                        rootName) +
                                ");");
            }

            out.line(
                    "java.util.ArrayList<" +
                            localType(elementType) +
                            "> " +
                            result +
                            " = new java.util.ArrayList<>();");

            out.beginBlock(
                    "for (Object " +
                            value +
                            " : " +
                            field +
                            ".find(" +
                            rootName +
                            "))");

            out.line(
                    result +
                            ".add(" +
                            objectExpression(
                                    elementType,
                                    value) +
                            ");");

            out.endBlock();
            out.line("return " + result + ";");

            out.endBlock();
        });
    }


    // -------------------------------------------------------------------------
    // Union
    // -------------------------------------------------------------------------

    private boolean tryUnion(
            ExecutableElement method,
            GeneratedClass generated,
            JsonPath path,
            TypeMirror returnType,
            TypeMirror elementType,
            VariableElement root) {

        PathSegment[] segments =
                path.segments();

        int unionIndex = -1;

        for (int i = 1; i < segments.length; i++) {
            PathSegment segment =
                    segments[i];

            if (segment instanceof PathSegment.Union) {
                if (unionIndex >= 0) {
                    return false;
                }

                unionIndex = i;

            } else if (!(segment instanceof PathSegment.Name) &&
                    !(segment instanceof PathSegment.Index)) {

                return false;
            }
        }

        if (unionIndex < 0) {
            return false;
        }

        PathSegment.Union union =
                (PathSegment.Union)
                        segments[unionIndex];

        if (union.union.length == 0) {
            return false;
        }

        boolean indexUnion = true;
        boolean nameUnion = true;

        for (PathSegment token :
                union.union) {

            if (!(token instanceof PathSegment.Index) &&
                    !(token instanceof PathSegment.Slice)) {

                indexUnion = false;
            }

            if (!(token instanceof PathSegment.Name)) {
                nameUnion = false;
            }
        }

        if (!indexUnion &&
                !nameUnion) {

            return false;
        }

        TypeMirror prefixType =
                root.asType();

        for (int i = 1; i < unionIndex; i++) {
            prefixType =
                    resolveDirectType(
                            prefixType,
                            segments[i]);

            if (prefixType == null) {
                return false;
            }
        }

        TypeMirror tokenValueType;

        if (indexUnion) {
            if (!isSequence(prefixType)) {
                return false;
            }

            tokenValueType =
                    sequenceElementType(
                            prefixType);

        } else {
            if (!isStringKeyMap(
                    prefixType)) {

                return false;
            }

            tokenValueType =
                    types.mapReadValueType(
                            prefixType);
        }

        if (tokenValueType == null) {
            return false;
        }

        TypeMirror finalType =
                tokenValueType;

        for (int i = unionIndex + 1;
             i < segments.length;
             i++) {

            finalType =
                    resolveDirectType(
                            finalType,
                            segments[i]);

            if (finalType == null) {
                return false;
            }
        }

        if (!canAddToResult(
                finalType,
                elementType)) {

            return false;
        }

        if (!canEmitAccesses(
                root.asType(),
                segments,
                1,
                unionIndex)) {

            return false;
        }

        if (!canEmitAccesses(
                tokenValueType,
                segments,
                unionIndex + 1,
                segments.length)) {

            return false;
        }

        final int unionAt =
                unionIndex;

        final TypeMirror containerType =
                prefixType;

        final TypeMirror valueType =
                tokenValueType;

        final boolean byIndex =
                indexUnion;

        generated.addMethod(out -> {
            NameAllocator names =
                    names(root);

            String result =
                    names.newName("result");

            String item =
                    names.newName("item");

            String rootName =
                    root.getSimpleName()
                            .toString();

            out.line("@Override");
            out.beginBlock(
                    methodHeader(
                            method,
                            root));

            out.line(
                    "java.util.ArrayList<" +
                            localType(elementType) +
                            "> " +
                            result +
                            " = new java.util.ArrayList<>();");

            if (!root.asType()
                    .getKind()
                    .isPrimitive()) {

                out.line(
                        "java.util.Objects.requireNonNull(" +
                                rootName +
                                ", " +
                                JavaWriter.stringLiteral(
                                        rootName) +
                                ");");
            }

            String expression =
                    rootName;

            TypeMirror expressionType =
                    root.asType();

            /*
             * Static prefix before the union.
             */
            for (int i = 1;
                 i < unionAt;
                 i++) {

                Access access =
                        access(
                                expressionType,
                                expression,
                                segments[i]);

                if (access.boundsCheck != null) {
                    out.line(
                            "if (!(" +
                                    access.boundsCheck +
                                    ")) return " +
                                    result +
                                    ";");
                }

                if (access.presentCheck != null) {
                    out.line(
                            "if (!(" +
                                    access.presentCheck +
                                    ")) return " +
                                    result +
                                    ";");
                }

                String value =
                        names.newName("value");

                out.line(
                        localType(access.type) +
                                " " +
                                value +
                                " = " +
                                access.expression +
                                ";");

                if (!access.type
                        .getKind()
                        .isPrimitive()) {

                    out.line(
                            "if (" +
                                    value +
                                    " == null) return " +
                                    result +
                                    ";");
                }

                expression =
                        value;

                expressionType =
                        access.type;
            }

            if (byIndex) {
                emitIndexUnion(
                        out,
                        names,
                        result,
                        item,
                        union,
                        segments,
                        unionAt,
                        expression,
                        containerType,
                        valueType);

            } else {
                emitNameUnion(
                        out,
                        names,
                        result,
                        item,
                        union,
                        segments,
                        unionAt,
                        expression,
                        valueType);
            }

            out.line(
                    "return " +
                            result +
                            ";");

            out.endBlock();
        });

        return true;
    }


    private void emitIndexUnion(
            JavaWriter out,
            NameAllocator names,
            String result,
            String item,
            PathSegment.Union union,
            PathSegment[] segments,
            int unionIndex,
            String expression,
            TypeMirror containerType,
            TypeMirror valueType) {

        String itemType =
                localType(
                        valueType);

        String size;

        if (containerType.getKind() ==
                TypeKind.ARRAY) {

            size =
                    expression +
                            ".length";

        } else {
            size =
                    names.newName("size");

            out.line(
                    "int " +
                            size +
                            " = " +
                            expression +
                            ".size();");
        }

        for (PathSegment token :
                union.union) {

            if (token instanceof PathSegment.Index) {
                int rawIndex =
                        ((PathSegment.Index) token)
                                .index;

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

                out.line("do {");
                out.indent();

                out.line(
                        itemType +
                                " " +
                                item +
                                " = " +
                                sequenceReadExpression(
                                        containerType,
                                        expression,
                                        index,
                                        valueType) +
                                ";");

                emitStaticSuffix(
                        out,
                        names,
                        result,
                        segments,
                        unionIndex + 1,
                        item,
                        valueType,
                        "break",
                        false);

                out.dedent();
                out.line("} while (false);");

                out.endBlock();

            } else {
                String index =
                        emitSliceLoop(
                                out,
                                names,
                                (PathSegment.Slice) token,
                                size);

                out.line(
                        itemType +
                                " " +
                                item +
                                " = " +
                                sequenceReadExpression(
                                        containerType,
                                        expression,
                                        index,
                                        valueType) +
                                ";");

                emitStaticSuffix(
                        out,
                        names,
                        result,
                        segments,
                        unionIndex + 1,
                        item,
                        valueType,
                        "continue",
                        false);

                out.dedent();
                out.line("}");
            }
        }
    }


    private void emitNameUnion(
            JavaWriter out,
            NameAllocator names,
            String result,
            String item,
            PathSegment.Union union,
            PathSegment[] segments,
            int unionIndex,
            String expression,
            TypeMirror valueType) {

        String itemType =
                localType(
                        valueType);

        for (PathSegment token :
                union.union) {

            String key =
                    ((PathSegment.Name) token)
                            .name;

            String keyLiteral =
                    JavaWriter.stringLiteral(
                            key);

            out.line("{");
            out.indent();

            out.line(
                    itemType +
                            " " +
                            item +
                            " = " +
                            expression +
                            ".get(" +
                            keyLiteral +
                            ");");

            out.beginBlock(
                    "if (" +
                            item +
                            " != null || " +
                            expression +
                            ".containsKey(" +
                            keyLiteral +
                            "))");

            out.line("do {");
            out.indent();

            emitStaticSuffix(
                    out,
                    names,
                    result,
                    segments,
                    unionIndex + 1,
                    item,
                    valueType,
                    "break",
                    false);

            out.dedent();
            out.line("} while (false);");

            out.endBlock();

            out.dedent();
            out.line("}");
        }
    }


    // -------------------------------------------------------------------------
    // Filter fields
    // -------------------------------------------------------------------------

    private String[] addFilterFields(
            GeneratedClass generated,
            String expression,
            PathSegment[] segments) {

        String[] fields =
                new String[segments.length];

        for (int i = 1;
             i < segments.length;
             i++) {

            if (!(segments[i] instanceof
                    PathSegment.Filter)) {

                continue;
            }

            String field =
                    "_sjf4j_find_filter_" +
                            filterSeq++;

            int filterIndex =
                    i;

            fields[i] =
                    field;

            generated.addField(out ->
                    out.line(
                            "private static final org.sjf4j.path.FilterExpr " +
                                    field +
                                    " = ((org.sjf4j.path.PathSegment.Filter) " +
                                    "org.sjf4j.path.JsonPath.parse(" +
                                    JavaWriter.stringLiteral(
                                            expression) +
                                    ").segments()[" +
                                    filterIndex +
                                    "]).filterExpr;"));
        }

        return fields;
    }


    private void emitFilterCheck(
            JavaWriter out,
            String[] filterFields,
            int index,
            String root,
            String item) {

        String field =
                filterFields[index];

        if (field != null) {
            out.line(
                    "if (!" +
                            field +
                            ".evalTruth(" +
                            root +
                            ", " +
                            item +
                            ")) continue;");
        }
    }


    // -------------------------------------------------------------------------
    // Slice
    // -------------------------------------------------------------------------

    /**
     * Emits a slice loop and leaves its body indented.
     */
    private String emitSliceLoop(
            JavaWriter out,
            NameAllocator names,
            PathSegment.Slice slice,
            String sizeExpression) {

        long step =
                slice.step == null
                        ? 1L
                        : slice.step;

        String start =
                names.newName(
                        "sliceStart");

        String end =
                names.newName(
                        "sliceEnd");

        String position =
                names.newName(
                        "slicePosition");

        String index =
                names.newName(
                        "i");

        if (step < 0) {
            out.line(
                    "long " +
                            start +
                            " = " +
                            (slice.start == null
                                    ? "(" + sizeExpression + " - 1L)"
                                    : slice.start + "L") +
                            ";");

            out.line(
                    "long " +
                            end +
                            " = " +
                            (slice.end == null
                                    ? "-1L"
                                    : slice.end + "L") +
                            ";");

            if (slice.start != null &&
                    slice.start < 0) {

                out.line(
                        start +
                                " += " +
                                sizeExpression +
                                ";");
            }

            if (slice.end != null &&
                    slice.end < 0) {

                out.line(
                        end +
                                " += " +
                                sizeExpression +
                                ";");
            }

            out.line(
                    start +
                            " = Math.min(Math.max(" +
                            start +
                            ", -1L), " +
                            sizeExpression +
                            " - 1L);");

            out.line(
                    end +
                            " = Math.min(Math.max(" +
                            end +
                            ", -1L), " +
                            sizeExpression +
                            " - 1L);");

            out.line(
                    "for (long " +
                            position +
                            " = " +
                            start +
                            "; " +
                            position +
                            " > " +
                            end +
                            "; " +
                            position +
                            " += " +
                            step +
                            "L) {");

        } else {
            out.line(
                    "long " +
                            start +
                            " = " +
                            (slice.start == null
                                    ? "0L"
                                    : slice.start + "L") +
                            ";");

            out.line(
                    "long " +
                            end +
                            " = " +
                            (slice.end == null
                                    ? sizeExpression
                                    : slice.end + "L") +
                            ";");

            if (slice.start != null &&
                    slice.start < 0) {

                out.line(
                        start +
                                " += " +
                                sizeExpression +
                                ";");
            }

            if (slice.end != null &&
                    slice.end < 0) {

                out.line(
                        end +
                                " += " +
                                sizeExpression +
                                ";");
            }

            out.line(
                    start +
                            " = Math.min(Math.max(" +
                            start +
                            ", 0L), " +
                            sizeExpression +
                            ");");

            out.line(
                    end +
                            " = Math.min(Math.max(" +
                            end +
                            ", 0L), " +
                            sizeExpression +
                            ");");

            out.line(
                    "for (long " +
                            position +
                            " = " +
                            start +
                            "; " +
                            position +
                            " < " +
                            end +
                            "; " +
                            position +
                            " += " +
                            step +
                            "L) {");
        }

        out.indent();

        out.line(
                "int " +
                        index +
                        " = (int) " +
                        position +
                        ";");

        return index;
    }


    // -------------------------------------------------------------------------
    // Static suffix
    // -------------------------------------------------------------------------

    private void emitStaticSuffix(
            JavaWriter out,
            NameAllocator names,
            String result,
            PathSegment[] segments,
            int index,
            String expression,
            TypeMirror expressionType,
            String miss,
            boolean expressionKnownNonNull) {

        for (int i = index;
             i < segments.length;
             i++) {

            Access access =
                    access(
                            expressionType,
                            expression,
                            segments[i]);

            if (access == null) {
                throw new AssertionError(
                        "Unresolved static find suffix");
            }

            boolean last =
                    i ==
                            segments.length - 1;

            if (!expressionKnownNonNull &&
                    access.needsReceiverNonNull) {

                out.line(
                        "if (" +
                                expression +
                                " == null) " +
                                miss +
                                ";");
            }

            if (access.boundsCheck != null) {
                out.line(
                        "if (!(" +
                                access.boundsCheck +
                                ")) " +
                                miss +
                                ";");
            }

            if (access.presentCheck != null) {
                out.line(
                        "if (!(" +
                                access.presentCheck +
                                ")) " +
                                miss +
                                ";");
            }

            String value =
                    names.newName("value");

            out.line(
                    localType(access.type) +
                            " " +
                            value +
                            " = " +
                            access.expression +
                            ";");

            if (!last &&
                    !access.type
                            .getKind()
                            .isPrimitive()) {

                out.line(
                        "if (" +
                                value +
                                " == null) " +
                                miss +
                                ";");
            }

            expression =
                    value;

            expressionType =
                    access.type;

            expressionKnownNonNull =
                    !last;
        }

        out.line(
                result +
                        ".add(" +
                        expression +
                        ");");
    }


    // -------------------------------------------------------------------------
    // Static access resolution
    // -------------------------------------------------------------------------

    private TypeMirror resolveDirectType(
            TypeMirror owner,
            PathSegment segment) {

        NodeAccess access =
                resolveAccess(
                        owner,
                        segment);

        return access == null ||
                !access.readable()
                ? null
                : access.readType();
    }


    private Access access(
            TypeMirror owner,
            String receiver,
            PathSegment segment) {

        NodeAccess resolved =
                resolveAccess(
                        owner,
                        segment);

        if (resolved == null ||
                !resolved.readable()) {

            return null;
        }

        TypeMirror valueType =
                resolved.readType();

        if (segment instanceof PathSegment.Name) {
            String name =
                    ((PathSegment.Name) segment)
                            .name;

            String key =
                    JavaWriter.stringLiteral(
                            name);

            switch (resolved.kind()) {
                case PROPERTY: {
                    PropertyAccess read =
                            resolved.property()
                                    .read();

                    String expression =
                            read.isMethod()
                                    ? receiver +
                                    "." +
                                    read.memberName() +
                                    "()"
                                    : receiver +
                                    "." +
                                    read.memberName();

                    return new Access(
                            valueType,
                            expression,
                            true,
                            null,
                            null);
                }

                case MAP:
                    return new Access(
                            valueType,
                            receiver +
                                    ".get(" +
                                    key +
                                    ")",
                            true,
                            null,
                            receiver +
                                    ".containsKey(" +
                                    key +
                                    ")");

                case JSON_OBJECT:
                    return new Access(
                            valueType,
                            objectExpression(
                                    valueType,
                                    receiver +
                                            ".getNode(" +
                                            key +
                                            ")"),
                            true,
                            null,
                            receiver +
                                    ".containsKey(" +
                                    key +
                                    ")");

                default:
                    /*
                     * DYNAMIC / EXTERNAL accesses depend on runtime node shape
                     * and are intentionally not treated as directly compiled
                     * find accesses.
                     */
                    return null;
            }
        }

        if (segment instanceof PathSegment.Index) {
            int index =
                    ((PathSegment.Index) segment)
                            .index;

            switch (resolved.kind()) {
                case ARRAY: {
                    String size =
                            receiver +
                                    ".length";

                    String normalized =
                            staticIndex(
                                    index,
                                    size);

                    return new Access(
                            valueType,
                            receiver +
                                    "[" +
                                    normalized +
                                    "]",
                            true,
                            normalized +
                                    " >= 0 && " +
                                    normalized +
                                    " < " +
                                    size,
                            null);
                }

                case LIST: {
                    String size =
                            receiver +
                                    ".size()";

                    String normalized =
                            staticIndex(
                                    index,
                                    size);

                    return new Access(
                            valueType,
                            receiver +
                                    ".get(" +
                                    normalized +
                                    ")",
                            true,
                            normalized +
                                    " >= 0 && " +
                                    normalized +
                                    " < " +
                                    size,
                            null);
                }

                case JSON_ARRAY: {
                    String size =
                            receiver +
                                    ".size()";

                    String normalized =
                            staticIndex(
                                    index,
                                    size);

                    return new Access(
                            valueType,
                            objectExpression(
                                    valueType,
                                    receiver +
                                            ".getNode(" +
                                            normalized +
                                            ")"),
                            true,
                            normalized +
                                    " >= 0 && " +
                                    normalized +
                                    " < " +
                                    size,
                            null);
                }

                default:
                    return null;
            }
        }

        return null;
    }


    private NodeAccess resolveAccess(
            TypeMirror owner,
            PathSegment segment) {

        if (segment instanceof PathSegment.Name) {
            return context.access.resolveName(
                    owner,
                    ((PathSegment.Name) segment)
                            .name);
        }

        if (segment instanceof PathSegment.Index) {
            return context.access.resolveIndex(
                    owner);
        }

        return null;
    }


    private boolean canEmitAccesses(
            TypeMirror start,
            PathSegment[] segments,
            int from,
            int to) {

        TypeMirror current =
                start;

        for (int i = from;
             i < to;
             i++) {

            Access access =
                    access(
                            current,
                            "value",
                            segments[i]);

            if (access == null) {
                return false;
            }

            current =
                    access.type;
        }

        return true;
    }


    // -------------------------------------------------------------------------
    // Container type helpers
    // -------------------------------------------------------------------------

    private boolean isSequence(
            TypeMirror type) {

        if (type == null) {
            return false;
        }

        if (type.getKind() ==
                TypeKind.ARRAY) {

            return true;
        }

        NodeKind kind =
                types.nodeKind(type);

        return kind == NodeKind.ARRAY_LIST
                || kind == NodeKind.ARRAY_JSON_ARRAY
                || kind == NodeKind.ARRAY_JAJO;
    }


    private boolean isJsonArray(
            TypeMirror type) {

        NodeKind kind =
                types.nodeKind(type);

        return kind == NodeKind.ARRAY_JSON_ARRAY
                || kind == NodeKind.ARRAY_JAJO;
    }


    private boolean isMap(
            TypeMirror type) {

        return types.nodeKind(type) ==
                NodeKind.OBJECT_MAP;
    }


    private TypeMirror sequenceElementType(
            TypeMirror type) {

        type =
                types.concrete(type);

        if (type.getKind() ==
                TypeKind.ARRAY) {

            return types.concrete(
                    ((ArrayType) type)
                            .getComponentType());
        }

        NodeKind kind =
                types.nodeKind(type);

        if (kind == NodeKind.ARRAY_LIST) {
            return types.listReadElementType(
                    type);
        }

        if (kind == NodeKind.ARRAY_JSON_ARRAY ||
                kind == NodeKind.ARRAY_JAJO) {

            return types.objectType();
        }

        return null;
    }


    private boolean isStringKeyMap(
            TypeMirror type) {

        if (!isMap(type)) {
            return false;
        }

        return types.hasSafeStringPathKey(type);
    }


    private boolean validateMapPathKeys(
            ExecutableElement method,
            GeneratedClass generated,
            TypeMirror start,
            PathSegment[] segments) {

        TypeMirror current = start;

        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];

            if (segment instanceof PathSegment.Name) {
                NodeAccess access = resolveAccess(current, segment);

                if (access != null &&
                        access.kind() == NodeAccess.Kind.MAP &&
                        !types.hasSafeStringPathKey(current)) {

                    error(
                            method,
                            generated,
                            "@FindByPath cannot use a String path key with Map key type " +
                                    types.mapDeclaredKeyType(current));
                    return false;
                }

                current = access == null || !access.readable()
                        ? null
                        : access.readType();
            } else if (segment instanceof PathSegment.Union) {
                PathSegment.Union union =
                        (PathSegment.Union) segment;

                if (isMap(current) &&
                        containsName(union) &&
                        !types.hasSafeStringPathKey(current)) {

                    error(
                            method,
                            generated,
                            "@FindByPath cannot use a String path key with Map key type " +
                                    types.mapDeclaredKeyType(current));
                    return false;
                }

                current = isMap(current)
                        ? types.mapReadValueType(current)
                        : isIndexOrSliceUnion(union)
                        ? sequenceElementType(current)
                        : null;
            } else if (segment instanceof PathSegment.Wildcard ||
                    segment instanceof PathSegment.Filter) {

                current = isMap(current)
                        ? types.mapReadValueType(current)
                        : sequenceElementType(current);
            } else if (segment instanceof PathSegment.Slice) {
                current = sequenceElementType(current);
            } else if (segment instanceof PathSegment.Index) {
                current = resolveDirectType(current, segment);
            }

            if (current == null) {
                return true;
            }
        }

        return true;
    }


    private boolean containsName(PathSegment.Union union) {
        for (PathSegment segment : union.union) {
            if (segment instanceof PathSegment.Name) {
                return true;
            }
        }

        return false;
    }


    private boolean isIndexOrSliceUnion(PathSegment.Union union) {
        for (PathSegment segment : union.union) {
            if (!(segment instanceof PathSegment.Index) &&
                    !(segment instanceof PathSegment.Slice)) {

                return false;
            }
        }

        return true;
    }


    // -------------------------------------------------------------------------
    // Expressions
    // -------------------------------------------------------------------------

    private String sequenceReadExpression(
            TypeMirror containerType,
            String receiver,
            String index,
            TypeMirror valueType) {

        if (containerType.getKind() ==
                TypeKind.ARRAY) {

            return receiver +
                    "[" +
                    index +
                    "]";
        }

        if (isJsonArray(containerType)) {
            return objectExpression(
                    valueType,
                    receiver +
                            ".getNode(" +
                            index +
                            ")");
        }

        return receiver +
                ".get(" +
                index +
                ")";
    }


    private String objectExpression(
            TypeMirror type,
            String expression) {

        if (types.isObject(type)) {
            return expression;
        }

        return "(" +
                localType(type) +
                ") " +
                expression;
    }


    private static String staticIndex(
            int index,
            String sizeExpression) {

        if (index >= 0) {
            return Integer.toString(
                    index);
        }

        if (index == Integer.MIN_VALUE) {
            return sizeExpression +
                    " + " +
                    index;
        }

        return sizeExpression +
                " - " +
                (-index);
    }


    // -------------------------------------------------------------------------
    // Method/type helpers
    // -------------------------------------------------------------------------

    private String methodHeader(
            ExecutableElement method,
            VariableElement root) {

        return "public " +
                method.getReturnType() +
                " " +
                method.getSimpleName() +
                "(" +
                root.asType() +
                " " +
                root.getSimpleName() +
                ")";
    }


    private NameAllocator names(
            VariableElement root) {

        NameAllocator names =
                new NameAllocator();

        names.reserve(
                root.getSimpleName()
                        .toString());

        return names;
    }


    private String localType(
            TypeMirror type) {

        return types.boxed(
                        types.concrete(type))
                .toString();
    }


    private boolean canAddToResult(
            TypeMirror valueType,
            TypeMirror elementType) {

        if (types.isObject(
                elementType)) {

            return true;
        }

        return types.isAssignableBoxedGeneric(
                valueType,
                elementType);
    }


    // -------------------------------------------------------------------------
    // Diagnostic
    // -------------------------------------------------------------------------

    private void error(
            Element element,
            GeneratedClass generated,
            String message) {

        generated.invalidate();

        context.error(
                element,
                generated.originName() +
                        ": " +
                        message);
    }


    // -------------------------------------------------------------------------
    // Resolved access
    // -------------------------------------------------------------------------

    private static final class Access {

        final TypeMirror type;
        final String expression;

        final boolean needsReceiverNonNull;

        final String boundsCheck;
        final String presentCheck;


        Access(
                TypeMirror type,
                String expression,
                boolean needsReceiverNonNull,
                String boundsCheck,
                String presentCheck) {

            this.type = type;
            this.expression = expression;
            this.needsReceiverNonNull =
                    needsReceiverNonNull;
            this.boundsCheck = boundsCheck;
            this.presentCheck = presentCheck;
        }
    }
}
