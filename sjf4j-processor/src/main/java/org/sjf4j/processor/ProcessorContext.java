package org.sjf4j.processor;

import org.sjf4j.processor.access.NodeAccessResolver;
import org.sjf4j.processor.annotation.NodeAnnotations;
import org.sjf4j.processor.method.InterfaceMethodResolver;
import org.sjf4j.processor.property.PropertyResolver;
import org.sjf4j.processor.type.TypeSystem;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Objects;

public final class ProcessorContext {

    public final Types typeUtils;
    public final Elements elements;
    public final Messager messager;
    public final Filer filer;

    public final TypeSystem types;
    public final NodeAnnotations annotations;
    public final PropertyResolver properties;
    public final NodeAccessResolver access;
    public final InterfaceMethodResolver methods;

    public ProcessorContext(ProcessingEnvironment environment) {
        Objects.requireNonNull(environment, "environment");

        this.typeUtils = environment.getTypeUtils();
        this.elements = environment.getElementUtils();
        this.messager = environment.getMessager();
        this.filer = environment.getFiler();

        this.types =
                new TypeSystem(typeUtils, elements);

        this.annotations =
                new NodeAnnotations();

        this.properties =
                new PropertyResolver(
                        typeUtils,
                        elements,
                        messager,
                        types,
                        annotations);

        this.access =
                new NodeAccessResolver(
                        types,
                        properties);

        this.methods =
                new InterfaceMethodResolver(
                        typeUtils,
                        elements,
                        types);
    }

    public void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

}