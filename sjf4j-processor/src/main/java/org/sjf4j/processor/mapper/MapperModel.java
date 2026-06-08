package org.sjf4j.processor.mapper;

import org.sjf4j.path.PathSegment;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model types shared by {@link MapperGenerator}.
 *
 * <p>These are annotation-processing value objects used during code generation.
 * They are intentionally package-private and static to keep the namespace clean
 * while grouping related types under one umbrella.</p>
 */
final class MapperModel {

    /** A readable source property: a public getter, record accessor, or public field. */
    static final class Read {
        /** Getter method, or {@code null} for direct field access. */
        final ExecutableElement method;
        /** Java property name (decapitalized from getter or field name). */
        final String javaName;
        /** Property type. */
        final TypeMirror type;

        Read(ExecutableElement m, String n, TypeMirror t) {
            method = m;
            javaName = n;
            type = t;
        }
    }

    /** A writable target property: a public setter or non-final public field. */
    static final class Write {
        /** Setter method, or {@code null} for direct field assignment. */
        final ExecutableElement setter;
        /** Java property name (decapitalized from setter or field name). */
        final String javaName;
        /** Property type. */
        final TypeMirror type;

        Write(ExecutableElement s, String n, TypeMirror t) {
            setter = s;
            javaName = n;
            type = t;
        }
    }

    /** One source parameter of a mapper method, with its precomputed readable properties. */
    static final class SourceParam {
        final VariableElement element;
        /** Parameter name as declared in the source method. */
        final String name;
        /** Readable properties indexed by property name; empty for dynamic sources. */
        final Map<String, Read> reads;
        /** True when the source type is a Map, JsonObject, or other dynamic container. */
        final boolean dynamic;

        SourceParam(VariableElement e, Map<String, Read> r, boolean d) {
            element = e;
            name = e.getSimpleName().toString();
            reads = r;
            dynamic = d;
        }
    }

    /** A resolved source reference after multi-parameter disambiguation. */
    static final class ResolvedSource {
        /** Which source parameter this reference reads from. */
        final SourceParam param;
        /** The property name, JSONPath, or JSON Pointer to read. */
        final String path;
        /** True when the root parameter itself may be null. */
        final boolean nullableRoot;

        ResolvedSource(SourceParam p, String s, boolean n) {
            param = p;
            path = s;
            nullableRoot = n;
        }
    }

    /**
     * A generated Java expression with type metadata and optional local variable
     * declarations that must precede the expression.
     */
    static final class Expr {
        /** The Java expression code. */
        String code;
        /** Expression result type, or {@code null} for computed expressions. */
        final TypeMirror type;
        /** True when the expression was produced by a path read. */
        final boolean path;
        /** True when the root of the source path may be null. */
        final boolean nullableRoot;
        /** True when the expression has already been assigned to a local variable. */
        boolean local;
        /** Local variable declarations that must be emitted before this expression. */
        final List<String> temps = new ArrayList<String>();
        /** Original nullable source expression to guard before a null-preserving generated conversion. */
        String nullGuardSource;
        /** Type of {@link #nullGuardSource}. */
        TypeMirror nullGuardType;
        /** True when {@link #nullGuardSource} is already a local variable. */
        boolean nullGuardLocal;
        /** Converted value template; {@code $source} is replaced with the guarded source variable. */
        String nullGuardCodeTemplate;

        Expr(String c, TypeMirror t) {
            this(c, t, false);
        }

        Expr(String c, TypeMirror t, boolean p) {
            this(c, t, p, false, false);
        }

        Expr(String c, TypeMirror t, boolean p, boolean n, boolean l) {
            code = c;
            type = t;
            path = p;
            nullableRoot = n;
            local = l;
        }
    }

    /** A cached source read result, keyed by source path, to avoid duplicate reads. */
    static final class CachedRead {
        final String code;
        final TypeMirror type;
        final boolean path;
        final boolean nullableRoot;

        CachedRead(String c, TypeMirror t, boolean p, boolean n) {
            code = c;
            type = t;
            path = p;
            nullableRoot = n;
        }
    }

    /** Resolved annotation values for one {@code @Mapping} entry. */
    static final class MappingSpec {
        final String target;
        final String source;
        final String[] sources;
        final String compute;
        final String nestedMapper;

        MappingSpec(String t, String s, String[] ss, String c, String n) {
            target = t;
            source = s;
            sources = ss;
            compute = c;
            nestedMapper = n;
        }
    }

