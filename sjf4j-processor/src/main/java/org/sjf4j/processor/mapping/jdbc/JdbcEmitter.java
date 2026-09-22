package org.sjf4j.processor.mapping.jdbc;

import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.mapping.CreatorResolver;
import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;


/**
 * Emits direct ResultSet mapping code from compiled JDBC mapping plans.
 *
 * <p>All semantic resolution must be completed by {@link JdbcCompiler}
 * before this emitter is invoked.</p>
 */
public final class JdbcEmitter {

    private final ProcessorContext context;
    private final TypeSystem types;


    public JdbcEmitter(
            ProcessorContext context) {

        this.context = context;
        this.types = context.types;
    }


    // -------------------------------------------------------------------------
    // Emit
    // -------------------------------------------------------------------------

    public void emit(
            JdbcCompiler.CompiledMethod method,
            GeneratedClass generated) {

        generated.addMethod(out ->
                emitMethod(
                        out,
                        method));
    }


    private void emitMethod(
            JavaWriter out,
            JdbcCompiler.CompiledMethod method) {

        JdbcPlan plan =
                method.plan();

        NameAllocator names =
                new NameAllocator();

        for (VariableElement parameter :
                plan.method()
                        .getParameters()) {

            names.reserve(
                    parameter.getSimpleName()
                            .toString());
        }

        String resultSet =
                plan.resultSetParameter()
                        .getSimpleName()
                        .toString();

        out.line("");
        out.line("@Override");
        out.line(
                methodHeader(plan) +
                        " {");

        out.indent();

        out.line("try {");
        out.indent();

        String metadata = null;
        String columns = null;

        /*
         * Map rows always require ResultSetMetaData because their keys come
         * directly from runtime column labels.
         *
         * PRESENT_ONLY also needs metadata once per mapper invocation.
         */
        if (method.kind() ==
                JdbcCompiler.CompiledMethod.Kind.MAP ||
                method.presentOnly()) {

            metadata =
                    names.newName(
                            "metadata");

            out.line(
                    "java.sql.ResultSetMetaData " +
                            metadata +
                            " = " +
                            resultSet +
                            ".getMetaData();");
        }

        if (method.presentOnly()) {

            columns =
                    emitColumnSet(
                            out,
                            names,
                            metadata);
        }

        if (plan.currentRow()) {

            String row =
                    emitRow(
                            out,
                            names,
                            method,
                            resultSet,
                            metadata,
                            columns);

            out.line(
                    "return " +
                            row +
                            ";");

        } else if (plan.list()) {

            emitList(
                    out,
                    names,
                    method,
                    resultSet,
                    metadata,
                    columns);

        } else {

            emitSingle(
                    out,
                    names,
                    method,
                    resultSet,
                    metadata,
                    columns);
        }

        out.dedent();
        out.line(
                "} catch (java.sql.SQLException e) {");

        out.indent();

        out.line(
                "throw new org.sjf4j.exception.BindingException(" +
                        stringLiteral(
                                "failed to map JDBC ResultSet in " +
                                        plan.method()
                                                .getSimpleName()) +
                        ", e);");

        out.dedent();
        out.line("}");

        out.dedent();
        out.line("}");
    }


    // -------------------------------------------------------------------------
    // Method shell
    // -------------------------------------------------------------------------

    private String methodHeader(
            JdbcPlan plan) {

        ExecutableElement method =
                plan.method();

        ExecutableType methodType =
                plan.methodType();

        StringBuilder out =
                new StringBuilder();

        out.append("public ")
                .append(
                        methodType.getReturnType())
                .append(' ')
                .append(
                        method.getSimpleName())
                .append('(');

        List<? extends VariableElement> parameters =
                method.getParameters();

        List<? extends TypeMirror> parameterTypes =
                methodType.getParameterTypes();

        for (int i = 0;
             i < parameters.size();
             i++) {

            if (i != 0) {
                out.append(", ");
            }

            out.append(
                            parameterTypes.get(i))
                    .append(' ')
                    .append(
                            parameters.get(i)
                                    .getSimpleName());
        }

        out.append(')');

        return out.toString();
    }


    // -------------------------------------------------------------------------
    // Cursor
    // -------------------------------------------------------------------------

