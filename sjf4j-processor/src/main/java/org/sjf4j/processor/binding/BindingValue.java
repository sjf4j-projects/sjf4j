package org.sjf4j.processor.binding;

import org.sjf4j.util.Asserts;
import org.sjf4j.processor.creator.CreatorPlan;

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
        VALUE_CODEC,
        /** Read uses OneOfIO; write dispatches by the actual Java class. */
        ONE_OF,
        POJO,
        JOJO,
        JSON_OBJECT,
        JSON_ARRAY,
        ARRAY,
        LIST,
        SET,
        MAP,

        /** Runtime dispatch for unknown nodes and whole-POJO OneOf fallbacks. */
        RUNTIME
    }

    private final BindingPlan.Direction direction;
    private final TypeMirror type;
    private final Kind kind;
    private final boolean primitive;

    private String helperName;
    private String oneOfOwner;
    private String oneOfProperty;
    private List<OneOfSubtype> oneOfSubtypes = Collections.emptyList();
    private CreatorPlan creator;
    private List<BindingProperty> properties = Collections.emptyList();
    private BindingValue elementValue;
    private BindingValue mapValue;
    private String codecName;
    private String codecPattern;
    private List<String> ignoredNames = Collections.emptyList();
    private boolean readDynamic;
    private boolean writeDynamic;

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
        return (kind == Kind.ONE_OF && direction == BindingPlan.Direction.WRITE_TO)
                || kind == Kind.POJO
                || kind == Kind.JOJO
                || kind == Kind.JSON_OBJECT
                || kind == Kind.JSON_ARRAY
                || kind == Kind.ARRAY
                || kind == Kind.LIST
                || kind == Kind.SET
                || kind == Kind.MAP;
    }

    CreatorPlan creator() {
        return creator;
    }

    void creator(CreatorPlan creator) {
        this.creator = creator;
    }

    String helperName() {
        return helperName;
    }

    void helperName(String helperName) {
        this.helperName = helperName;
    }

    void oneOfMember(String owner, String property) {
        this.oneOfOwner = owner;
        this.oneOfProperty = property;
    }

    String oneOfOwner() {
        return oneOfOwner;
    }

    String oneOfProperty() {
        return oneOfProperty;
    }

    static final class OneOfSubtype {
        final TypeMirror type;
        final BindingValue compiled;

        OneOfSubtype(TypeMirror type, BindingValue compiled) {
            this.type = type;
            this.compiled = compiled;
        }
    }

    void oneOfSubtypes(List<OneOfSubtype> subtypes) {
        this.oneOfSubtypes = Collections.unmodifiableList(subtypes);
    }

    List<OneOfSubtype> oneOfSubtypes() {
        return oneOfSubtypes;
    }

    List<BindingProperty> properties() {
        return properties;
    }

    void properties(List<BindingProperty> properties) {
        this.properties = Collections.unmodifiableList(properties);
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
