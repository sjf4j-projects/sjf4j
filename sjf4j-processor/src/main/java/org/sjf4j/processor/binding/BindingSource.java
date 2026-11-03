package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import java.util.List;

/** Source helpers shared by binder emitters. */
final class BindingSource {

    private BindingSource() {
    }

    static void beginOverride(JavaWriter out, ResolvedMethod resolved) {
        ExecutableElement method = resolved.declaration();
        ExecutableType type = resolved.type();
        StringBuilder source = new StringBuilder();

        source.append("public ")
                .append(type.getReturnType())
                .append(' ')
                .append(method.getSimpleName())
                .append('(');

        List<? extends VariableElement> declarations = method.getParameters();
        List<? extends TypeMirror> parameters = type.getParameterTypes();

        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                source.append(", ");
            }

            source.append(parameters.get(i)).append(' ').append(declarations.get(i).getSimpleName());
        }

        source.append(')');

        List<? extends TypeMirror> thrown = type.getThrownTypes();

        if (!thrown.isEmpty()) {
            source.append(" throws ");

            for (int i = 0; i < thrown.size(); i++) {
                if (i > 0) {
                    source.append(", ");
                }

                source.append(thrown.get(i));
            }
        }

        out.line("@Override");
        out.beginBlock(source.toString());
        out.beginBlock("try");
    }

    static void endOverride(JavaWriter out, BindingPlan plan) {
        out.endBlock();

        // Keep catch identifiers distinct from interface parameter names.
        NameAllocator names = new NameAllocator();
        for (VariableElement parameter : plan.method().declaration().getParameters()) {
            names.reserve(parameter.getSimpleName().toString());
        }
        String failure = names.newName("failure");

        out.beginBlock("catch (org.sjf4j.exception.BindingException " + failure + ")");
        out.line("throw " + failure + ";");
        out.endBlock();

        out.beginBlock("catch (Exception " + failure + ")");
        if (plan.direction() == BindingPlan.Direction.READ_FROM) {
            out.line("throw new org.sjf4j.exception.BindingException("
                    + JavaWriter.stringLiteral("failed to read node as '" + diagnosticTypeName(plan.valueType()) + "'")
                    + ", " + failure + ");");
        } else {
            out.line("throw new org.sjf4j.exception.BindingException("
                    + "\"failed to write node type '\" + org.sjf4j.util.Types.name("
                    + parameterName(plan, 0) + ") + \"' to streaming\", " + failure + ");");
        }
        out.endBlock();
        out.endBlock();
    }

    /**
     * Mirror Class.getTypeName()/Type.getTypeName() in generated diagnostics.
     * Nested classes use binary names (Outer$Inner), including generic arguments.
     */
    private static String diagnosticTypeName(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return diagnosticTypeName(((ArrayType) type).getComponentType()) + "[]";
        }
        if (type.getKind() == TypeKind.WILDCARD) {
            WildcardType wildcard = (WildcardType) type;
            if (wildcard.getExtendsBound() != null) {
                return "? extends " + diagnosticTypeName(wildcard.getExtendsBound());
            }
            if (wildcard.getSuperBound() != null) {
                return "? super " + diagnosticTypeName(wildcard.getSuperBound());
            }
            return "?";
        }
        if (type.getKind() != TypeKind.DECLARED) return type.toString();

        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        String name = element.getQualifiedName().toString();
        for (Element enclosing = element.getEnclosingElement();
             enclosing instanceof TypeElement; enclosing = enclosing.getEnclosingElement()) {
            int dot = name.lastIndexOf('.');
            name = name.substring(0, dot) + '$' + name.substring(dot + 1);
        }

        List<? extends TypeMirror> arguments = declared.getTypeArguments();
        if (arguments.isEmpty()) return name;

        StringBuilder result = new StringBuilder(name).append('<');
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) result.append(", ");
            result.append(diagnosticTypeName(arguments.get(i)));
        }
        return result.append('>').toString();
    }

    static String parameterName(BindingPlan plan, int index) {
        return plan.method().declaration().getParameters().get(index).getSimpleName().toString();
    }
}
