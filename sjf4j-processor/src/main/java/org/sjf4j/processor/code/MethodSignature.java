package org.sjf4j.processor.code;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/** Formats an analyzed method declaration; callers choose whether to emit throws. */
public final class MethodSignature {

    private MethodSignature() {
    }

    public static String override(ExecutableElement method, boolean includeThrows) {
        return override(method, (ExecutableType) method.asType(), includeThrows);
    }

    public static String override(ExecutableElement method, ExecutableType type, boolean includeThrows) {
        StringBuilder out = new StringBuilder("public ")
                .append(type.getReturnType()).append(' ')
                .append(method.getSimpleName()).append('(');
        List<? extends VariableElement> declarations = method.getParameters();
        List<? extends TypeMirror> parameters = type.getParameterTypes();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parameters.get(i)).append(' ').append(declarations.get(i).getSimpleName());
        }
        out.append(')');
        if (includeThrows && !type.getThrownTypes().isEmpty()) {
            out.append(" throws ");
            List<? extends TypeMirror> thrown = type.getThrownTypes();
            for (int i = 0; i < thrown.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(thrown.get(i));
            }
        }
        return out.toString();
    }
}
