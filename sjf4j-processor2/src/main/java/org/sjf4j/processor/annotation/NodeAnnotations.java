package org.sjf4j.processor.annotation;

import org.sjf4j.annotation.node.NodeProperty;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Map;

/**
 * Resolves node annotation semantics from SJF4J and compatible annotations.
 */
public final class NodeAnnotations {

    private static final String JACKSON3_PROPERTY =
            "tools.jackson.annotation.JsonProperty";
    private static final String JACKSON2_PROPERTY =
            "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String FASTJSON2_PROPERTY =
            "com.alibaba.fastjson2.annotation.JSONField";

    /**
     * Returns the explicit node-facing property name, or null when none is declared.
     */
    public String explicitPropertyName(Element element) {
        NodeProperty property = element.getAnnotation(NodeProperty.class);
        if (property != null && !property.value().isEmpty()) {
            return property.value();
        }

        String name = stringValue(element, JACKSON3_PROPERTY, "value");
        if (name != null && !name.isEmpty()) {
            return name;
        }

        name = stringValue(element, JACKSON2_PROPERTY, "value");
        if (name != null && !name.isEmpty()) {
            return name;
        }

        name = stringValue(element, FASTJSON2_PROPERTY, "name");
        if (name != null && !name.isEmpty()) {
            return name;
        }

        return null;
    }

    /**
     * Returns the explicit node-facing property name or the Java fallback name.
     */
    public String propertyName(Element element, String fallback) {
        String name = explicitPropertyName(element);
        return name == null ? fallback : name;
    }

    private String stringValue(
            Element element,
            String annotationName,
            String memberName) {

        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            Element annotation = mirror.getAnnotationType().asElement();

            if (!(annotation instanceof TypeElement)) {
                continue;
            }

            if (!((TypeElement) annotation)
                    .getQualifiedName()
                    .contentEquals(annotationName)) {
                continue;
            }

            for (Map.Entry<? extends ExecutableElement,
                    ? extends AnnotationValue> entry
                    : mirror.getElementValues().entrySet()) {

                if (!entry.getKey()
                        .getSimpleName()
                        .contentEquals(memberName)) {
                    continue;
                }

                Object value = entry.getValue().getValue();
                return value instanceof String
                        ? (String) value
                        : null;
            }

            return null;
        }

        return null;
    }
}