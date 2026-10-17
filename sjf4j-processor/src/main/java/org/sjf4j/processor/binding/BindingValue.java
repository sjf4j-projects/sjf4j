package org.sjf4j.processor.binding;

import org.sjf4j.util.Asserts;

import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;

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
        JOJO,
        JSON_OBJECT,
        JSON_ARRAY,
        VALUE_CODEC,
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
    private List<String> ignoredNames = Collections.emptyList();
    private boolean readDynamic;
    private boolean writeDynamic;
    // Codec selection belongs to the compiled value plan, not the backend.
    // Future @BindingOptions may supply defaults without changing emitters.
    private String codecName;
    private String codecPattern;

    BindingValue(
            BindingPlan.Direction direction,
            TypeMirror type,
            Kind kind,
            boolean primitive) {

        this.direction = Asserts.notNull(direction, "direction");
        this.type = Asserts.notNull(type, "type");
        this.kind = Asserts.notNull(kind, "kind");
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
                || kind == Kind.JOJO
                || kind == Kind.JSON_OBJECT
                || kind == Kind.JSON_ARRAY
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

    List<String> ignoredNames() {
        return ignoredNames;
    }

    void ignoredNames(List<String> names) {
        this.ignoredNames = Collections.unmodifiableList(names);
    }

    boolean readDynamic() {
        return readDynamic;
    }

    boolean writeDynamic() {
        return writeDynamic;
    }

    void dynamicOptions(boolean read, boolean write) {
        this.readDynamic = read;
        this.writeDynamic = write;
    }

    boolean isCodec() {
        return kind == Kind.VALUE_CODEC;
    }

    void codecSelection(String name, String pattern) {
        this.codecName = name;
        this.codecPattern = pattern;
    }

    String codecName() {
        return codecName;
    }

    String codecPattern() {
        return codecPattern;
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
