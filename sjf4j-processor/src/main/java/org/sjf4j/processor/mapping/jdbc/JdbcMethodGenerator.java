package org.sjf4j.processor.mapping.jdbc;

import org.sjf4j.annotation.mapping.EnsureMapping;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingIfParentPresent;
import org.sjf4j.mapping.JdbcRowMapper;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Analyzes abstract methods declared by a compiled JDBC mapper.
 */
public final class JdbcMethodGenerator {

    private final ProcessorContext context;
    private final TypeSystem types;

    private final TypeMirror resultSetType;
    private final TypeMirror metadataType;
    private final TypeMirror rowMapperType;

    public JdbcMethodGenerator(ProcessorContext context) {
        this.context = context;
        this.types = context.types;

        TypeElement resultSet = context.elements.getTypeElement(ResultSet.class.getName());

        if (resultSet == null) throw new IllegalStateException("java.sql.ResultSet is not available");
        this.resultSetType = resultSet.asType();
        this.metadataType = context.elements.getTypeElement(ResultSetMetaData.class.getName()).asType();
        this.rowMapperType = context.elements.getTypeElement(JdbcRowMapper.class.getName()).asType();
    }

    /**
     * Analyzes one effective abstract mapper method.
     */
    public JdbcPlan analyze(ResolvedMethod resolvedMethod, GeneratedClass generated) {
        ExecutableElement method = resolvedMethod.declaration();
        if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
            error(method, generated, "@CompiledJdbcMapper methods must be abstract");
            return null;
        }

        /*
         * Method-local type variables cannot be specialized by the owning
         * mapper type.
         */
        if (!method.getTypeParameters().isEmpty()) {
            error(method, generated, "JDBC mapper methods cannot declare type parameters");
            return null;
        }

        /*
         * Resolve inherited interface generics before inspecting the method.
         *
         * Example:
         *
         * interface Base<T> {
         *     T map(ResultSet rs);
         * }
         *
         * interface Users extends Base<User> {}
         *
         * resolves to:
         *
         * User map(ResultSet rs)
         */
        ExecutableType methodType = resolvedMethod.type();
        if (!validateConcreteSignature(method, methodType, generated)) return null;

        List<? extends VariableElement> parameters = method.getParameters();
        List<? extends TypeMirror> parameterTypes = methodType.getParameterTypes();
        JdbcPlan.CursorMode cursorMode = resolveCursorMode(method, parameters, parameterTypes, generated);

        if (cursorMode == null) return null;

        TypeMirror returnType = methodType.getReturnType();
        if (returnType.getKind() == TypeKind.VOID) {
            error(method, generated, "JDBC mapper method must return a mapped result");
            return null;
        }

        JdbcPlan.ResultKind resultKind = resolveResultKind(returnType);
        TypeMirror rowType = resolveRowType(method, returnType, resultKind, generated);

        if (rowType == null) return null;

        if ((cursorMode == JdbcPlan.CursorMode.PREPARE) != (resultKind == JdbcPlan.ResultKind.PREPARED)) {
            error(method, generated, "JDBC metadata methods must return JdbcRowMapper<T>, "
                    + "and JDBC ResultSet methods cannot return a prepared mapper");
            return null;
        }

        if (!types.isFullyConcrete(rowType)) {
            error(method, generated, "JDBC row target must be fully concrete: " + rowType);
            return null;
        }

        if (!validateJdbcAnnotations(method, generated)) return null;

        List<JdbcPlan.Rule> rules = readRules(method, generated);

        if (rules == null) return null;

