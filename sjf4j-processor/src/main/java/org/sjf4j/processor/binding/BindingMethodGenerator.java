package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Analyzes one abstract @CompiledBinder method.
 */
final class BindingMethodGenerator {

    private static final String STREAMING_READER =
            "org.sjf4j.binding.StreamingReader";

    private static final String STREAMING_WRITER =
            "org.sjf4j.binding.StreamingWriter";

    private static final String IO_EXCEPTION =
            "java.io.IOException";

    private final ProcessorContext context;
    private final TypeMirror readerType;
    private final TypeMirror writerType;
    private final TypeMirror ioExceptionType;

    BindingMethodGenerator(ProcessorContext context) {
        this.context = context;
        this.readerType = requiredType(STREAMING_READER);
        this.writerType = requiredType(STREAMING_WRITER);
        this.ioExceptionType = requiredType(IO_EXCEPTION);
    }

    BindingPlan analyze(
            ResolvedMethod resolved,
            GeneratedClass generated) {

        ExecutableElement method =
                resolved.declaration();

        boolean read =
                method.getAnnotation(ReadFrom.class) != null;

        boolean write =
                method.getAnnotation(WriteTo.class) != null;

        if (read == write) {
            error(
                    method,
                    generated,
                    "Binder method must declare exactly one of @ReadFrom or @WriteTo");

            return null;
        }

        if (!isResolvedSignature(resolved)) {
            error(
                    method,
                    generated,
                    "Binder method contains unresolved type variables after interface specialization: " +
                            resolved.type());

            return null;
        }

        if (!declaresIOException(resolved.type())) {
            error(
                    method,
                    generated,
                    "@" + (read ? "ReadFrom" : "WriteTo") +
                            " method must declare java.io.IOException or a supertype");

            return null;
        }

        return read
                ? analyzeRead(resolved, generated)
                : analyzeWrite(resolved, generated);
    }

    private BindingPlan analyzeRead(
            ResolvedMethod resolved,
            GeneratedClass generated) {

        ExecutableElement method =
                resolved.declaration();

        ExecutableType type =
                resolved.type();

        List<? extends TypeMirror> parameters =
                type.getParameterTypes();

        if (parameters.size() != 1 ||
                !isReader(parameters.get(0))) {

            error(
                    method,
                    generated,
                    "@ReadFrom method must have exactly one StreamingReader parameter");

            return null;
        }

        TypeMirror returnType =
                type.getReturnType();

        if (returnType.getKind() == TypeKind.VOID) {
            error(
                    method,
                    generated,
                    "@ReadFrom method must return the bound value");

            return null;
        }

        return BindingPlan.readFrom(
                resolved,
                returnType,
                0);
    }

    private BindingPlan analyzeWrite(
            ResolvedMethod resolved,
            GeneratedClass generated) {

        ExecutableElement method =
                resolved.declaration();

        ExecutableType type =
                resolved.type();

        if (type.getReturnType().getKind() !=
                TypeKind.VOID) {

            error(
                    method,
                    generated,
                    "@WriteTo method must return void");

            return null;
        }

        List<? extends TypeMirror> parameters =
                type.getParameterTypes();

        if (parameters.size() != 2) {
            error(
                    method,
                    generated,
                    "@WriteTo method must have one value parameter and one StreamingWriter parameter");

            return null;
        }

        int writer = -1;

        for (int i = 0; i < parameters.size(); i++) {
            if (isWriter(parameters.get(i))) {
                if (writer >= 0) {
                    writer = -2;
                    break;
                }
                writer = i;
            }
        }

        if (writer < 0) {
            error(
                    method,
                    generated,
                    "@WriteTo method must have exactly one StreamingWriter parameter");

            return null;
        }

        int value = writer == 0 ? 1 : 0;

        return BindingPlan.writeTo(
                resolved,
                parameters.get(value),
                value,
                writer);
    }

    private boolean isResolvedSignature(
            ResolvedMethod method) {

        ExecutableType type =
                method.type();

        if (!context.types.isFullyResolved(
                type.getReturnType())) {
            return false;
        }

        for (TypeMirror parameter :
                type.getParameterTypes()) {

            if (!context.types.isFullyResolved(
                    parameter)) {
                return false;
            }
        }

        for (TypeMirror thrown :
                type.getThrownTypes()) {

            if (!context.types.isFullyResolved(
                    thrown)) {
                return false;
            }
        }

        return true;
    }

    private boolean declaresIOException(
            ExecutableType type) {

        for (TypeMirror thrown :
                type.getThrownTypes()) {

            if (context.typeUtils.isAssignable(
                    ioExceptionType,
                    thrown)) {
                return true;
            }
        }

        return false;
    }

    private boolean isReader(TypeMirror type) {
        return context.typeUtils.isAssignable(
                type,
                readerType);
    }

    private boolean isWriter(TypeMirror type) {
        return context.typeUtils.isAssignable(
                type,
                writerType);
    }

    private TypeMirror requiredType(String name) {
        return context.elements
                .getTypeElement(name)
                .asType();
    }

    private void error(
            ExecutableElement method,
            GeneratedClass generated,
            String message) {

        context.error(method, message);
        generated.invalidate();
    }
}