    /** How a target path write handles missing intermediate parents. */
    static enum PathWriteMode {
        /** Throw when a parent is null. */
        STRICT,
        /** Skip the write when a parent is null. */
        IF_PARENT_PRESENT,
        /** Create missing parents before writing. */
        ENSURE
    }

    /** A parsed target path with its write mode for {@code @Mapping(target = "$.a.b")} style mappings. */
    static final class TargetPathWrite {
        /** Original path string. */
        final String path;
        /** Parsed path segments. */
        final PathSegment[] segments;
        /** How to handle missing intermediate parents. */
        final PathWriteMode mode;

        TargetPathWrite(String p, PathSegment[] s, PathWriteMode m) {
            path = p;
            segments = s;
            mode = m;
        }
    }

    /** Creation plan for a target type: constructor (if any), property order, and writable properties. */
    static final class Plan {
        /** Constructor to use, or {@code null} for no-args + setter/field assignment. */
        final ExecutableElement ctor;
        /** Concrete type constructed or returned by a creator. */
        final TypeMirror type;
        /** Creation expression for mutable creator-based plans, or {@code null}. */
        final String create;
        /** Ordered property names to assign. */
        final List<String> names;
        /** Writable properties indexed by name. */
        final Map<String, Write> writes;

        Plan(ExecutableElement c, TypeMirror t, String createExpr, List<String> n, Map<String, Write> w) {
            ctor = c;
            type = t;
            create = createExpr;
            names = n;
            writes = w;
        }
    }

    /** Describes a collection or map type for container mapping. */
    static final class ContainerType {
        /** True for Map, false for Collection. */
        final boolean map;
        /** Map key type, or {@code null} for collections. */
        final TypeMirror key;
        /** Map value type or collection element type. */
        final TypeMirror value;
        /** The full declared type. */
        final TypeMirror mirror;

        ContainerType(boolean m, TypeMirror k, TypeMirror v, TypeMirror type) {
            map = m;
            key = k;
            value = v;
            mirror = type;
        }
    }

    /** Describes an array-like source that can feed collection targets. */
    static final class ArrayLikeType {
        /** True for Java arrays, false for JsonArray/JAJO sources. */
        final boolean javaArray;
        /** Element type for Java arrays, Object for JsonArray/JAJO. */
        final TypeMirror value;
        /** The full source type. */
        final TypeMirror mirror;

        ArrayLikeType(boolean a, TypeMirror v, TypeMirror type) {
            javaArray = a;
            value = v;
            mirror = type;
        }
    }

    /** A resolved value converter: a mapper method reference and its return type. */
    static final class Converter {
        /** Converter method name (possibly qualified), or {@code null} for direct assignment. */
        final String method;
        /** Converter return type. */
        final TypeMirror type;

        Converter(String m, TypeMirror t) {
            method = m;
            type = t;
        }
    }

    /** A resolved property access expression for grouped path reads. */
    static final class GroupAccess {
        /** The Java access expression, e.g. {@code parentVar.getFoo()}. */
        final String code;
        /** The access result type. */
        final TypeMirror type;

        GroupAccess(String c, TypeMirror t) {
            code = c;
            type = t;
        }
    }

    /**
     * A node in the grouped read tree. Each node represents one intermediate
     * source path segment shared by multiple target mappings.
     */
    static final class GroupNode {
        /** Segment property name. */
        final String name;
        /** Node type after reading this segment. */
        final TypeMirror type;
        /** Generated local variable name holding this segment's value. */
        final String temp;
        /** Java expression to read this segment from its parent. */
        final String code;
        /** Leaf mappings that terminate at this node. */
        final List<GroupLeaf> leaves = new ArrayList<GroupLeaf>();
        /** Child nodes indexed by segment name. */
        final Map<String, GroupNode> children = new LinkedHashMap<String, GroupNode>();

        GroupNode(String n, TypeMirror t, String tmp, String c) {
            name = n;
            type = t;
            temp = tmp;
            code = c;
        }
    }

    /** A leaf mapping in the grouped read tree: one target property resolved from a shared source path. */
    static final class GroupLeaf {
        /** Target property name to assign. */
        final String target;
        /** Generated local variable name holding the leaf value. */
        final String temp;
        /** Leaf value type. */
        final TypeMirror type;
        /** Java expression to read the leaf value. */
        final String code;

        GroupLeaf(String targetName, String tmp, TypeMirror t, String c) {
            target = targetName;
            temp = tmp;
            type = t;
            code = c;
        }
    }
}
