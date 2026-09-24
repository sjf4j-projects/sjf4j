package org.sjf4j.processor.binding;

import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Recursively compiled binding semantics for one Java value type. */
final class BindingValue {

    enum Kind {
        STRING,
        CHARACTER,
        BOOLEAN,
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        NUMBER,
        BIG_INTEGER,
        BIG_DECIMAL,
        ENUM,
        POJO,
        LIST,
        SET,
        MAP,

        /** Runtime dispatch is reserved for NodeKind.COMPILE_TIME_UNKNOWN. */
        RUNTIME
    }

    private final BindingPlan.Direction direction;
    private final TypeMirror type;
    private final Kind kind;
    private final boolean primitive;

    private String helperName;
    private List<BindingProperty> properties = Collections.emptyList();
    private BindingValue elementValue;
    private BindingValue mapValue;

    BindingValue(
            BindingPlan.Direction direction,
            TypeMirror type,
            Kind kind,
            boolean primitive) {

        this.direction = Objects.requireNonNull(direction, "direction");
        this.type = Objects.requireNonNull(type, "type");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.primitive = primitive;
    }

    BindingPlan.Direction direction() {
        return direction;
    }

    TypeMirror type() {
        return type;
    }

    Kind kind() {
        return kind;
    }

    boolean primitive() {
        return primitive;
    }

    boolean helperRequired() {
        return kind == Kind.POJO
                || kind == Kind.LIST
                || kind == Kind.SET
                || kind == Kind.MAP;
    }

    String helperName() {
        return helperName;
    }

    void helperName(String helperName) {
        this.helperName = helperName;
    }

    List<BindingProperty> properties() {
        return properties;
    }

    void properties(List<BindingProperty> properties) {
        this.properties = Collections.unmodifiableList(properties);
    }

    BindingValue elementValue() {
        return elementValue;
    }

    void elementValue(BindingValue elementValue) {
        this.elementValue = elementValue;
    }

    BindingValue mapValue() {
        return mapValue;
    }

    void mapValue(BindingValue mapValue) {
        this.mapValue = mapValue;
    }
}
