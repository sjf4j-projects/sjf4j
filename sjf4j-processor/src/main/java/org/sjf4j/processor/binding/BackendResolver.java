package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.BindingBackend;
import org.sjf4j.annotation.binding.BindingFormat;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/** Resolves the concrete backend used by one compiled binder interface. */
final class BackendResolver {

    private final ProcessorContext context;

    BackendResolver(ProcessorContext context) {
        this.context = context;
    }

    BackendSpec resolve(
            TypeElement type,
            GeneratedClass generated) {

        CompiledBinder annotation =
                type.getAnnotation(CompiledBinder.class);

        if (annotation == null) {
            error(
                    type,
                    generated,
                    "Missing @CompiledBinder annotation");
            return null;
        }

        BindingFormat format = annotation.format();
        BindingBackend backend = annotation.backend();

        BackendGroup group = BackendCatalog.group(format);

        if (backend == BindingBackend.AUTO) {
            for (BackendSpec candidate : group.backends()) {
                if (usable(candidate)) {
                    return candidate;
                }
            }

            error(
                    type,
                    generated,
                    "No usable " + format +
                            " backend is visible on the compilation classpath");
            return null;
        }

        BackendSpec selected = group.find(backend);

        if (selected == null) {
            error(
                    type,
                    generated,
                    "Binding backend " + backend +
                            " does not support format " + format);
            return null;
        }

        /*
         * Check the native library first. An aggregate SJF4J artifact may make
         * every adapter visible even when the corresponding third-party
         * library is intentionally absent.
         */
        if (!libraryVisible(selected)) {
            error(
                    type,
                    generated,
                    "Binding backend " + backend +
                            " requires native library type " +
                            selected.libraryMarkerType() +
                            " on the compilation classpath");
            return null;
        }

        if (!adapterVisible(selected)) {
            error(
                    type,
                    generated,
                    "Binding backend " + backend +
                            " is not available on the compilation classpath; " +
                            "ensure the SJF4J backend adapter " +
                            selected.binderType() +
                            " and its Reader/Writer types are present");
            return null;
        }

        TypeElement binder =
                context.elements.getTypeElement(
                        selected.binderType());

        if (!hasPublicNoArgsConstructor(binder)) {
            error(
                    type,
                    generated,
                    "Binding backend " + backend +
                            " must expose a public no-arg constructor: " +
                            selected.binderType());
            return null;
        }

        return selected;
    }

    /**
     * AUTO requires both the native library and the SJF4J adapter. The native
     * marker is checked first so aggregate SJF4J artifacts do not force javac
     * to complete adapter classes whose optional dependencies are absent.
     */
    private boolean usable(BackendSpec spec) {
        if (!libraryVisible(spec) ||
                !adapterVisible(spec)) {
            return false;
        }

        TypeElement binder =
                context.elements.getTypeElement(
                        spec.binderType());

        return hasPublicNoArgsConstructor(binder);
    }

    private boolean libraryVisible(BackendSpec spec) {
        String marker = spec.libraryMarkerType();
        return marker == null ||
                context.elements.getTypeElement(marker) != null;
    }

    private boolean adapterVisible(BackendSpec spec) {
        return context.elements.getTypeElement(
                spec.binderType()) != null &&
                context.elements.getTypeElement(
                        spec.readerType()) != null &&
                context.elements.getTypeElement(
                        spec.writerType()) != null;
    }

    private boolean hasPublicNoArgsConstructor(
            TypeElement type) {

        if (type == null) {
            return false;
        }

        boolean declared = false;

        for (Element element :
                type.getEnclosedElements()) {

            if (element.getKind() !=
                    ElementKind.CONSTRUCTOR) {
                continue;
            }

            declared = true;

            ExecutableElement constructor =
                    (ExecutableElement) element;

            if (constructor.getParameters().isEmpty() &&
                    constructor.getModifiers().contains(
                            Modifier.PUBLIC)) {
                return true;
            }
        }

        /* Public classes with no declared constructor have a public default one. */
        return !declared &&
                type.getModifiers().contains(
                        Modifier.PUBLIC);
    }

    private void error(
            TypeElement type,
            GeneratedClass generated,
            String message) {

        context.error(type, message);
        generated.invalidate();
    }
}
