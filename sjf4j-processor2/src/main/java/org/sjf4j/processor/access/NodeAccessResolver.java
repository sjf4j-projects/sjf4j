package org.sjf4j.processor.access;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.processor.property.PropertyResolver;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;


/**
 * Resolves compile-time structural access for OBNT node types.
 */
public final class NodeAccessResolver {

    private final TypeSystem types;
    private final PropertyResolver properties;


    public NodeAccessResolver(
            TypeSystem types,
            PropertyResolver properties) {

        this.types =
                Objects.requireNonNull(
                        types,
                        "types");

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties");
    }


    /**
     * Resolves a statically known object property name.
     *
     * <p>For JOJO types, declared Java properties take precedence over the
     * dynamic JsonObject namespace. A declared read-only or write-only property
     * therefore remains read-only or write-only instead of falling back to a
     * dynamic key.</p>
     */
    public NodeAccess resolveName(
            TypeMirror owner,
            String name) {

        if (owner == null ||
                name == null) {

            return null;
        }

        owner =
                types.concrete(owner);

        NodeKind kind =
                types.nodeKind(owner);

        switch (kind) {
            case OBJECT_POJO:
                return property(
                        owner,
                        name);

            case OBJECT_JOJO: {
                Property property =
                        properties.resolve(
                                owner,
                                name);

                return property != null
                        ? property(
                        owner,
                        property)
                        : jsonObject(owner);
            }

            case OBJECT_MAP:
                return map(owner);

            case OBJECT_JSON_OBJECT:
                return jsonObject(owner);

            case OBJECT_EXTERNAL:
                return external(owner);

            case UNKNOWN:
                return unknown(owner);

            default:
                return null;
        }
    }


    /**
     * Resolves an object key whose value is known only at runtime.
     *
     * <p>POJO properties cannot be resolved dynamically. JOJO is supported
     * because its JsonObject namespace remains dynamic.</p>
     */
    public NodeAccess resolveDynamicName(
            TypeMirror owner) {

        if (owner == null) {
            return null;
        }

        owner =
                types.concrete(owner);

        NodeKind kind =
                types.nodeKind(owner);

        switch (kind) {
            case OBJECT_JOJO:
            case OBJECT_JSON_OBJECT:
                return jsonObject(owner);

            case OBJECT_MAP:
                return map(owner);

            case OBJECT_EXTERNAL:
                return external(owner);

            case UNKNOWN:
                return unknown(owner);

            default:
                return null;
        }
    }


    /**
     * Resolves indexed array access.
     *
     * <p>Set deliberately has no indexed access.</p>
     */
    public NodeAccess resolveIndex(
            TypeMirror owner) {

        if (owner == null) {
            return null;
        }

        owner =
                types.concrete(owner);

        NodeKind kind =
                types.nodeKind(owner);

        switch (kind) {
            case ARRAY_ARRAY:
                return array(owner);

            case ARRAY_LIST:
                return list(owner);

            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return jsonArray(owner);

            case ARRAY_EXTERNAL:
                return external(owner);

            case UNKNOWN:
                return unknown(owner);

            default:
                return null;
        }
    }


    /**
     * Resolves append access.
     *
     * <p>Java arrays and Set deliberately do not support append.</p>
     */
    public NodeAccess resolveAppend(
            TypeMirror owner) {

        if (owner == null) {
            return null;
        }

        owner =
                types.concrete(owner);

        NodeKind kind =
                types.nodeKind(owner);

        switch (kind) {
            case ARRAY_LIST:
                return list(owner);

            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return jsonArray(owner);

            case ARRAY_EXTERNAL:
                return external(owner);

            case UNKNOWN:
                return unknown(owner);

            default:
                return null;
        }
    }


    // -------------------------------------------------------------------------
    // Property
    // -------------------------------------------------------------------------

    private NodeAccess property(
            TypeMirror owner,
            String name) {

        Property property =
                properties.resolve(
                        owner,
                        name);

        return property == null
                ? null
                : property(
                owner,
                property);
    }


    private NodeAccess property(
            TypeMirror owner,
            Property property) {

        PropertyAccess read =
                property.read();

        PropertyAccess write =
                property.write();

        return new NodeAccess(
                NodeAccess.Kind.PROPERTY,
                owner,
                read == null
                        ? null
                        : read.type(),
                write == null
                        ? null
                        : write.type(),
                property);
    }


    // -------------------------------------------------------------------------
    // Map
    // -------------------------------------------------------------------------

    private NodeAccess map(
            TypeMirror owner) {

        return new NodeAccess(
                NodeAccess.Kind.MAP,
                owner,
                types.mapReadValueType(owner),
                types.mapWriteValueType(owner),
                null);
    }


    // -------------------------------------------------------------------------
    // JsonObject / JOJO
    // -------------------------------------------------------------------------

    private NodeAccess jsonObject(
            TypeMirror owner) {

        TypeMirror valueType =
                types.objectType();

        return new NodeAccess(
                NodeAccess.Kind.JSON_OBJECT,
                owner,
                valueType,
                valueType,
                null);
    }


    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    private NodeAccess list(
            TypeMirror owner) {

        return new NodeAccess(
                NodeAccess.Kind.LIST,
                owner,
                types.listReadElementType(owner),
                types.listWriteElementType(owner),
                null);
    }


    // -------------------------------------------------------------------------
    // Java array
    // -------------------------------------------------------------------------

    private NodeAccess array(
            TypeMirror owner) {

        TypeMirror elementType =
                ((ArrayType) owner)
                        .getComponentType();

        return new NodeAccess(
                NodeAccess.Kind.ARRAY,
                owner,
                elementType,
                elementType,
                null);
    }


    // -------------------------------------------------------------------------
    // JsonArray / JAJO
    // -------------------------------------------------------------------------

    private NodeAccess jsonArray(
            TypeMirror owner) {

        TypeMirror valueType =
                types.objectType();

        return new NodeAccess(
                NodeAccess.Kind.JSON_ARRAY,
                owner,
                valueType,
                valueType,
                null);
    }


    // -------------------------------------------------------------------------
    // Dynamic Object
    // -------------------------------------------------------------------------

    private NodeAccess dynamic(
            TypeMirror owner) {

        TypeMirror valueType =
                types.objectType();

        return new NodeAccess(
                NodeAccess.Kind.DYNAMIC,
                owner,
                valueType,
                valueType,
                null);
    }


    // -------------------------------------------------------------------------
    // External node
    // -------------------------------------------------------------------------

    private NodeAccess external(
            TypeMirror owner) {

        TypeMirror valueType =
                types.objectType();

        return new NodeAccess(
                NodeAccess.Kind.EXTERNAL,
                owner,
                valueType,
                valueType,
                null);
    }


    // -------------------------------------------------------------------------
    // Unknown static shape
    // -------------------------------------------------------------------------

    private NodeAccess unknown(
            TypeMirror owner) {

        if (types.isObject(owner)) {
            return dynamic(owner);
        }

        if (types.isExternalNode(owner)) {
            return external(owner);
        }

        return null;
    }
}