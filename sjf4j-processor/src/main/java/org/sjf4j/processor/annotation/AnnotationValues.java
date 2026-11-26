package org.sjf4j.processor.annotation;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.Map;

/** Annotation mirror access with explicit distinction between declared and defaulted values. */
public final class AnnotationValues {

    private AnnotationValues() {
    }

    public static String name(AnnotationMirror mirror) {
        Element type = mirror.getAnnotationType().asElement();
        return type instanceof TypeElement ? ((TypeElement) type).getQualifiedName().toString() : "";
    }

    public static AnnotationMirror find(Element element, String name) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (name.equals(name(mirror))) return mirror;
        }
        return null;
    }

    public static AnnotationValue explicit(AnnotationMirror mirror, String member) {
        return mirror == null ? null : member(mirror.getElementValues(), member);
    }

    public static AnnotationValue withDefaults(Elements elements, AnnotationMirror mirror, String member) {
        return mirror == null ? null : member(elements.getElementValuesWithDefaults(mirror), member);
    }

    private static AnnotationValue member(Map<? extends ExecutableElement, ? extends AnnotationValue> values,
                                          String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) return entry.getValue();
        }
        return null;
    }

    /** Converts enum constants by simple name; leaves default policy to the caller. */
    public static String text(AnnotationValue value, String fallback) {
        Object raw = value == null ? null : value.getValue();
        if (raw instanceof VariableElement) return ((VariableElement) raw).getSimpleName().toString();
        return raw == null ? fallback : raw.toString();
    }
}