        VariableElement inputParameter = parameters.get(0);
        return new JdbcPlan(method, methodType, cursorMode, resultKind, inputParameter,
                returnType, rowType, rules);
    }

    /*
     * --------------------------------------------------------------
     * Signature
     * --------------------------------------------------------------
     */

    private boolean validateConcreteSignature(ExecutableElement method, ExecutableType methodType,
                                              GeneratedClass generated) {
        List<? extends VariableElement> parameters = method.getParameters();
        List<? extends TypeMirror> parameterTypes = methodType.getParameterTypes();

        for (int i = 0; i < parameterTypes.size(); i++) {
            TypeMirror parameterType = parameterTypes.get(i);
            if (!types.isFullyConcrete(parameterType)) {
                error(parameters.get(i), generated,
                        "JDBC mapper parameter type must be fully concrete: " + parameterType);
                return false;
            }
        }
        return true;
    }

    private JdbcPlan.CursorMode resolveCursorMode(ExecutableElement method,
                                                  List<? extends VariableElement> parameters,
                                                  List<? extends TypeMirror> parameterTypes, GeneratedClass generated) {
        if (parameters.size() == 1) {
            if (types.isSameErasure(parameterTypes.get(0), resultSetType))
                return JdbcPlan.CursorMode.CURSOR;
            if (types.isSameErasure(parameterTypes.get(0), metadataType))
                return JdbcPlan.CursorMode.PREPARE;
        }

        error(method, generated, "JDBC mapper methods must accept ResultSet or "
                + "ResultSetMetaData; use JdbcRowMapper<T> prepare(ResultSetMetaData) "
                + "for current-row mapping");
        return null;
    }

    /*
     * --------------------------------------------------------------
     * Result
     * --------------------------------------------------------------
     */

    private JdbcPlan.ResultKind resolveResultKind(TypeMirror returnType) {
        /*
         * Only java.util.List<T> has cursor-consuming multi-row semantics.
         *
         * ArrayList<T>, Collection<T>, Set<T>, etc. are not interpreted as
         * result containers here.
         */
        if (types.isSameErasure(returnType, types.listType())) return JdbcPlan.ResultKind.LIST;
        if (types.isSameErasure(returnType, rowMapperType)) return JdbcPlan.ResultKind.PREPARED;
        return JdbcPlan.ResultKind.SINGLE;
    }

    private TypeMirror resolveRowType(ExecutableElement method, TypeMirror returnType,
                                      JdbcPlan.ResultKind resultKind, GeneratedClass generated) {
        if (resultKind == JdbcPlan.ResultKind.SINGLE) return returnType;
        if (resultKind == JdbcPlan.ResultKind.PREPARED) {
            if (!(returnType instanceof DeclaredType)) {
                error(method, generated, "JDBC prepared mapper must declare JdbcRowMapper<T>");
                return null;
            }
            List<? extends TypeMirror> arguments = ((DeclaredType) returnType).getTypeArguments();
            if (arguments.size() != 1 || !types.isFullyConcrete(arguments.get(0))) {
                error(method, generated, "JDBC prepared mapper must declare a concrete JdbcRowMapper<T>");
                return null;
            }
            return arguments.get(0);
        }

        TypeMirror elementType = types.listWriteElementType(returnType);

        if (elementType == null) {
            error(method, generated, "JDBC List row element type is not writable: " + returnType);
            return null;
        }

        if (!types.isFullyConcrete(elementType)) {
            error(method, generated, "JDBC List row type must be fully concrete: " + elementType);
            return null;
        }
        return elementType;
    }

    /*
     * --------------------------------------------------------------
     * JDBC Annotation Subset
     * --------------------------------------------------------------
     */

    private boolean validateJdbcAnnotations(ExecutableElement method, GeneratedClass generated) {
        /*
         * JDBC mapping is flat on the source side and does not implement the
         * conditional/ensure write semantics of normal object mapping.
         */
        if (method.getAnnotation(MappingIfParentPresent.class) != null) {
            error(method, generated, "@MappingIfParentPresent is not supported by @CompiledJdbcMapper");
            return false;
        }

        if (method.getAnnotation(EnsureMapping.class) != null) {
            error(method, generated, "@EnsureMapping is not supported by @CompiledJdbcMapper");
            return false;
        }
        return true;
    }

    /*
     * --------------------------------------------------------------
     * Mapping Rules
     * --------------------------------------------------------------
     */

    private List<JdbcPlan.Rule> readRules(ExecutableElement method, GeneratedClass generated) {
        Mapping[] mappings = method.getAnnotationsByType(Mapping.class);
        List<JdbcPlan.Rule> rules = new ArrayList<JdbcPlan.Rule>(mappings.length);
        Set<String> targets = new HashSet<>();

        for (Mapping mapping : mappings) {
            String target = mapping.target();
            if (target == null || target.length() == 0) {
                error(method, generated, "@Mapping target cannot be empty");
                return null;
            }

            if (!targets.add(target)) {
                error(method, generated, "Duplicate JDBC mapping target: " + target);
                return null;
            }

            boolean computed = mapping.compute().trim().length() != 0;
            if (computed && mapping.ignore()) {
                error(method, generated, "JDBC computed mapping cannot use ignore=true");
                return null;
            }

            if (computed && mapping.source().trim().length() != 0) {
                error(method, generated, "JDBC computed mapping must use sources instead of source");
                return null;
            }

            if (!computed && mapping.sources().length != 0) {
                error(method, generated, "JDBC sources requires compute");
                return null;
            }

            rules.add(new JdbcPlan.Rule(mapping));
        }

        return rules;
    }

    /*
     * --------------------------------------------------------------
     * Diagnostic
     * --------------------------------------------------------------
     */

    private void error(Element element, GeneratedClass generated, String message) {
        context.error(element, message);
        generated.invalidate();
    }
}
