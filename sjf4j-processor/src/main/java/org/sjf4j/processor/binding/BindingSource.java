package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/** Source helpers shared by binder emitters. */
final class BindingSource {

    private BindingSource() {
    }

    static void beginOverride(
            JavaWriter out,
            ResolvedMethod resolved) {

        ExecutableElement method =
                resolved.declaration();

        ExecutableType type =
                resolved.type();

        StringBuilder source =
                new StringBuilder();

        source.append("public ")
                .append(type.getReturnType())
                .append(' ')
                .append(method.getSimpleName())
                .append('(');

        List<? extends VariableElement> declarations =
                method.getParameters();

        List<? extends TypeMirror> parameters =
                type.getParameterTypes();

        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                source.append(", ");
            }

            source.append(parameters.get(i))
                    .append(' ')
                    .append(declarations.get(i)
                            .getSimpleName());
        }

        source.append(')');

        List<? extends TypeMirror> thrown =
                type.getThrownTypes();

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
    }

    static void endOverride(JavaWriter out) {
        out.endBlock();
    }

    static String parameterName(
            BindingPlan plan,
            int index) {

        return plan.method()
                .declaration()
                .getParameters()
                .get(index)
                .getSimpleName()
                .toString();
    }
}