    private void emitSingle(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String metadata,
            String columns) {

        out.line(
                "if (!" +
                        resultSet +
                        ".next()) {");

        out.indent();
        out.line("return null;");
        out.dedent();

        out.line("}");

        String row =
                emitRow(
                        out,
                        names,
                        method,
                        resultSet,
                        metadata,
                        columns);

        if (!method.firstResult()) {

            out.line(
                    "if (" +
                            resultSet +
                            ".next()) {");

            out.indent();

            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            stringLiteral(
                                    "JDBC mapper expected a single row but ResultSet contains multiple rows") +
                            ");");

            out.dedent();
            out.line("}");
        }

        out.line(
                "return " +
                        row +
                        ";");
    }


    private void emitList(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String metadata,
            String columns) {

        String result =
                names.newName(
                        "result");

        out.line(
                method.plan()
                        .returnType() +
                        " " +
                        result +
                        " = new java.util.ArrayList<" +
                        method.plan()
                                .rowType() +
                        ">();");

        out.line(
                "while (" +
                        resultSet +
                        ".next()) {");

        out.indent();

        String row =
                emitRow(
                        out,
                        names,
                        method,
                        resultSet,
                        metadata,
                        columns);

        out.line(
                result +
                        ".add(" +
                        row +
                        ");");

        out.dedent();
        out.line("}");

        out.line(
                "return " +
                        result +
                        ";");
    }


    // -------------------------------------------------------------------------
    // Row
    // -------------------------------------------------------------------------

    private String emitRow(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String metadata,
            String columns) {

        if (method.kind() ==
                JdbcCompiler.CompiledMethod.Kind.MAP) {

            return emitMapRow(
                    out,
                    names,
                    resultSet,
                    metadata);
        }

        return emitObjectRow(
                out,
                names,
                method,
                resultSet,
                columns);
    }


    // -------------------------------------------------------------------------
    // Map row
    // -------------------------------------------------------------------------

    private String emitMapRow(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            String metadata) {

        String result =
                names.newName(
                        "row");

        String count =
                names.newName(
                        "columnCount");

        String index =
                names.newName(
                        "column");

        String label =
                names.newName(
                        "label");

        out.line(
                "int " +
                        count +
                        " = " +
                        metadata +
                        ".getColumnCount();");

        out.line(
                "java.util.Map<String, Object> " +
                        result +
                        " = new java.util.LinkedHashMap<String, Object>(" +
                        count +
                        ");");

        out.line(
                "for (int " +
                        index +
                        " = 1; " +
                        index +
                        " <= " +
                        count +
                        "; " +
                        index +
                        "++) {");

        out.indent();

        out.line(
                "String " +
                        label +
                        " = " +
                        metadata +
                        ".getColumnLabel(" +
                        index +
                        ");");

        out.line(
                result +
                        ".put(" +
                        label +
                        ", " +
                        resultSet +
                        ".getObject(" +
                        index +
                        "));");

        out.dedent();
        out.line("}");

        return result;
    }


    // -------------------------------------------------------------------------
    // Object row
    // -------------------------------------------------------------------------

    private String emitObjectRow(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String columns) {

        CreatorResolver.Creation creation =
                method.creation();

        List<String> constructorValues =
                new ArrayList<String>(
                        method.constructorArguments()
                                .size());

        for (JdbcCompiler.ConstructorArgument argument :
                method.constructorArguments()) {

            constructorValues.add(
                    emitColumnRead(
                            out,
                            names,
                            resultSet,
                            argument.read()));
        }

        String target =
                names.newName(
                        "row");

        switch (creation.kind()) {
            case NO_ARGS:
                out.line(
                        method.targetType() +
                                " " +
                                target +
                                " = new " +
                                method.targetType() +
                                "();");
                break;

            case CONSTRUCTOR:
                out.line(
                        method.targetType() +
                                " " +
                                target +
                                " = new " +
                                method.targetType() +
                                "(" +
                                join(
                                        constructorValues) +
                                ");");
                break;

            case FACTORY:
                out.line(
                        method.targetType() +
                                " " +
                                target +
                                " = " +
                                factoryExpression(
                                        creation) +
                                ";");
                break;

            default:
                throw new AssertionError(
                        creation.kind());
        }

        for (JdbcCompiler.Assignment assignment :
                method.assignments()) {

            if (method.presentOnly()) {

                out.line(
                        "if (" +
                                columns +
                                ".contains(" +
                                stringLiteral(
                                        assignment.read()
                                                .column()) +
                                ")) {");

                out.indent();
            }

            String value =
                    emitColumnRead(
                            out,
                            names,
                            resultSet,
                            assignment.read());

            emitTargetWrite(
                    out,
                    names,
                    target,
                    assignment.target(),
                    value);

            if (method.presentOnly()) {

                out.dedent();
                out.line("}");
            }
        }

        return target;
    }


    // -------------------------------------------------------------------------
    // PRESENT_ONLY
    // -------------------------------------------------------------------------

    private String emitColumnSet(
            JavaWriter out,
            NameAllocator names,
            String metadata) {

        String columns =
                names.newName(
                        "columns");

        String count =
                names.newName(
                        "columnCount");

        String index =
                names.newName(
                        "column");

        out.line(
                "java.util.Set<String> " +
                        columns +
                        " = new java.util.HashSet<String>();");

        out.line(
                "int " +
                        count +
                        " = " +
                        metadata +
                        ".getColumnCount();");

        out.line(
                "for (int " +
                        index +
                        " = 1; " +
                        index +
                        " <= " +
                        count +
                        "; " +
                        index +
                        "++) {");

        out.indent();

        out.line(
                columns +
                        ".add(" +
                        metadata +
                        ".getColumnLabel(" +
                        index +
                        "));");

        out.dedent();
        out.line("}");

        return columns;
    }


    // -------------------------------------------------------------------------
    // Column read
    // -------------------------------------------------------------------------

    private String emitColumnRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read) {

        switch (read.kind()) {
            case STRING:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getString");

            case BOOLEAN:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getBoolean",
                        "boolean",
                        "java.lang.Boolean");

            case BYTE:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getByte",
                        "byte",
                        "java.lang.Byte");

            case SHORT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getShort",
                        "short",
                        "java.lang.Short");

            case INT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getInt",
                        "int",
                        "java.lang.Integer");

            case LONG:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getLong",
                        "long",
                        "java.lang.Long");

            case FLOAT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getFloat",
                        "float",
                        "java.lang.Float");

            case DOUBLE:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getDouble",
                        "double",
                        "java.lang.Double");

            case CHARACTER:
                return emitCharacterRead(
                        out,
                        names,
                        resultSet,
                        read);

            case BIG_DECIMAL:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getBigDecimal");

            case BYTES:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getBytes");

            case DATE:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getDate");

            case TIME:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getTime");

            case TIMESTAMP:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        "getTimestamp");

            case ENUM:
                return emitEnumRead(
                        out,
                        names,
                        resultSet,
                        read);

            case OBJECT:
                return emitObjectRead(
                        out,
                        names,
                        resultSet,
                        read);

            case TYPED_OBJECT:
                return emitTypedObjectRead(
                        out,
                        names,
                        resultSet,
                        read);

            case NODE_VALUE:
                return emitNodeValueRead(
                        out,
                        names,
                        resultSet,
                        read);

            default:
                throw new AssertionError(
                        read.kind());
        }
    }


    private String emitReferenceRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String getter) {

        String value =
                names.newName("value_" + read.column());

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        resultSet +
                        "." +
                        getter +
                        "(" +
                        stringLiteral(
                                read.column()) +
                        ");");

        return value;
    }


    private String emitPrimitiveRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String getter,
            String primitiveType,
            String wrapperType) {

        String raw =
                names.newName("raw_" + read.column());

        out.line(
                primitiveType +
                        " " +
                        raw +
                        " = " +
                        resultSet +
                        "." +
                        getter +
                        "(" +
                        stringLiteral(
                                read.column()) +
                        ");");

        if (read.primitive()) {

            out.line(
                    "if (" +
                            resultSet +
                            ".wasNull()) {");

            out.indent();

            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            stringLiteral(
                                    "SQL NULL cannot be mapped to primitive target from column '" +
                                            read.column() +
                                            "'") +
                            ");");

            out.dedent();
            out.line("}");

            return raw;
        }

        String value =
                names.newName("value_" + read.column());

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        resultSet +
                        ".wasNull() ? null : " +
                        wrapperType +
                        ".valueOf(" +
                        raw +
                        ");");

        return value;
    }


    private String emitCharacterRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read) {

        String raw =
                names.newName("raw_" + read.column());

        out.line(
                "java.lang.String " +
                        raw +
                        " = " +
                        resultSet +
                        ".getString(" +
                        stringLiteral(
                                read.column()) +
                        ");");

        if (read.primitive()) {

            out.line(
                    "if (" +
                            raw +
                            " == null) {");

            out.indent();

            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            stringLiteral(
                                    "SQL NULL cannot be mapped to primitive char from column '" +
                                            read.column() +
                                            "'") +
                            ");");

            out.dedent();
            out.line("}");

            emitCharacterLengthCheck(
                    out,
                    raw,
                    read.column());

            String value =
                    names.newName("value_" + read.column());

            out.line(
                    "char " +
                            value +
                            " = " +
                            raw +
                            ".charAt(0);");

            return value;
        }

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.Character " +
                        value +
                        " = null;");

        out.line(
                "if (" +
                        raw +
                        " != null) {");

        out.indent();

        emitCharacterLengthCheck(
                out,
                raw,
                read.column());

        out.line(
                value +
                        " = java.lang.Character.valueOf(" +
                        raw +
                        ".charAt(0));");

        out.dedent();
        out.line("}");

        return value;
    }


    private void emitCharacterLengthCheck(
            JavaWriter out,
            String raw,
            String column) {

        out.line(
                "if (" +
                        raw +
                        ".length() != 1) {");

        out.indent();

        out.line(
                "throw new org.sjf4j.exception.BindingException(" +
                        stringLiteral(
                                "JDBC column '" +
                                        column +
                                        "' cannot be mapped to char because its string value does not contain exactly one character") +
                        ");");

        out.dedent();
        out.line("}");
    }


    private String emitEnumRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read) {

        String raw =
                names.newName("raw_" + read.column());

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.String " +
                        raw +
                        " = " +
                        resultSet +
                        ".getString(" +
                        stringLiteral(
                                read.column()) +
                        ");");

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        raw +
                        " == null ? null : " +
                        read.targetType() +
                        ".valueOf(" +
                        raw +
                        ");");

        return value;
    }


    private String emitObjectRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read) {

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.Object " +
                        value +
                        " = " +
                        resultSet +
                        ".getObject(" +
                        stringLiteral(
                                read.column()) +
                        ");");

        return value;
    }


    private String emitTypedObjectRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read) {

        String value =
                names.newName("value_" + read.column());

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        resultSet +
                        ".getObject(" +
                        stringLiteral(
                                read.column()) +
                        ", " +
                        classLiteral(
                                read.targetType()) +
                        ");");

        return value;
    }


    private String emitNodeValueRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read) {

        String raw =
                names.newName("raw_" + read.column());

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.Object " +
                        raw +
                        " = " +
                        resultSet +
                        ".getObject(" +
                        stringLiteral(
                                read.column()) +
                        ");");

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = (" +
                        read.targetType() +
                        ") org.sjf4j.Nodes.to(" +
                        raw +
                        ", " +
                        classLiteral(
                                read.targetType()) +
                        ");");

        return value;
    }


    // -------------------------------------------------------------------------
    // Target write
    // -------------------------------------------------------------------------

    private void emitTargetWrite(
            JavaWriter out,
            NameAllocator names,
            String root,
            JdbcCompiler.Target target,
            String value) {

        List<JdbcCompiler.TargetStep> steps =
                target.steps();

        String owner =
                root;

        for (int i = 0;
             i < steps.size() - 1;
             i++) {

            JdbcCompiler.TargetStep step =
                    steps.get(i);

            NodeAccess access =
                    step.access();

            String next =
                    names.newName(
                            "parent");

            out.line(
                    localType(
                            access.readType()) +
                            " " +
                            next +
                            " = " +
                            readExpression(
                                    owner,
                                    step) +
                            ";");

            out.line(
                    "if (" +
                            next +
                            " == null) {");

            out.indent();

            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            stringLiteral(
                                    "JDBC target path parent is null: " +
                                            target.expression()) +
                            ");");

            out.dedent();
            out.line("}");

            owner =
                    next;
        }

        writeValue(
                out,
                owner,
                steps.get(
                        steps.size() - 1),
                value);
    }


    private String readExpression(
            String owner,
            JdbcCompiler.TargetStep step) {

        NodeAccess access =
                step.access();

        PathSegment segment =
                step.segment();

        switch (access.kind()) {
            case PROPERTY: {
                PropertyAccess read =
                        access.property()
                                .read();

                if (read == null) {
                    throw new IllegalStateException(
                            "JDBC target path property is not readable");
                }

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
                                nameKey(segment) +
                                ")");

            case JSON_OBJECT:
                return cast(
                        access.readType(),
                        owner +
                                ".getNode(" +
                                nameKey(segment) +
                                ")");

            case LIST:
                return cast(
                        access.readType(),
                        owner +
                                ".get(" +
                                index(segment) +
                                ")");

            case ARRAY:
                return owner +
                        "[" +
                        index(segment) +
                        "]";

            case JSON_ARRAY:
                return cast(
                        access.readType(),
                        owner +
                                ".getNode(" +
                                index(segment) +
                                ")");

            default:
                throw new IllegalStateException(
                        "Unsupported JDBC target path read: " +
                                access.kind());
        }
    }


    private void writeValue(
            JavaWriter out,
            String owner,
            JdbcCompiler.TargetStep step,
            String value) {

        NodeAccess access =
                step.access();

        PathSegment segment =
                step.segment();

        switch (access.kind()) {
            case PROPERTY: {
                PropertyAccess write =
                        access.property()
                                .write();

                if (write == null) {
                    throw new IllegalStateException(
                            "JDBC target property is not writable");
                }

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
                out.line(
                        owner +
                                ".put(" +
                                nameKey(segment) +
                                ", " +
                                value +
                                ");");
                return;

            case JSON_OBJECT:
                out.line(
                        owner +
                                ".put(" +
                                nameKey(segment) +
                                ", " +
                                value +
                                ");");
                return;

            case LIST:
                out.line(
                        owner +
                                ".set(" +
                                index(segment) +
                                ", " +
                                value +
                                ");");
                return;

            case ARRAY:
                out.line(
                        owner +
                                "[" +
                                index(segment) +
                                "] = " +
                                value +
                                ";");
                return;

            case JSON_ARRAY:
                out.line(
                        owner +
                                ".set(" +
                                index(segment) +
                                ", " +
                                value +
                                ");");
                return;

            default:
                throw new IllegalStateException(
                        "Unsupported JDBC target path write: " +
                                access.kind());
        }
    }


    // -------------------------------------------------------------------------
    // Creator
    // -------------------------------------------------------------------------

    private String factoryExpression(
            CreatorResolver.Creation creation) {

        ExecutableElement factory =
                creation.factory();

        if (creation.factoryOwner() == null ||
                !factory.getModifiers()
                        .contains(
                                Modifier.STATIC)) {

            return "this." +
                    factory.getSimpleName() +
                    "()";
        }

        return creation.factoryOwner()
                .getQualifiedName()
                .toString() +
                "." +
                factory.getSimpleName() +
                "()";
    }


    // -------------------------------------------------------------------------
    // Type / path helpers
    // -------------------------------------------------------------------------

    private String classLiteral(
            TypeMirror type) {

        TypeMirror boxed =
                types.boxed(type);

        return context.typeUtils
                .erasure(boxed)
                .toString() +
                ".class";
    }


    private String localType(
            TypeMirror type) {

        return types.boxed(type)
                .toString();
    }


    private String cast(
            TypeMirror type,
            String expression) {

        if (type == null ||
                types.isObject(type)) {

            return expression;
        }

        return "(" +
                localType(type) +
                ") " +
                expression;
    }


    private String nameKey(
            PathSegment segment) {

        if (!(segment instanceof
                PathSegment.Name)) {

            throw new IllegalStateException(
                    "Expected name segment, got " +
                            segment);
        }

        return stringLiteral(
                ((PathSegment.Name) segment)
                        .name);
    }


    private int index(
            PathSegment segment) {

        if (!(segment instanceof
                PathSegment.Index)) {

            throw new IllegalStateException(
                    "Expected index segment, got " +
                            segment);
        }

        return ((PathSegment.Index) segment)
                .index;
    }


    private String join(
            List<String> values) {

        StringBuilder out =
                new StringBuilder();

        for (int i = 0;
             i < values.size();
             i++) {

            if (i != 0) {
                out.append(", ");
            }

            out.append(
                    values.get(i));
        }

        return out.toString();
    }


    /**
     * JDK-8-compatible Java string literal escaping.
     */
    private static String stringLiteral(
            String value) {

        StringBuilder out =
                new StringBuilder(
                        value.length() + 2);

        out.append('"');

        for (int i = 0;
             i < value.length();
             i++) {

            char c =
                    value.charAt(i);

            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;

                case '"':
                    out.append("\\\"");
                    break;

                case '\n':
                    out.append("\\n");
                    break;

                case '\r':
                    out.append("\\r");
                    break;

                case '\t':
                    out.append("\\t");
                    break;

                case '\b':
                    out.append("\\b");
                    break;

                case '\f':
                    out.append("\\f");
                    break;

                default:
                    if (c < 0x20) {
                        String hex =
                                Integer.toHexString(c);

                        out.append("\\u");

                        for (int j = hex.length();
                             j < 4;
                             j++) {

                            out.append('0');
                        }

                        out.append(hex);

                    } else {
                        out.append(c);
                    }
            }
        }

        out.append('"');

        return out.toString();
    }
}
