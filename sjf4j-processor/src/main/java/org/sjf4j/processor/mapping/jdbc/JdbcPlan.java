package org.sjf4j.processor.mapping.jdbc;

import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.util.Asserts;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * Analyzed JDBC mapper method.
 *
 * <p>The plan describes only the method shape and declared JDBC mapping rules.
 * Target creation, column reads and value conversion are compiled later by
 * {@link JdbcCompiler}.</p>
 */
public final class JdbcPlan {

    /**
     * Defines who owns ResultSet cursor movement.
     */
    public enum CursorMode {
        /**
         * The generated method consumes the ResultSet cursor.
         */
        CURSOR,

        /**
         * The ResultSet is already positioned on the row to map.
         */
        CURRENT_ROW
    }


    /**
     * Defines the result shape of the mapper method.
     */
    public enum ResultKind {
        SINGLE,
        LIST
    }


    private final ExecutableElement method;
    private final ExecutableType methodType;

    private final CursorMode cursorMode;
    private final ResultKind resultKind;

    private final VariableElement resultSetParameter;
    private final VariableElement rowNumberParameter;

    private final TypeMirror returnType;
    private final TypeMirror rowType;

    private final List<Rule> rules;


    JdbcPlan(
            ExecutableElement method,
            ExecutableType methodType,
            CursorMode cursorMode,
            ResultKind resultKind,
            VariableElement resultSetParameter,
            VariableElement rowNumberParameter,
            TypeMirror returnType,
            TypeMirror rowType,
            List<Rule> rules) {

        this.method =
                Asserts.notNull(method, "method");

        this.methodType =
                Asserts.notNull(methodType, "methodType");

        this.cursorMode =
                Asserts.notNull(cursorMode, "cursorMode");

        this.resultKind =
                Asserts.notNull(resultKind, "resultKind");

        this.resultSetParameter =
                Asserts.notNull(
                        resultSetParameter,
                        "resultSetParameter");

        this.rowNumberParameter =
                rowNumberParameter;

        this.returnType =
                Asserts.notNull(
                        returnType,
                        "returnType");

        this.rowType =
                Asserts.notNull(
                        rowType,
                        "rowType");

        this.rules =
                Collections.unmodifiableList(
                        rules);
    }


    public ExecutableElement method() {
        return method;
    }


    /**
     * Method type resolved from the actual compiled mapper interface.
     *
     * <p>For inherited generic methods this contains the specialized concrete
     * parameter and return types.</p>
     */
    public ExecutableType methodType() {
        return methodType;
    }


    public CursorMode cursorMode() {
        return cursorMode;
    }


    public boolean consumesCursor() {
        return cursorMode ==
                CursorMode.CURSOR;
    }


    public boolean currentRow() {
        return cursorMode ==
                CursorMode.CURRENT_ROW;
    }


    public ResultKind resultKind() {
        return resultKind;
    }


    public boolean single() {
        return resultKind ==
                ResultKind.SINGLE;
    }


    public boolean list() {
        return resultKind ==
                ResultKind.LIST;
    }


    public VariableElement resultSetParameter() {
        return resultSetParameter;
    }


    public VariableElement rowNumberParameter() {
        return rowNumberParameter;
    }


    public TypeMirror returnType() {
        return returnType;
    }


    /**
     * Target type for one ResultSet row.
     *
     * <p>For {@code List<T>} mapper methods this is {@code T}; otherwise it is
     * the method return type.</p>
     */
    public TypeMirror rowType() {
        return rowType;
    }


    public List<Rule> rules() {
        return rules;
    }


    /*
     * --------------------------------------------------------------
     * Mapping Rule
     * --------------------------------------------------------------
     */

    public static final class Rule {

        private final Mapping mapping;


        Rule(Mapping mapping) {
            this.mapping =
                    Asserts.notNull(
                            mapping,
                            "mapping");
        }


        public Mapping mapping() {
            return mapping;
        }


        public String target() {
            return mapping.target();
        }


        public String source() {
            return mapping.source();
        }


        public String[] sources() {
            return mapping.sources();
        }


        public String compute() {
            return mapping.compute();
        }


        public boolean ignore() {
            return mapping.ignore();
        }
    }
}
