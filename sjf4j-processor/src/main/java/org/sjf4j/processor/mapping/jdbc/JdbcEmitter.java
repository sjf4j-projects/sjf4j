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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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


    /*
     * --------------------------------------------------------------
     * Emit
     * --------------------------------------------------------------
     */

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

        out.line(
                "java.util.Objects.requireNonNull(" +
                        resultSet +
                        ", " +
                        stringLiteral(resultSet) +
                        ");");

        out.line("try {");
        out.indent();

        String metadata = null;
        String columns = null;
        String columnCount = null;
        String labels = null;
        String consumed = null;

        Map<String, String> indexes =
                Collections.emptyMap();

        /*
         * Map rows always require ResultSetMetaData because their keys come
         * directly from runtime column labels.
         *
         * PRESENT_ONLY and JOJO remainder mapping also need metadata once per
         * mapper invocation.
         */
        if (method.kind() ==
                JdbcCompiler.CompiledMethod.Kind.MAP ||
                method.presentOnly() ||
                method.jojo()) {

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

        if (method.jojo()) {

            columnCount =
                    names.newName(
                            "columnCount");

            labels =
                    names.newName(
                            "labels");

            String index =
                    names.newName(
                            "column");

            out.line(
                    "int " +
                            columnCount +
                            " = " +
                            metadata +
                            ".getColumnCount();");

            out.line(
                    "java.lang.String[] " +
                            labels +
                            " = new java.lang.String[" +
                            columnCount +
                            " + 1];");

            out.line(
                    "for (int " +
                            index +
                            " = 1; " +
                            index +
                            " <= " +
                            columnCount +
                            "; " +
                            index +
                            "++) {");

            out.indent();

            out.line(
                    labels +
                            "[" +
                            index +
                            "] = " +
                            metadata +
                            ".getColumnLabel(" +
                            index +
                            ");");

            out.dedent();
            out.line("}");
        }

        if (plan.list() || method.jojo()) {
            indexes =
                    emitColumnIndexes(
                            out,
                            names,
                            resultSet,
                            columns,
                            columnCount,
                            labels,
                            method.jojo()
                                    ? method.consumedColumns()
                                    : method.readColumns());
        }

        if (method.jojo()) {
            consumed =
                    emitConsumedColumns(
                            out,
                            names,
                            columnCount,
                            indexes,
                            method.consumedColumns());
        }

        if (plan.currentRow()) {

            String row =
                    emitRow(
                            out,
                            names,
                             method,
                             resultSet,
                             metadata,
                            columnCount,
                            columns,
                            indexes,
                            columnCount,
                            labels,
                            consumed);

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
                    columnCount,
                    columns,
                    indexes,
                    columnCount,
                    labels,
                    consumed);

        } else {

            emitSingle(
                    out,
                    names,
                     method,
                     resultSet,
                     metadata,
                    columnCount,
                    columns,
                    indexes,
                    columnCount,
                    labels,
                    consumed);
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


    /*
     * --------------------------------------------------------------
     * Method Shell
     * --------------------------------------------------------------
     */

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


    /*
     * --------------------------------------------------------------
     * Cursor
     * --------------------------------------------------------------
     */

    private void emitSingle(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String metadata,
            String mapColumnCount,
            String columns,
            Map<String, String> indexes,
            String columnCount,
            String labels,
            String consumed) {

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
                        mapColumnCount,
                        columns,
                        indexes,
                        columnCount,
                        labels,
                        consumed);

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
            String mapColumnCount,
            String columns,
            Map<String, String> indexes,
            String columnCount,
            String labels,
            String consumed) {

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

        if (method.kind() ==
                JdbcCompiler.CompiledMethod.Kind.MAP) {

            out.line(
                    "if (!" +
                            resultSet +
                            ".next()) {");

            out.indent();
            out.line(
                    "return " +
                            result +
                            ";");
            out.dedent();
            out.line("}");

            String count =
                    names.newName(
                            "columnCount");

            out.line(
                    "int " +
                            count +
                            " = " +
                            metadata +
                            ".getColumnCount();");

            out.line("do {");
            out.indent();

            String row =
                    emitRow(
                            out,
                            names,
                            method,
                            resultSet,
                            metadata,
                            count,
                            columns,
                            indexes,
                            columnCount,
                            labels,
                            consumed);

            out.line(
                    result +
                            ".add(" +
                            row +
                            ");");

            out.dedent();
            out.line(
                    "} while (" +
                            resultSet +
                            ".next());");

            out.line(
                    "return " +
                            result +
                            ";");

            return;
        }

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
                        mapColumnCount,
                        columns,
                        indexes,
                        columnCount,
                        labels,
                        consumed);

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


    /*
     * --------------------------------------------------------------
     * Row
     * --------------------------------------------------------------
     */

    private String emitRow(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String metadata,
            String mapColumnCount,
            String columns,
            Map<String, String> indexes,
            String columnCount,
            String labels,
            String consumed) {

        if (method.kind() ==
                JdbcCompiler.CompiledMethod.Kind.MAP) {

            return emitMapRow(
                    out,
                     names,
                     resultSet,
                    metadata,
                    mapColumnCount);
        }

        return emitObjectRow(
                out,
                names,
                method,
                resultSet,
                columns,
                indexes,
                columnCount,
                labels,
                consumed);
    }


    /*
     * --------------------------------------------------------------
     * Map Row
     * --------------------------------------------------------------
     */

    private String emitMapRow(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            String metadata,
            String columnCount) {

        if (columnCount == null) {
            columnCount =
                    names.newName(
                            "columnCount");

            out.line(
                    "int " +
                            columnCount +
                            " = " +
                            metadata +
                            ".getColumnCount();");
        }

        String result =
                names.newName(
                        "row");

        String index =
                names.newName(
                        "column");

        String label =
                names.newName(
                        "label");

        out.line(
                "java.util.Map<String, Object> " +
                        result +
                        " = new java.util.LinkedHashMap<String, Object>(" +
                        columnCount +
                        ");");

        out.line(
                "for (int " +
                        index +
                        " = 1; " +
                        index +
                        " <= " +
                        columnCount +
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


    /*
     * --------------------------------------------------------------
     * Object Row
     * --------------------------------------------------------------
     */

    private String emitObjectRow(
            JavaWriter out,
            NameAllocator names,
            JdbcCompiler.CompiledMethod method,
            String resultSet,
            String columns,
            Map<String, String> indexes,
            String columnCount,
            String labels,
            String consumed) {

        CreatorResolver.Creation creation =
                method.creation();

        List<String> constructorValues =
                new ArrayList<String>(
                        method.constructorArguments()
                                .size());

        for (JdbcCompiler.ConstructorArgument argument :
                method.constructorArguments()) {

            constructorValues.add(
                    emitValue(
                            out,
                            names,
                            resultSet,
                            argument.value(),
                            indexes));
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
                                presentColumns(
                                        columns,
                                        assignment.value(),
                                        indexes) +
                                ") {");

                out.indent();
            }

            String value =
                    emitValue(
                            out,
                            names,
                            resultSet,
                            assignment.value(),
                            indexes);

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

        if (method.jojo()) {
            emitDynamicColumns(
                    out,
                    names,
                    target,
                    resultSet,
                    columnCount,
                    labels,
                    consumed);
        }

        return target;
    }


    /*
     * --------------------------------------------------------------
     * PRESENT_ONLY
     * --------------------------------------------------------------
     */

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


    private Map<String, String> emitColumnIndexes(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            String columns,
            String columnCount,
            String labels,
            List<String> sourceColumns) {

        Map<String, String> result =
                new LinkedHashMap<String, String>();

        if (labels != null) {
            for (String source :
                    sourceColumns) {
                String index =
                        names.newName(
                                "index_" +
                                        source);

                out.line(
                        "int " +
                                index +
                                " = 0;");

                result.put(
                        source,
                        index);
            }

            String column =
                    names.newName(
                            "column");

            out.line(
                    "for (int " +
                            column +
                            " = 1; " +
                            column +
                            " <= " +
                            columnCount +
                            "; " +
                            column +
                            "++) {");

            out.indent();

            for (String source :
                    sourceColumns) {
                String index =
                        result.get(source);

                out.line(
                        "if (" +
                                index +
                                " == 0 && " +
                                stringLiteral(source) +
                                ".equalsIgnoreCase(" +
                                labels +
                                "[" +
                                column +
                                "])) { " +
                                index +
                                " = " +
                                column +
                                "; }");
            }

            out.dedent();
            out.line("}");

            if (columns == null) {
                for (String source :
                        sourceColumns) {
                    String index =
                            result.get(source);

                    out.line(
                            "if (" +
                                    index +
                                    " == 0) { " +
                                    index +
                                    " = " +
                                    resultSet +
                                    ".findColumn(" +
                                    stringLiteral(source) +
                                    "); }");
                }
            }

            return result;
        }

        for (String source :
                sourceColumns) {

            String index =
                    names.newName(
                            "index_" +
                                    source);

            if (columns == null) {
                out.line(
                        "int " +
                                index +
                                " = " +
                                resultSet +
                                ".findColumn(" +
                                stringLiteral(source) +
                                ");");
            } else {
                out.line(
                        "int " +
                                index +
                                " = " +
                                columns +
                                ".contains(" +
                                stringLiteral(source) +
                                ") ? " +
                                resultSet +
                                ".findColumn(" +
                                stringLiteral(source) +
                                ") : 0;");
            }

            result.put(
                    source,
                    index);
        }

        return result;
    }


    private String emitConsumedColumns(
            JavaWriter out,
            NameAllocator names,
            String columnCount,
            Map<String, String> indexes,
            List<String> consumedColumns) {

        String consumed =
                names.newName(
                        "consumed");

        out.line(
                "boolean[] " +
                        consumed +
                        " = new boolean[" +
                        columnCount +
                        " + 1];");

        for (String column :
                consumedColumns) {

            String index =
                    indexes.get(column);

            out.line(
                    "if (" +
                            index +
                            " != 0) { " +
                            consumed +
                            "[" +
                            index +
                            "] = true; }");
        }

        return consumed;
    }


    private void emitDynamicColumns(
            JavaWriter out,
            NameAllocator names,
            String target,
            String resultSet,
            String columnCount,
            String labels,
            String consumed) {

        String dynamic =
                names.newName(
                        "dynamic");

        String column =
                names.newName(
                        "column");

        out.line(
                "java.util.Map<String, Object> " +
                        dynamic +
                        " = " +
                        "org.sjf4j.InternalAccess.dynamicProperties(" +
                        target +
                        ");");

        out.line(
                "for (int " +
                        column +
                        " = 1; " +
                        column +
                        " <= " +
                        columnCount +
                        "; " +
                        column +
                        "++) {");

        out.indent();

        out.line(
                "if (!" +
                        consumed +
                        "[" +
                        column +
                        "]) {");

        out.indent();

        out.line(
                "if (" +
                        dynamic +
                        " == null) {");

        out.indent();

        out.line(
                dynamic +
                        " = new java.util.LinkedHashMap<String, Object>();");

        out.line(
                "org.sjf4j.InternalAccess.dynamicProperties(" +
                        target +
                        ", " +
                        dynamic +
                        ");");

        out.dedent();
        out.line("}");

        out.line(
                dynamic +
                        ".put(" +
                        labels +
                        "[" +
                        column +
                        "], " +
                        resultSet +
                        ".getObject(" +
                        column +
                        "));" );

        out.dedent();
        out.line("}");

        out.dedent();
        out.line("}");
    }


    /*
     * --------------------------------------------------------------
     * Column Read
     * --------------------------------------------------------------
     */

    private String emitValue(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.Value value,
            Map<String, String> indexes) {

        if (value.read() != null) {
            return emitColumnRead(
                    out,
                    names,
                    resultSet,
                    value.read(),
                    indexes);
        }

        JdbcCompiler.ComputedValue compute =
                value.compute();

        List<String> arguments =
                new ArrayList<String>(
                        compute.reads()
                                .size());

        for (JdbcCompiler.ColumnRead read :
                compute.reads()) {
            arguments.add(
                    emitColumnRead(
                            out,
                            names,
                            resultSet,
                            read,
                            indexes));
        }

        String result =
                names.newName("computed");

        out.line(
                localType(
                        compute.targetType()) +
                        " " +
                        result +
                        " = this." +
                        compute.helper() +
                        "(" +
                        join(arguments) +
                        ");");

        return result;
    }


    private String presentColumns(
            String columns,
            JdbcCompiler.Value value,
            Map<String, String> indexes) {

        List<JdbcCompiler.ColumnRead> reads =
                value.read() != null
                        ? Collections.singletonList(
                                value.read())
                        : value.compute()
                                .reads();

        StringBuilder out =
                new StringBuilder();

        for (JdbcCompiler.ColumnRead read : reads) {
            if (out.length() != 0) {
                out.append(" && ");
            }

            String index =
                    indexes.get(
                            read.column());

            if (index != null) {
                out.append(index)
                        .append(" != 0");
            } else {
                out.append(columns)
                        .append(".contains(")
                        .append(stringLiteral(
                                read.column()))
                        .append(")");
            }
        }

        return out.length() == 0
                ? "true"
                : out.toString();
    }

    private String emitColumnRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            Map<String, String> indexes) {

        String column =
                indexes.get(
                        read.column());

        if (column == null) {
            column = stringLiteral(
                    read.column());
        }

        switch (read.kind()) {
            case STRING:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getString");

            case BOOLEAN:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getBoolean",
                        "boolean",
                        "java.lang.Boolean");

            case BYTE:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getByte",
                        "byte",
                        "java.lang.Byte");

            case SHORT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getShort",
                        "short",
                        "java.lang.Short");

            case INT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getInt",
                        "int",
                        "java.lang.Integer");

            case LONG:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getLong",
                        "long",
                        "java.lang.Long");

            case FLOAT:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getFloat",
                        "float",
                        "java.lang.Float");

            case DOUBLE:
                return emitPrimitiveRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getDouble",
                        "double",
                        "java.lang.Double");

            case CHARACTER:
                return emitCharacterRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case BIG_DECIMAL:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getBigDecimal");

            case BYTES:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getBytes");

            case DATE:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getDate");

            case TIME:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getTime");

            case TIMESTAMP:
                return emitReferenceRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column,
                        "getTimestamp");

            case ENUM:
                return emitEnumRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case OBJECT:
                return emitObjectRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case TYPED_OBJECT:
                return emitTypedObjectRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

            case NODE_VALUE:
                return emitNodeValueRead(
                        out,
                        names,
                        resultSet,
                        read,
                        column);

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
            String column,
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
                        column +
                        ");");

        return value;
    }


    private String emitPrimitiveRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column,
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
                        column +
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
            JdbcCompiler.ColumnRead read,
            String column) {

        String raw =
                names.newName("raw_" + read.column());

        out.line(
                "java.lang.String " +
                        raw +
                        " = " +
                        resultSet +
                        ".getString(" +
                        column +
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
            JdbcCompiler.ColumnRead read,
            String column) {

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
                        column +
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
            JdbcCompiler.ColumnRead read,
            String column) {

        String value =
                names.newName("value_" + read.column());

        out.line(
                "java.lang.Object " +
                        value +
                        " = " +
                        resultSet +
                        ".getObject(" +
                        column +
                        ");");

        return value;
    }


    private String emitTypedObjectRead(
            JavaWriter out,
            NameAllocator names,
            String resultSet,
            JdbcCompiler.ColumnRead read,
            String column) {

        String value =
                names.newName("value_" + read.column());

        out.line(
                read.targetType() +
                        " " +
                        value +
                        " = " +
                        resultSet +
                        ".getObject(" +
                        column +
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
            JdbcCompiler.ColumnRead read,
            String column) {

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
                        column +
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


    /*
     * --------------------------------------------------------------
     * Target Write
     * --------------------------------------------------------------
     */

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


    /*
     * --------------------------------------------------------------
     * Creator
     * --------------------------------------------------------------
     */

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


    /*
     * --------------------------------------------------------------
     * Type / Path Helpers
     * --------------------------------------------------------------
     */

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
