package org.sjf4j.processor;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;

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
        this.mapType = elements.getTypeElement(Map.class.getName()).asType();
        this.listType = elements.getTypeElement(List.class.getName()).asType();
        this.objectType = elements.getTypeElement(Object.class.getName()).asType();
        this.jsonObjectType = elements.getTypeElement(JsonObject.class.getName()).asType();
        this.jsonArrayType = elements.getTypeElement(JsonArray.class.getName()).asType();
    }

    public void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
