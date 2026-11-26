package org.sjf4j.processor.annotation;

import org.sjf4j.annotation.node.NodeProperty;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Resolves node annotation semantics from SJF4J and compatible annotations.
 */
public final class NodeAnnotations {

    private static final String JACKSON2_CREATOR =
            "com.fasterxml.jackson.annotation.JsonCreator";
    private static final String JACKSON2_ALIAS =
            "com.fasterxml.jackson.annotation.JsonAlias";
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
        if (property != null && !property.value().isEmpty()) return property.value();

        String name = stringValue(element, JACKSON3_PROPERTY, "value");
        if (name != null && !name.isEmpty()) return name;

        name = stringValue(element, JACKSON2_PROPERTY, "value");
        if (name != null && !name.isEmpty()) return name;

        name = stringValue(element, FASTJSON2_PROPERTY, "name");
        if (name != null && !name.isEmpty()) return name;

        return null;
    }

    /**
     * Returns the explicit node-facing property name or the Java fallback name.
     */
    public String propertyName(Element element, String fallback) {
        String name = explicitPropertyName(element);
        return name == null ? fallback : name;
    }

    /** Jackson 2 and 3 share com.fasterxml.jackson.annotation; no Jackson dependency is needed here. */
    public boolean hasJacksonCreator(Element element) {
        return AnnotationValues.find(element, JACKSON2_CREATOR) != null;
    }

    /** Returns creator parameter aliases, following Runtime's annotation precedence. */
    public String[] propertyAliases(Element element) {
        NodeProperty property = element.getAnnotation(NodeProperty.class);
        if (property != null && property.aliases().length > 0) return property.aliases();
        return stringArrayValue(element, JACKSON2_ALIAS, "value");
    }

    /**
     * Returns whether the declared type uses SJF4J's OneOf node semantics.
     *
     * <p>The annotation has moved between node annotation packages before, so
     * recognition deliberately follows the SJF4J annotation namespace and
     * simple name instead of importing one concrete annotation class.</p>
     */
    public boolean hasOneOf(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;

        Element element = ((DeclaredType) type).asElement();

        if (!(element instanceof TypeElement)) return false;

        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            Element annotationType = annotation.getAnnotationType().asElement();

            if (!(annotationType instanceof TypeElement)) continue;

            String name = ((TypeElement) annotationType).getQualifiedName().toString();

            if (name.startsWith("org.sjf4j.annotation.") &&
                    name.endsWith(".OneOf")) {
                return true;
            }
        }

        return false;
    }

    private String[] stringArrayValue(Element element, String annotationName, String memberName) {
        AnnotationValue member = AnnotationValues.explicit(AnnotationValues.find(element, annotationName), memberName);
        Object value = member == null ? null : member.getValue();
        if (!(value instanceof List)) return new String[0];
        List<?> values = (List<?>) value;
        String[] aliases = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Object alias = ((AnnotationValue) values.get(i)).getValue();
            aliases[i] = (String) alias;
        }
        return aliases;
    }

    private String stringValue(Element element, String annotationName, String memberName) {
        AnnotationValue member = AnnotationValues.explicit(AnnotationValues.find(element, annotationName), memberName);
        Object value = member == null ? null : member.getValue();
        return value instanceof String ? (String) value : null;
    }
}
