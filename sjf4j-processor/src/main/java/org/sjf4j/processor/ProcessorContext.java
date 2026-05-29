package org.sjf4j.processor;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

public final class ProcessorContext {

    public final Types types;
    public final Elements elements;
    public final Messager messager;
    public final Filer filer;

    public final TypeMirror mapType;
    public final TypeMirror listType;
    public final TypeMirror jsonObjectType;
    public final TypeMirror jsonArrayType;
    public final TypeMirror objectType;

    public ProcessorContext(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.messager = env.getMessager();
        this.filer = env.getFiler();
        this.mapType = elements.getTypeElement("java.util.Map").asType();
        this.listType = elements.getTypeElement("java.util.List").asType();
        this.objectType = elements.getTypeElement("java.lang.Object").asType();
        TypeElement jo = elements.getTypeElement("org.sjf4j.JsonObject");
        TypeElement ja = elements.getTypeElement("org.sjf4j.JsonArray");
        this.jsonObjectType = jo == null ? null : jo.asType();
        this.jsonArrayType = ja == null ? null : ja.asType();
    }

    public void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
