package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/** Analyzes one abstract @CompiledBinder method. */
final class BindingMethodGenerator {

    private final ProcessorContext context;

    private final TypeMirror stringType;
    private final TypeMirror readerType;
    private final TypeMirror inputStreamType;
    private final TypeMirror writerType;
    private final TypeMirror outputStreamType;
    private final TypeMirror ioExceptionType;

    BindingMethodGenerator(ProcessorContext context) {
        this.context = context;
        this.stringType = requiredType("java.lang.String");
        this.readerType = requiredType("java.io.Reader");
        this.inputStreamType = requiredType("java.io.InputStream");
        this.writerType = requiredType("java.io.Writer");
        this.outputStreamType = requiredType("java.io.OutputStream");
        this.ioExceptionType = requiredType("java.io.IOException");
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

        if (parameters.size() != 1) {
            error(
                    method,
                    generated,
                    "@ReadFrom method must have exactly one input parameter");
            return null;
        }

        BindingPlan.ReadInput input =
                readInput(parameters.get(0));

        if (input == null) {
            error(
                    method,
                    generated,
                    "@ReadFrom input must be String, byte[], InputStream, or Reader");
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
                input);
    }

    private BindingPlan analyzeWrite(
            ResolvedMethod resolved,
            GeneratedClass generated) {

        ExecutableElement method =
                resolved.declaration();

        ExecutableType type =
                resolved.type();

        List<? extends TypeMirror> parameters =
                type.getParameterTypes();

        TypeMirror returnType =
                type.getReturnType();

        if (parameters.size() == 1) {
            BindingPlan.WriteOutput output =
                    returnOutput(returnType);

            if (output == null) {
                error(
                        method,
                        generated,
                        "Single-parameter @WriteTo method must return String or byte[]");
                return null;
            }

            return BindingPlan.writeTo(
                    resolved,
                    parameters.get(0),
                    output);
        }

        if (parameters.size() == 2) {
            if (returnType.getKind() !=
                    TypeKind.VOID) {
                error(
                        method,
                        generated,
                        "Two-parameter @WriteTo method must return void");
                return null;
            }

            BindingPlan.WriteOutput output =
                    streamOutput(parameters.get(1));

            if (output == null) {
                error(
                        method,
                        generated,
                        "Second @WriteTo parameter must be OutputStream or Writer");
                return null;
            }

            return BindingPlan.writeTo(
                    resolved,
                    parameters.get(0),
                    output);
        }

        error(
                method,
                generated,
                "@WriteTo method must have one value parameter, optionally followed by an OutputStream or Writer");
        return null;
    }

    private BindingPlan.ReadInput readInput(
            TypeMirror type) {

        if (context.types.isSameErasure(
                type,
                stringType)) {
            return BindingPlan.ReadInput.STRING;
        }

        if (isByteArray(type)) {
            return BindingPlan.ReadInput.BYTES;
        }

        if (context.typeUtils.isAssignable(
                type,
                inputStreamType)) {
            return BindingPlan.ReadInput.INPUT_STREAM;
        }

        if (context.typeUtils.isAssignable(
                type,
                readerType)) {
            return BindingPlan.ReadInput.READER;
        }

        return null;
    }

    private BindingPlan.WriteOutput returnOutput(
            TypeMirror type) {

        if (context.types.isSameErasure(
                type,
                stringType)) {
            return BindingPlan.WriteOutput.STRING;
        }

        if (isByteArray(type)) {
            return BindingPlan.WriteOutput.BYTES;
        }

        return null;
    }

    private BindingPlan.WriteOutput streamOutput(
            TypeMirror type) {

        if (context.typeUtils.isAssignable(
                type,
                outputStreamType)) {
            return BindingPlan.WriteOutput.OUTPUT_STREAM;
        }

        if (context.typeUtils.isAssignable(
                type,
                writerType)) {
            return BindingPlan.WriteOutput.WRITER;
        }

        return null;
    }

    private boolean isByteArray(TypeMirror type) {
        if (type.getKind() != TypeKind.ARRAY) {
            return false;
        }

        return ((ArrayType) type)
                .getComponentType()
                .getKind() == TypeKind.BYTE;
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
