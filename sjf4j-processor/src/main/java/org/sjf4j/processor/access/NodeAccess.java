package org.sjf4j.processor.access;

import org.sjf4j.processor.property.Property;

import javax.lang.model.type.TypeMirror;
import java.util.Objects;


/**
 * Describes one statically resolved node access.
 *
 * <p>A null read type means the access is not readable. A null write type
 * means the access is not writable.</p>
 */
public final class NodeAccess {

    public enum Kind {
        PROPERTY,
        MAP,
        JSON_OBJECT,
        LIST,
        ARRAY,
        JSON_ARRAY,
        DYNAMIC
    }


    private final Kind kind;
    private final TypeMirror ownerType;

    private final TypeMirror readType;
    private final TypeMirror writeType;

    private final Property property;


    NodeAccess(
            Kind kind,
            TypeMirror ownerType,
            TypeMirror readType,
            TypeMirror writeType,
            Property property) {

        this.kind =
                Objects.requireNonNull(
                        kind,
                        "kind");

        this.ownerType =
                Objects.requireNonNull(
                        ownerType,
                        "ownerType");

        this.readType = readType;
        this.writeType = writeType;
        this.property = property;
    }


    public Kind kind() {
        return kind;
    }

    public TypeMirror ownerType() {
        return ownerType;
    }

    public TypeMirror readType() {
        return readType;
    }

    public TypeMirror writeType() {
        return writeType;
    }

    public Property property() {
        return property;
    }

    public boolean readable() {
        return readType != null;
    }

    public boolean writable() {
        return writeType != null;
    }
}