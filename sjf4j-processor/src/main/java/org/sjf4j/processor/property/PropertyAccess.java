package org.sjf4j.processor.property;

import org.sjf4j.util.Asserts;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;

/**
 * A resolved Java member used to read or write a node property.
 */
public final class PropertyAccess {

    private final Element member;
    private final TypeMirror type;

    PropertyAccess(Element member, TypeMirror type) {
        this.member = Asserts.notNull(member, "member");
        this.type = Asserts.notNull(type, "type");
    }

    public Element member() {
        return member;
    }

    public TypeMirror type() {
        return type;
    }

    public String memberName() {
        return member.getSimpleName().toString();
    }

    public boolean isMethod() {
        return member.getKind() == ElementKind.METHOD;
    }

    public boolean isField() {
        return member.getKind() == ElementKind.FIELD;
    }
}