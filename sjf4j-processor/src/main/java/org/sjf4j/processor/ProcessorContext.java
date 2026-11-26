package org.sjf4j.processor;

import org.sjf4j.processor.discovery.RegistryDiscovery;
import org.sjf4j.util.Asserts;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.TreeMap;

/**
 * Javac services, registry discovery and the compiled index for one processing run.
 * Errors are reported against source elements rather than written into
 * generated code.
 */
public final class ProcessorContext {

    public final Types typeUtils;
    public final Elements elements;
    public final Messager messager;
    public final Filer filer;
    public final ProcessingEnvironment processingEnvironment;

    public final RegistryDiscovery discovery;

    private final Map<String, TypeElement> compiledInterfaces = new TreeMap<>();

    public ProcessorContext(ProcessingEnvironment environment) {
        Asserts.notNull(environment, "environment");

        this.typeUtils = environment.getTypeUtils();
        this.elements = environment.getElementUtils();
        this.messager = environment.getMessager();
        this.filer = environment.getFiler();
        this.processingEnvironment = environment;

        this.discovery = new RegistryDiscovery(this);
    }

    /** Records an implementation only after its source file was emitted successfully. */
    public void recordCompiledInterface(TypeElement type) {
        compiledInterfaces.put(elements.getBinaryName(type).toString(), type);
    }

    /** Writes a framework-neutral index once, in the final processing round. */
    public void writeCompiledIndex() {
        if (compiledInterfaces.isEmpty()) return;

        try {
            FileObject file = filer.createResource(
                    StandardLocation.CLASS_OUTPUT, "", "META-INF/sjf4j/compiled.interfaces",
                    compiledInterfaces.values().toArray(new Element[0]));
            try (Writer out = file.openWriter()) {
                for (String name : compiledInterfaces.keySet()) {
                    out.write(name);
                    out.write('\n');
                }
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write SJF4J compiled interface index: " + e.getMessage());
        }
    }

    /** Creates isolated compiler services for one generated interface. */
    public CompilationContext forInterface(TypeElement owner, boolean useRegistries) {
        return new CompilationContext(this, owner, useRegistries);
    }

    /** Emits a compile-time error at the supplied source element. */
    public void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

}
