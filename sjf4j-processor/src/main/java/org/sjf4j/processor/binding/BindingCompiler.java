package org.sjf4j.processor.binding;

import org.sjf4j.NodeKind;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.NodeIgnore;
import org.sjf4j.annotation.node.NamingStrategy;
import org.sjf4j.annotation.node.PropertyStrategy;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.creator.CreatorPlan;
import org.sjf4j.processor.creator.StandardCreatorResolver;
import org.sjf4j.processor.discovery.CompiledMetadata;
import org.sjf4j.processor.discovery.ValueCodecMetadata;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.property.PropertyAccess;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles Java types into recursive direct-binding semantics before source
 * emission.
 *
 * <p>Runtime dispatch is explicit for unknown node kinds and OneOf boundaries.
 * Other unsupported statically known shapes remain compile-time errors.</p>
 */
final class BindingCompiler {

    private final ProcessorContext context;
    private final NameAllocator names;
    private final CompiledMetadata metadata;
    private final Backend backend;
    private final StandardCreatorResolver creators;

    private final TypeMirror stringType;
    private final TypeMirror arrayListType;
    private final TypeMirror linkedHashSetType;
    private final TypeMirror linkedHashMapType;

    private final Map<String, BindingValue> cache =
            new LinkedHashMap<String, BindingValue>();

    private final List<BindingValue> helpers =
            new ArrayList<BindingValue>();

    BindingCompiler(
            ProcessorContext context,
            NameAllocator names,
            CompiledMetadata metadata,
            Backend backend) {

        this.context = context;
        this.names = names;
        this.metadata = metadata;
        this.backend = backend;
        this.creators = new StandardCreatorResolver(context);

        this.stringType = requiredType("java.lang.String");
        this.arrayListType = requiredType("java.util.ArrayList");
        this.linkedHashSetType = requiredType("java.util.LinkedHashSet");
        this.linkedHashMapType = requiredType("java.util.LinkedHashMap");
    }

    CompiledMethod compile(
            BindingPlan plan,
            GeneratedClass generated) {

        BindingValue value =
                compileValue(
                        plan.direction(),
                        plan.valueType(),
                        plan.method().declaration(),
                        generated);

        return value == null
                ? null
                : new CompiledMethod(plan, value);
    }

    Collection<BindingValue> helpers() {
        return Collections.unmodifiableList(helpers);
    }

    private BindingValue compileValue(
            BindingPlan.Direction direction,
            TypeMirror inputType,
            ExecutableElement method,
            GeneratedClass generated) {
        return compileValue(direction, inputType, method, generated, null, null);
    }

    /** Codec selection is part of the value plan, never a backend decision. */
    private BindingValue compileValue(
            BindingPlan.Direction direction,
            TypeMirror inputType,
            ExecutableElement method,
            GeneratedClass generated,
            String codecName,
            String codecPattern) {

        if (inputType == null) {
            error(
                    method,
                    generated,
                    "Cannot resolve binding type");
            return null;
        }

        TypeMirror type =
                context.types.concrete(inputType);

        String key =
                direction.name() + ':' + type.toString()
                        + ':' + codecName + ':' + codecPattern;

        BindingValue existing =
                cache.get(key);

        if (existing != null) {
            return existing;
        }

        boolean explicitCodec = codecName != null || codecPattern != null;
        if (explicitCodec && !context.types.isNodeValue(type)
                && cannotRegisterCodecFor(type)) {
            unsupported(method, generated, type,
                    "@NodeProperty codecName/codecPattern cannot be applied to native OBNT types");
            return null;
        }

        BindingValue.Kind kind = explicitCodec
                ? BindingValue.Kind.VALUE_CODEC
                : resolveKind(direction, type, method, generated);

        if (kind == null) {
            return null;
        }

        // A discovered codec declaration makes named formats statically
        // checkable. Runtime-only registrations remain dynamically resolved.
        // A codecPattern overrides codecName and is validated by the codec
        // instance when the generated binder initializes.
        if (kind == BindingValue.Kind.VALUE_CODEC && codecName != null
                && codecPattern == null && metadata.hasValueCodec(type)
                && metadata.valueCodec(type, codecName) == null) {
            error(method, generated, "Unknown @NodeProperty codecName '"
                    + codecName + "' for type " + type);
            return null;
        }

        BindingValue value =
                new BindingValue(
                        direction,
                        type,
                        kind,
                        type.getKind().isPrimitive());

        /*
         * Cache the placeholder before recursively compiling children so
         * recursive POJO/container graphs terminate correctly.
         */
        cache.put(key, value);
        if (kind == BindingValue.Kind.VALUE_CODEC) {
            if (metadata.staticBinding()) {
                List<ValueCodecMetadata> candidates = metadata.valueCodecs(type);
                String selectedFormat = codecPattern == null && codecName != null
                        ? codecName : metadata.selectedFormat(type);
                if (candidates.isEmpty() && context.types.isNodeValue(type)) {
                    if (!compileAnnotatedNodeValue(type, value, method, generated)) {
                        return null;
                    }
                } else if (candidates.isEmpty()) {
                    error(method, generated, "No statically declared ValueCodec for "
                            + type + "; add an @NodeValue field to the interface or @BindingOptions.registries");
                    return null;
                } else {
                if (selectedFormat == null && candidates.size() > 1) {
                    error(method, generated, "Multiple ValueCodecs for " + type
                            + "; select a format with @BindingOptions.valueCodecs or @NodeProperty(codecName)");
                    return null;
                }
                ValueCodecMetadata selected = metadata.valueCodec(type, selectedFormat);
                if (selected == null) {
                    error(method, generated, "Unknown @CodecMapping format '"
                            + selectedFormat + "' for " + type);
                    return null;
                }
                value.staticCodec(selected);
                value.codecSelection(selectedFormat, codecPattern);
                value.codecRawType(selected.rawType);
                }
            } else {
                value.codecSelection(codecName, codecPattern);
                // Dynamic binders preserve RuntimeContext format selection.
                TypeMirror rawType = metadata.stableCodecRawType(type,
                        codecPattern == null ? codecName : null);
                if (rawType == null && context.types.isNodeValue(type)) {
                    rawType = context.types.nodeValueRawType(type);
                }
                value.codecRawType(rawType);
            }
        }

        if (value.helperRequired() || kind == BindingValue.Kind.VALUE_CODEC
                || kind == BindingValue.Kind.ONE_OF) {
            value.helperName(
                    names.newName(
                            helperSuggestion(
                                    direction,
                                    type)));

            helpers.add(value);
        }

        switch (kind) {
            case JOJO:
                TypeElement jojoType = context.types.typeElement(type);
                NodeObject options = jojoType == null ? null : jojoType.getAnnotation(NodeObject.class);
                value.dynamicOptions(options == null || options.readDynamic(),
                        options == null || options.writeDynamic());
                // JOJO declared members use the existing POJO property compiler.
            case POJO:
                if (!compilePojo(
                        value,
                        method,
                        generated)) {
                    return null;
                }
                break;

            case ONE_OF:
                TypeElement oneOfElement = context.types.typeElement(type);
                OneOf oneOfAnnotation = oneOfElement == null ? null
                        : oneOfElement.getAnnotation(OneOf.class);
                if (direction == BindingPlan.Direction.WRITE_TO) {
                    if (!compileOneOfWrite(value, oneOfAnnotation, method, generated)) return null;
                } else {
                    if (!compileOneOfRead(value, oneOfAnnotation, method, generated)) return null;
                }
                break;

            case ARRAY:
                value.elementValue(compileValue(value.direction(),
                        ((ArrayType) value.type()).getComponentType(), method, generated));
                if (value.elementValue() == null) return null;
                break;

            case LIST:
            case SET:
                if (!compileElement(
                        value,
                        method,
                        generated)) {
                    return null;
                }
                break;

            case MAP:
                if (!compileMap(
                        value,
                        method,
                        generated)) {
                    return null;
                }
                break;

            default:
                break;
        }

        return value;
    }

    /**
     * Compile the declared @ValueToRaw / @RawToValue contract into direct
     * calls. Unlike SimpleValueCodec, the raw-to-value conversion must receive
     * JSON null unchanged: @RawToValue may deliberately map it to a value.
     */
    private boolean compileAnnotatedNodeValue(TypeMirror type, BindingValue value,
                                              ExecutableElement site, GeneratedClass generated) {
        TypeElement owner = context.types.typeElement(type);
        TypeMirror rawType = context.types.nodeValueRawType(type);
        if (owner == null || rawType == null) {
            error(site, generated, "Cannot resolve @NodeValue raw type for " + type);
            return false;
        }
        String encoder = null;
        String decoder = null;
        for (Element member : context.elements.getAllMembers(owner)) {
            if (member.getAnnotation(ValueToRaw.class) != null
                    && member.getKind() == ElementKind.METHOD) {
                ExecutableElement candidate = (ExecutableElement) member;
                if (!candidate.getModifiers().contains(Modifier.STATIC)
                        && candidate.getParameters().isEmpty()) {
                    if (encoder != null) {
                        error(site, generated, "Ambiguous @ValueToRaw methods on " + type);
                        return false;
                    }
                    encoder = candidate.getSimpleName().toString();
                }
            }
            if (member.getAnnotation(RawToValue.class) == null) continue;
            if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement candidate = (ExecutableElement) member;
                if (candidate.getModifiers().contains(Modifier.STATIC)
                        && candidate.getParameters().size() == 1
                        && context.typeUtils.isSameType(context.typeUtils.erasure(
                                candidate.getParameters().get(0).asType()),
                                context.typeUtils.erasure(rawType))) {
                    if (decoder != null) {
                        error(site, generated, "Ambiguous @RawToValue factories on " + type);
                        return false;
                    }
                    decoder = ((TypeElement) candidate.getEnclosingElement())
                            .getQualifiedName() + "." + candidate.getSimpleName();
                }
            } else if (member.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement candidate = (ExecutableElement) member;
                if (candidate.getParameters().size() == 1
                        && context.typeUtils.isSameType(context.typeUtils.erasure(
                                candidate.getParameters().get(0).asType()),
                                context.typeUtils.erasure(rawType))) {
                    if (decoder != null) {
                        error(site, generated, "Ambiguous @RawToValue factories on " + type);
                        return false;
                    }
                    decoder = "new " + type;
                }
            }
        }
        if (encoder == null || decoder == null) {
            error(site, generated, "@NodeValue " + type
                    + " requires accessible @ValueToRaw and @RawToValue methods for static binding");
            return false;
        }
        value.nodeValueMethods(encoder, decoder);
        value.codecRawType(rawType);
        return true;
    }

    private BindingValue.Kind resolveKind(
            BindingPlan.Direction direction,
            TypeMirror type,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeKind javaKind =
                type.getKind();

        if (javaKind == TypeKind.ARRAY) {
            // The read plan has a concrete element type; write currently uses
            // the existing runtime array writer instead of a native helper.
            return direction == BindingPlan.Direction.READ_FROM
                    ? BindingValue.Kind.ARRAY : BindingValue.Kind.RUNTIME;
        }

        if (javaKind.isPrimitive()) {
            switch (javaKind) {
                case BOOLEAN:
                    return BindingValue.Kind.BOOLEAN;
                case BYTE:
                    return BindingValue.Kind.BYTE;
                case SHORT:
                    return BindingValue.Kind.SHORT;
                case INT:
                    return BindingValue.Kind.INT;
                case LONG:
                    return BindingValue.Kind.LONG;
                case FLOAT:
                    return BindingValue.Kind.FLOAT;
                case DOUBLE:
                    return BindingValue.Kind.DOUBLE;
                case CHAR:
                    return BindingValue.Kind.CHARACTER;
                default:
                    return unsupported(
                            method,
                            generated,
                            type,
                            "primitive kind " + javaKind);
            }
        }

        String erased =
                context.typeUtils
                        .erasure(type)
                        .toString();

        if ("java.lang.String".equals(erased)) {
            return BindingValue.Kind.STRING;
        }
        if ("java.lang.Character".equals(erased)) {
            return BindingValue.Kind.CHARACTER;
        }
        if ("java.lang.Boolean".equals(erased)) {
            return BindingValue.Kind.BOOLEAN;
        }
        if ("java.lang.Byte".equals(erased)) {
            return BindingValue.Kind.BYTE;
        }
        if ("java.lang.Short".equals(erased)) {
            return BindingValue.Kind.SHORT;
        }
        if ("java.lang.Integer".equals(erased)) {
            return BindingValue.Kind.INT;
        }
        if ("java.lang.Long".equals(erased)) {
            return BindingValue.Kind.LONG;
        }
        if ("java.lang.Float".equals(erased)) {
            return BindingValue.Kind.FLOAT;
        }
        if ("java.lang.Double".equals(erased)) {
            return BindingValue.Kind.DOUBLE;
        }
        if ("java.lang.Number".equals(erased)) {
            return BindingValue.Kind.NUMBER;
        }
        if ("java.math.BigInteger".equals(erased)) {
            return BindingValue.Kind.BIG_INTEGER;
        }
        if ("java.math.BigDecimal".equals(erased)) {
            return BindingValue.Kind.BIG_DECIMAL;
        }

        TypeElement element =
                context.types.typeElement(type);

        if (element != null &&
                element.getKind() == ElementKind.ENUM) {
            return BindingValue.Kind.ENUM;
        }

        // Native containers and external trees precede codecs in the runtime
        // classification contract. Never override their structural semantics.
        NodeKind nodeKind = context.types.nodeKind(type);
        if ((nodeKind == NodeKind.OBJECT_POJO || nodeKind == NodeKind.UNKNOWN
                || nodeKind.isValue())
                && (context.types.isNodeValue(type) || metadata.hasValueCodec(type))) {
            return BindingValue.Kind.VALUE_CODEC;
        }

        if (context.annotations.hasOneOf(type)) {
            return BindingValue.Kind.ONE_OF;
        }

        // PARENT discriminators require the containing POJO's field order and
        // deferred state. Delegate only this POJO subtree, not its ancestors.
        if (direction == BindingPlan.Direction.READ_FROM
                && (nodeKind == NodeKind.OBJECT_POJO || nodeKind == NodeKind.OBJECT_JOJO)
                && hasParentOneOf(type)) {
            return BindingValue.Kind.RUNTIME;
        }

        switch (nodeKind) {
            case COMPILE_TIME_UNKNOWN:
                return BindingValue.Kind.RUNTIME;

            case OBJECT_JSON_OBJECT:
                return BindingValue.Kind.JSON_OBJECT;

            case ARRAY_JSON_ARRAY:
                return BindingValue.Kind.JSON_ARRAY;

            case OBJECT_JOJO:
            case OBJECT_POJO:
                if (direction ==
                        BindingPlan.Direction.WRITE_TO) {
                    return nodeKind == NodeKind.OBJECT_JOJO
                            ? BindingValue.Kind.JOJO : BindingValue.Kind.POJO;
                }

                // Creator selection is resolved once for the POJO, at compile time.
                return nodeKind == NodeKind.OBJECT_JOJO
                        ? BindingValue.Kind.JOJO : BindingValue.Kind.POJO;

            case ARRAY_LIST:
                if (directList(direction, type)) {
                    return BindingValue.Kind.LIST;
                }
                return unsupported(
                        method,
                        generated,
                        type,
                        "List type cannot be bound directly with the declared generic/write constraints");

            case ARRAY_SET:
                if (directSet(direction, type)) {
                    return BindingValue.Kind.SET;
                }
                return unsupported(
                        method,
                        generated,
                        type,
                        "Set type cannot be bound directly with the declared generic/write constraints");

            case OBJECT_MAP:
                if (directMap(direction, type)) {
                    return BindingValue.Kind.MAP;
                }
                return unsupported(
                        method,
                        generated,
                        type,
                        "Map direct binding requires a String key and a directly writable/readable value type");

            case UNKNOWN:
                return unsupported(
                        method,
                        generated,
                        type,
                        "compile-time node kind is UNKNOWN");

            default:
                return unsupported(
                        method,
                        generated,
                        type,
                        "node kind " + nodeKind +
                                " is not yet supported by V2 direct binding");
        }
    }

    private boolean cannotRegisterCodecFor(TypeMirror type) {
        switch (context.types.nodeKind(type)) {
            case VALUE_STRING:
            case VALUE_STRING_CHARACTER:
            case VALUE_STRING_ENUM:
            case VALUE_NUMBER:
            case VALUE_BOOLEAN:
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
            case ARRAY_ARRAY:
                return true;
            default:
                return type.getKind().isPrimitive();
        }
    }

    private boolean hasParentOneOf(TypeMirror type) {
        for (Property property : context.properties.resolve(type).values()) {
            OneOf read = memberOneOf(property.read());
            OneOf write = memberOneOf(property.write());
            if ((read != null && read.scope() == OneOf.Scope.PARENT)
                    || (write != null && write.scope() == OneOf.Scope.PARENT)) {
                return true;
            }
        }
        return false;
    }

    private static OneOf memberOneOf(PropertyAccess access) {
        return access == null ? null : access.member().getAnnotation(OneOf.class);
    }

    /**
     * Compile only the inexpensive, statically safe CURRENT/key case. Other
     * mappings retain OneOfIO, including PARENT/path/shape discriminators,
     * recursive graphs, and subtypes with discriminator-bound fields.
     */
    private boolean compileOneOfRead(BindingValue value, OneOf annotation,
                                     ExecutableElement method, GeneratedClass generated) {
        if (backend != Backend.JACKSON2 || annotation == null
                || annotation.scope() != OneOf.Scope.CURRENT
                || annotation.discriminatorKey().isEmpty() || !annotation.discriminatorPath().isEmpty()
                || annotation.mappings().length == 0) return true;

        List<TypeMirror> types = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        // Preflight the complete set before compiling children: a fallback
        // must not accidentally introduce compiler diagnostics.
        for (OneOfMapping mapping : annotation.mappings()) {
            TypeMirror subtype;
            try {
                Class<?> mapped = mapping.type();
                TypeElement element = context.elements.getTypeElement(mapped.getCanonicalName());
                if (element == null) return true;
                subtype = element.asType();
            } catch (MirroredTypeException e) {
                subtype = e.getTypeMirror();
            }
            if (!canCompileOneOfReadSubtype(subtype, annotation.discriminatorKey(), method)
                    || mapping.when().length == 0) return true;
            for (String when : mapping.when()) {
                if (!keys.add(when)) return true;
            }
            types.add(subtype);
        }

        List<BindingValue.OneOfSubtype> subtypes = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            TypeMirror subtype = types.get(i);
            BindingValue child = compileValue(BindingPlan.Direction.READ_FROM,
                    subtype, method, generated);
            if (child == null) return false;
            subtypes.add(new BindingValue.OneOfSubtype(subtype, child, false,
                    annotation.mappings()[i].when()));
        }
        value.oneOfKey(annotation.discriminatorKey());
        value.oneOfSubtypes(subtypes);
        return true;
    }

    private boolean canCompileOneOfReadSubtype(TypeMirror subtype, String key,
                                               ExecutableElement origin) {
        TypeElement type = context.types.typeElement(subtype);
        if (type == null
                || (type.getKind() != ElementKind.CLASS
                && !"RECORD".equals(type.getKind().name()))
                || !type.getModifiers().contains(Modifier.FINAL)
                || !isVisibleOneOfSubtype(subtype)
                || context.types.nodeKind(subtype) != NodeKind.OBJECT_POJO
                || context.annotations.hasOneOf(subtype) || hasParentOneOf(subtype)) return false;

        int publicConstructors = 0;
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR
                    && enclosed.getModifiers().contains(Modifier.PUBLIC)) publicConstructors++;
        }
        if (publicConstructors != 1) return false;

        // Resolve exactly the constructor or annotated factory that compilePojo
        // will use. Checking only constructors misses @NodeCreator factory aliases
        // that consume the OneOf discriminator.
        // Preflight is optional: invalid/unsupported plans fall back to runtime
        // instead of emitting diagnostics or partially compiling subtype helpers.
        CreatorPlan creator = creators.resolveBinding(subtype, origin, ignored -> {});
        if (creator == null) return false;
        if (creator.kind() != CreatorPlan.Kind.NO_ARGS) {
            List<? extends VariableElement> parameters = creator.executable().getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                VariableElement arg = parameters.get(i);
                boolean isDiscriminator = key.equals(context.annotations.propertyName(
                        arg, arg.getSimpleName().toString()))
                        || java.util.Arrays.asList(context.annotations.propertyAliases(arg)).contains(key);
                // A consumed String creator argument can be initialized directly
                // from the discriminator without buffering the whole object.
                if (isDiscriminator
                        ? !context.types.isSameErasure(creator.parameterTypes().get(i), stringType)
                        : !simpleOneOfReadValue(creator.parameterTypes().get(i))) return false;
            }
        }
        for (Property property : context.properties.resolve(subtype).values()) {
            PropertyAccess writable = property.write();
            if (writable == null) continue;
            if (key.equals(property.name()) || !simpleOneOfReadValue(writable.type())
                    || hasMemberOneOf(property.read()) || hasMemberOneOf(property.write())) return false;
            // Mirror the POJO/Creator property alias precedence before creating
            // compiled subtype helpers. An alias overlapping the discriminator
            // must select the ordinary runtime OneOf path.
            NodeProperty writerAnnotation = writable.member().getAnnotation(NodeProperty.class);
            NodeProperty readerAnnotation = property.read() == null ? null
                    : property.read().member().getAnnotation(NodeProperty.class);
            String[] aliases = writerAnnotation != null && writerAnnotation.aliases().length != 0
                    ? writerAnnotation.aliases()
                    : readerAnnotation == null ? new String[0] : readerAnnotation.aliases();
            if (java.util.Arrays.asList(aliases).contains(key)) return false;
        }
        return true;
    }

    private boolean simpleOneOfReadValue(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return type.getKind() != TypeKind.CHAR;
        }
        TypeElement element = context.types.typeElement(type);
        if (element != null && element.getKind() == ElementKind.ENUM) return false;
        String name = context.typeUtils.erasure(type).toString();
        switch (name) {
            case "java.lang.String":
            case "java.lang.Boolean":
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
            case "java.lang.Long":
            case "java.lang.Float":
            case "java.lang.Double":
            case "java.lang.Number":
            case "java.math.BigInteger":
            case "java.math.BigDecimal":
                return true;
            default:
                // Only codecs with a known raw shape may replay buffered
                // fields without a generic NodeMapper conversion.
                return (context.types.isNodeValue(type) || metadata.hasValueCodec(type))
                        && metadata.stableCodecRawType(type, null) != null;
        }
    }

    private boolean compileOneOfWrite(BindingValue value, OneOf annotation,
                                      ExecutableElement method, GeneratedClass generated) {
        if (annotation == null) return true;
        List<BindingValue.OneOfSubtype> subtypes = new ArrayList<>();
        for (OneOfMapping mapping : annotation.mappings()) {
            TypeMirror subtype;
            try {
                Class<?> target = mapping.type();
                TypeElement element = context.elements.getTypeElement(target.getCanonicalName());
                if (element == null) continue;
                subtype = element.asType();
            } catch (MirroredTypeException e) {
                subtype = e.getTypeMirror();
            }
            boolean visible = isVisibleOneOfSubtype(subtype);
            BindingValue compiled = null;
            if (visible && canCompileOneOfSubtype(subtype)) {
                compiled = compileValue(BindingPlan.Direction.WRITE_TO, subtype, method, generated);
                if (compiled == null) return false;
                addOneOfConstantProperties(compiled, subtype);
            }
            boolean cachedPojo = visible && compiled == null
                    && context.types.nodeKind(subtype) == NodeKind.OBJECT_POJO;
            subtypes.add(new BindingValue.OneOfSubtype(subtype, compiled, cachedPojo));
        }
        value.oneOfSubtypes(subtypes);
        return true;
    }

    private boolean isVisibleOneOfSubtype(TypeMirror subtype) {
        TypeElement type = context.types.typeElement(subtype);
        if (type == null) return false;
        for (Element owner = type; owner instanceof TypeElement; owner = owner.getEnclosingElement()) {
            if (!owner.getModifiers().contains(Modifier.PUBLIC)) return false;
        }
        return true;
    }

    /**
     * Check the entire write graph before calling compileValue. An unsuccessful
     * compileValue has already reported an error and populated its helper cache;
     * it cannot safely be converted into an optional OneOf branch fallback.
     */
    private boolean canCompileOneOfSubtype(TypeMirror subtype) {
        return canCompileOneOfWriteGraph(subtype, new HashSet<String>(), true);
    }

    private boolean canCompileOneOfWriteGraph(TypeMirror type, Set<String> visited,
                                              boolean mappedSubtype) {
        if (type == null) return false;
        type = context.types.concrete(type);
        if (!visited.add(type.toString())) return true;

        if (type.getKind().isPrimitive() || type.getKind() == TypeKind.ARRAY) return true;

        String erased = context.typeUtils.erasure(type).toString();
        switch (erased) {
            case "java.lang.String":
            case "java.lang.Character":
            case "java.lang.Boolean":
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
            case "java.lang.Long":
            case "java.lang.Float":
            case "java.lang.Double":
            case "java.lang.Number":
            case "java.math.BigInteger":
            case "java.math.BigDecimal":
                return true;
            default:
                break;
        }

        TypeElement element = context.types.typeElement(type);
        if (element != null && element.getKind() == ElementKind.ENUM) return true;
        NodeKind kind = context.types.nodeKind(type);
        if (context.types.isNodeValue(type) || metadata.hasValueCodec(type)) return true;
        // The nested OneOf writer performs its own branch eligibility checks.
        if (context.annotations.hasOneOf(type)) return true;

        switch (kind) {
            case COMPILE_TIME_UNKNOWN:
            case OBJECT_JSON_OBJECT:
            case ARRAY_JSON_ARRAY:
                return true;
            case ARRAY_LIST:
            case ARRAY_SET:
                if (kind == NodeKind.ARRAY_LIST ? !directList(BindingPlan.Direction.WRITE_TO, type)
                        : !directSet(BindingPlan.Direction.WRITE_TO, type)) return false;
                return canCompileOneOfWriteGraph(kind == NodeKind.ARRAY_LIST
                                ? context.types.listReadElementType(type)
                                : context.types.setReadElementType(type), visited, false);
            case OBJECT_MAP:
                return directMap(BindingPlan.Direction.WRITE_TO, type)
                        && canCompileOneOfWriteGraph(context.types.mapReadValueType(type), visited, false);
            case OBJECT_POJO:
            case OBJECT_JOJO:
                if (element == null || !hasCompatibleOneOfWriteMembers(element,
                        mappedSubtype && kind == NodeKind.OBJECT_POJO)) return false;
                for (Property property : context.properties.resolve(type).values()) {
                    PropertyAccess access = property.read();
                    if (access == null) continue;
                    if (memberOneOf(property.read()) != null || memberOneOf(property.write()) != null) {
                        continue; // Member OneOf has its own mapped subtype fallbacks.
                    }
                    if (!canCompileOneOfWriteGraph(access.type(), visited, false)) return false;
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * Runtime discovers annotated non-public members in each superclass,
     * whereas PropertyResolver uses public members only. Ignore annotations
     * also belong to Runtime discovery, so do not emit a direct subtype writer
     * when either rule could change its serialized JSON.
     */
    private boolean hasCompatibleOneOfWriteMembers(TypeElement type, boolean allowConstants) {
        NodeObject options = type.getAnnotation(NodeObject.class);
        if (options != null
                && (options.propertyStrategy() != PropertyStrategy.BEAN_FIELD
                || options.naming() != NamingStrategy.IDENTITY)) return false;

        Set<String> constantNames = new HashSet<String>();
        Map<String, Property> publicProperties = context.properties.resolve(type.asType());
        for (TypeElement current = type; current != null; ) {
            for (Element member : current.getEnclosedElements()) {
                if (member.getKind() != ElementKind.FIELD && member.getKind() != ElementKind.METHOD) {
                    continue;
                }
                if (member.getModifiers().contains(Modifier.STATIC)) continue;
                if (member.getAnnotation(NodeIgnore.class) != null) return false;
                if (member.getKind() == ElementKind.FIELD
                        && isIgnoredOneOfFieldType((VariableElement) member)) return false;
                if (member.getModifiers().contains(Modifier.PUBLIC)) continue;
                if (member.getAnnotation(NodeProperty.class) == null
                        && member.getAnnotation(OneOf.class) == null
                        && context.annotations.explicitPropertyName(member) == null
                        && context.annotations.propertyAliases(member).length == 0) continue;

                if (allowConstants && member.getKind() == ElementKind.FIELD
                        && isInlineStringConstant((VariableElement) member)) {
                    VariableElement field = (VariableElement) member;
                    String name = field.getAnnotation(NodeProperty.class).value();
                    if (publicProperties.containsKey(name)
                            || !constantNames.add(name)
                            || hasOneOfConstantAccessor(type, field.getSimpleName().toString())) {
                        return false;
                    }
                    continue;
                }
                return false;
            }
            TypeMirror parent = current.getSuperclass();
            current = parent.getKind() == TypeKind.NONE
                    ? null : context.types.typeElement(parent);
        }
        return true;
    }

    /** A private annotated String constant can be serialized without accessing the instance. */
    private boolean isInlineStringConstant(VariableElement field) {
        NodeProperty property = field.getAnnotation(NodeProperty.class);
        return property != null && !property.value().isEmpty()
                && NodeProperty.CODEC_NAME_UNSET.equals(property.codecName())
                && property.codecPattern().isEmpty()
                && field.getModifiers().contains(Modifier.FINAL)
                && !field.getModifiers().contains(Modifier.TRANSIENT)
                && context.types.isSameErasure(field.asType(), stringType)
                && field.getConstantValue() instanceof String;
    }

    /** An accessor for the same Java field changes Runtime's property family selection. */
    private boolean hasOneOfConstantAccessor(TypeElement type, String fieldName) {
        if (fieldName.isEmpty()) return false;
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Element member : context.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            String name = member.getSimpleName().toString();
            if (name.equals("get" + suffix) || name.equals("is" + suffix)) return true;
        }
        return false;
    }

    /** Only mapped OneOf subtypes may inline these fields; other POJO plans are unchanged. */
    private void addOneOfConstantProperties(BindingValue value, TypeMirror subtype) {
        if (value.kind() != BindingValue.Kind.POJO) return;
        TypeElement type = context.types.typeElement(subtype);
        if (type == null) return;

        List<BindingProperty> constants = new ArrayList<BindingProperty>();
        for (TypeElement current = type; current != null; ) {
            for (Element member : current.getEnclosedElements()) {
                if (member.getKind() != ElementKind.FIELD
                        || member.getModifiers().contains(Modifier.PUBLIC)
                        || member.getModifiers().contains(Modifier.STATIC)) continue;
                VariableElement field = (VariableElement) member;
                if (!isInlineStringConstant(field)) continue;
                String name = field.getAnnotation(NodeProperty.class).value();
                boolean included = false;
                for (BindingProperty property : value.properties()) {
                    if (property.name().equals(name)) {
                        included = true;
                        break;
                    }
                }
                if (!included) {
                    BindingValue stringValue = new BindingValue(BindingPlan.Direction.WRITE_TO,
                            stringType, BindingValue.Kind.STRING, false);
                    constants.add(new BindingProperty(name, (String) field.getConstantValue(),
                            stringValue));
                }
            }
            TypeMirror parent = current.getSuperclass();
            current = parent.getKind() == TypeKind.NONE
                    ? null : context.types.typeElement(parent);
        }
        if (constants.isEmpty()) return;
        constants.addAll(value.properties());
        value.properties(constants);
    }

    private boolean isIgnoredOneOfFieldType(VariableElement field) {
        TypeElement fieldType = context.types.typeElement(field.asType());
        return fieldType != null && fieldType.getAnnotation(NodeIgnore.class) != null;
    }

    /** Member annotations override their declared type's OneOf mapping. */
    private BindingValue compileMemberOneOf(BindingValue owner, String property,
                                            TypeMirror propertyType, OneOf annotation,
                                            ExecutableElement method, GeneratedClass generated) {
        TypeMirror type = context.types.concrete(propertyType);
        BindingValue result = new BindingValue(owner.direction(), type, BindingValue.Kind.ONE_OF, false);
        result.helperName(names.newName(helperSuggestion(owner.direction(), type)));
        if (owner.direction() == BindingPlan.Direction.READ_FROM) {
            result.oneOfMember(context.typeUtils.erasure(owner.type()).toString(), property);
            if (!compileOneOfRead(result, annotation, method, generated)) return null;
        } else if (!compileOneOfWrite(result, annotation, method, generated)) {
            return null;
        }
        helpers.add(result);
        return result;
    }

    private boolean compilePojo(
            BindingValue value,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeElement element = context.types.typeElement(value.type());
        if (value.direction() == BindingPlan.Direction.READ_FROM
                && element != null && "RECORD".equals(element.getKind().name())) {
            return compileRecord(value, method, generated);
        }

        if (value.direction() == BindingPlan.Direction.READ_FROM) {
            CreatorPlan creator = creators.resolveBinding(value.type(), method,
                    message -> error(method, generated, message));
            if (creator == null) return false;
            if (creator.kind() != CreatorPlan.Kind.NO_ARGS) {
                if (value.kind() == BindingValue.Kind.JOJO) {
                    error(method, generated, "Compiled JOJO creators are not supported yet: " + value.type());
                    return false;
                }
                return compileCreatorPojo(value, creator, method, generated);
            }
        }

        Map<String, Property> properties =
                context.properties.resolve(
                        value.type());

        List<BindingProperty> compiled =
                new ArrayList<BindingProperty>(
                        properties.size());

        Map<String, String> claimedNames = new LinkedHashMap<String, String>();
        List<String> ignored = new ArrayList<String>();

        for (Property property :
                properties.values()) {

            PropertyAccess access =
                    value.direction() ==
                            BindingPlan.Direction.READ_FROM
                            ? property.write()
                            : property.read();

            if (access == null) {
                if (value.kind() == BindingValue.Kind.JOJO
                        && value.direction() == BindingPlan.Direction.READ_FROM) {
                    ignored.add(property.name());
                    PropertyAccess readable = property.read();
                    if (readable != null) {
                        NodeProperty ann = readable.member().getAnnotation(NodeProperty.class);
                        if (ann != null) Collections.addAll(ignored, ann.aliases());
                    }
                }
                continue;
            }

            NodeProperty readAnnotation = property.read() == null ? null
                    : property.read().member().getAnnotation(NodeProperty.class);
            NodeProperty writeAnnotation = property.write() == null ? null
                    : property.write().member().getAnnotation(NodeProperty.class);

            boolean memberOneOf = hasMemberOneOf(property.read()) || hasMemberOneOf(property.write());

            String[] aliases = new String[0];
            if (value.direction() == BindingPlan.Direction.READ_FROM) {
                if (writeAnnotation != null && writeAnnotation.aliases().length != 0) {
                    aliases = writeAnnotation.aliases();
                } else if (readAnnotation != null) {
                    aliases = readAnnotation.aliases();
                }
            }
            if (value.direction() == BindingPlan.Direction.READ_FROM) {
                for (String key : aliases) {
                    if (key.isEmpty()) {
                        error(method, generated, "Empty @NodeProperty alias on " + value.type()
                                + "." + property.name());
                        return false;
                    }
                }
                String[] keys = new String[aliases.length + 1];
                keys[0] = property.name();
                System.arraycopy(aliases, 0, keys, 1, aliases.length);
                for (String key : keys) {
                    String previous = claimedNames.putIfAbsent(key, property.name());
                    if (previous != null) {
                        error(method, generated, "Duplicate compiled property name '" + key
                                + "' for " + previous + " and " + property.name());
                        return false;
                    }
                }
            }

            if (conflictingCodecOptions(readAnnotation, writeAnnotation)) {
                error(method, generated, "Conflicting @NodeProperty codecName/codecPattern on "
                        + value.type() + "." + property.name());
                return false;
            }
            BindingValue propertyValue = memberOneOf
                    ? compileMemberOneOf(value, property.name(), access.type(),
                            memberOneOf(property.read()) != null ? memberOneOf(property.read())
                                    : memberOneOf(property.write()), method, generated)
                    : compileValue(
                            value.direction(),
                            access.type(),
                            method,
                            generated,
                            selectedCodecName(readAnnotation, writeAnnotation),
                            selectedCodecPattern(readAnnotation, writeAnnotation));

            if (propertyValue == null) {
                return false;
            }

            compiled.add(
                    new BindingProperty(
                            property.name(),
                            aliases,
                            access,
                            propertyValue));
        }

        value.properties(compiled);
        value.ignoredNames(ignored);
        return true;
    }

    /**
     * Record parameters are read directly into typed locals. They are not
     * settable properties, so PropertyResolver's setter/field path cannot
     * describe them.
     */
    private boolean compileRecord(BindingValue value, ExecutableElement method,
                                  GeneratedClass generated) {
        CreatorPlan creator = creators.resolveBinding(value.type(), method,
                message -> error(method, generated, message));
        if (creator == null) return false;
        if (creator.kind() == CreatorPlan.Kind.STATIC_FACTORY) {
            return compileCreatorPojo(value, creator, method, generated);
        }
        if (creator.kind() != CreatorPlan.Kind.CONSTRUCTOR) {
            error(method, generated, "Record requires a constructor or @NodeCreator factory: " + value.type());
            return false;
        }

        List<? extends VariableElement> args = creator.executable().getParameters();
        List<BindingProperty> compiled = new ArrayList<>(args.size());
        Map<String, String> claimed = new LinkedHashMap<>();

        for (int i = 0; i < args.size(); i++) {
            VariableElement arg = args.get(i);
            NodeProperty annotation = arg.getAnnotation(NodeProperty.class);
            String name = context.annotations.propertyName(arg, arg.getSimpleName().toString());
            String[] aliases = context.annotations.propertyAliases(arg);
            if (name.isEmpty()) {
                error(method, generated, "Empty record creator property name in " + value.type());
                return false;
            }
            for (String key : recordNames(name, aliases)) {
                if (key.isEmpty()) {
                    error(method, generated, "Empty @NodeProperty alias on " + value.type() + "." + name);
                    return false;
                }
                String previous = claimed.putIfAbsent(key, name);
                if (previous != null) {
                    error(method, generated, "Duplicate compiled property name '" + key
                            + "' for " + previous + " and " + name);
                    return false;
                }
            }
            OneOf override = creatorOneOf(value.type(), arg, name);
            if (override != null && override.scope() != OneOf.Scope.CURRENT) {
                error(method, generated, "creator @OneOf supports only scope=CURRENT: "
                        + value.type() + "." + name);
                return false;
            }
            if (override != null && annotation != null
                    && (!NodeProperty.CODEC_NAME_UNSET.equals(annotation.codecName())
                    || !annotation.codecPattern().isEmpty())) {
                error(method, generated, "creator @OneOf cannot be combined with a value codec on "
                        + value.type() + "." + name);
                return false;
            }
            BindingValue child = override == null
                    ? compileValue(value.direction(), creator.parameterTypes().get(i),
                            method, generated,
                            annotation == null || NodeProperty.CODEC_NAME_UNSET.equals(annotation.codecName())
                                    ? null : annotation.codecName(),
                            annotation == null || annotation.codecPattern().isEmpty()
                                    ? null : annotation.codecPattern())
                    : compileCreatorOneOf(value, creator.parameterTypes().get(i), override,
                            i, method, generated);
            if (child == null) return false;
            compiled.add(new BindingProperty(name, aliases, child, i, args.size()));
        }

        value.creator(creator);
        value.properties(compiled);
        return true;
    }

    /** Resolve Creator OneOf from parameter or bound member, including inherited fields. */
    private OneOf creatorOneOf(TypeMirror ownerType, VariableElement parameter, String name) {
        OneOf onParameter = parameter.getAnnotation(OneOf.class);
        if (onParameter != null) return onParameter;
        Property property = context.properties.resolve(ownerType).get(name);
        if (property != null) {
            OneOf declared = memberOneOf(property.read());
            if (declared == null) declared = memberOneOf(property.write());
            if (declared != null) return declared;
        }
        TypeElement owner = context.types.typeElement(ownerType);
        if (owner == null) return null;
        for (Element member : context.elements.getAllMembers(owner)) {
            if (member.getKind() != ElementKind.FIELD) continue;
            OneOf declared = member.getAnnotation(OneOf.class);
            if (declared == null) continue;
            String fieldName = context.annotations.propertyName(
                    member, member.getSimpleName().toString());
            if (name.equals(fieldName)) return declared;
        }
        return null;
    }

    /** Runtime metadata resolves and validates the effective annotation for this argument. */
    private BindingValue compileCreatorOneOf(BindingValue owner, TypeMirror argType,
                                              OneOf annotation, int index,
                                              ExecutableElement method, GeneratedClass generated) {
        TypeMirror type = context.types.concrete(argType);
        BindingValue result = new BindingValue(owner.direction(), type, BindingValue.Kind.ONE_OF, false);
        result.helperName(names.newName(helperSuggestion(owner.direction(), type)));
        result.oneOfCreator(context.typeUtils.erasure(owner.type()).toString(), index);
        if (!compileOneOfRead(result, annotation, method, generated)) return null;
        helpers.add(result);
        return result;
    }

    /** Constructor parameters first, then settable properties buffered until creation. */
    private boolean compileCreatorPojo(BindingValue value, CreatorPlan creator,
                                       ExecutableElement method, GeneratedClass generated) {
        List<? extends VariableElement> args = creator.executable().getParameters();
        Map<String, Property> properties = context.properties.resolve(value.type());
        List<BindingProperty> compiled = new ArrayList<>();
        Map<String, String> claimed = new LinkedHashMap<>();
        Map<String, Boolean> consumed = new LinkedHashMap<>();

        for (int i = 0; i < args.size(); i++) {
            VariableElement arg = args.get(i);
            NodeProperty annotation = arg.getAnnotation(NodeProperty.class);
            String name = context.annotations.propertyName(arg, arg.getSimpleName().toString());
            String[] aliases = context.annotations.propertyAliases(arg);
            if (name.isEmpty() || name.matches("arg[0-9]+")
                    && context.annotations.explicitPropertyName(arg) == null) {
                error(method, generated, "Missing creator parameter name for " + value.type()
                        + " argument " + i + "; use @NodeProperty or @JsonProperty");
                return false;
            }
            if (!claimCreatorNames(value, method, generated, claimed, name, aliases)) return false;
            OneOf override = creatorOneOf(value.type(), arg, name);
            if (override != null && override.scope() != OneOf.Scope.CURRENT) {
                error(method, generated, "creator @OneOf supports only scope=CURRENT: "
                        + value.type() + "." + name);
                return false;
            }
            if (override != null && annotation != null
                    && (!NodeProperty.CODEC_NAME_UNSET.equals(annotation.codecName())
                    || !annotation.codecPattern().isEmpty())) {
                error(method, generated, "creator @OneOf cannot be combined with a value codec on "
                        + value.type() + "." + name);
                return false;
            }
            BindingValue child = override == null
                    ? compileValue(value.direction(), creator.parameterTypes().get(i),
                            method, generated,
                            annotation == null || NodeProperty.CODEC_NAME_UNSET.equals(annotation.codecName())
                                    ? null : annotation.codecName(),
                            annotation == null || annotation.codecPattern().isEmpty()
                                    ? null : annotation.codecPattern())
                    : compileCreatorOneOf(value, creator.parameterTypes().get(i), override,
                            i, method, generated);
            if (child == null) return false;
            compiled.add(new BindingProperty(name, aliases, child, i, args.size()));
            consumed.put(name, true);
        }

        int pending = 0;
        for (Property property : properties.values()) {
            if (consumed.containsKey(property.name()) || property.write() == null) continue;
            NodeProperty getter = property.read() == null ? null
                    : property.read().member().getAnnotation(NodeProperty.class);
            NodeProperty setter = property.write().member().getAnnotation(NodeProperty.class);
            boolean memberOneOf = hasMemberOneOf(property.read()) || hasMemberOneOf(property.write());
            String[] aliases = setter != null && setter.aliases().length != 0 ? setter.aliases()
                    : getter == null ? new String[0] : getter.aliases();
            if (!claimCreatorNames(value, method, generated, claimed, property.name(), aliases)) return false;
            if (conflictingCodecOptions(getter, setter)) {
                error(method, generated, "Conflicting @NodeProperty codecName/codecPattern on "
                        + value.type() + "." + property.name());
                return false;
            }
            BindingValue child = memberOneOf
                    ? compileMemberOneOf(value, property.name(), property.write().type(),
                            memberOneOf(property.read()) != null ? memberOneOf(property.read())
                                    : memberOneOf(property.write()), method, generated)
                    : compileValue(value.direction(), property.write().type(),
                            method, generated, selectedCodecName(getter, setter),
                            selectedCodecPattern(getter, setter));
            if (child == null) return false;
            compiled.add(new BindingProperty(property.name(), aliases,
                    property.write(), child, pending++));
        }

        value.creator(creator);
        value.properties(compiled);
        return true;
    }

    private boolean claimCreatorNames(BindingValue value, ExecutableElement method,
                                      GeneratedClass generated, Map<String, String> claimed,
                                      String name, String[] aliases) {
        for (String key : recordNames(name, aliases)) {
            if (key.isEmpty()) {
                error(method, generated, "Empty creator property name/alias on " + value.type());
                return false;
            }
            String previous = claimed.putIfAbsent(key, name);
            if (previous != null) {
                error(method, generated, "Duplicate compiled property name '" + key
                        + "' for " + previous + " and " + name);
                return false;
            }
        }
        return true;
    }

    private static String[] recordNames(String name, String[] aliases) {
        String[] names = new String[aliases.length + 1];
        names[0] = name;
        System.arraycopy(aliases, 0, names, 1, aliases.length);
        return names;
    }

    private static boolean conflictingCodecOptions(NodeProperty getter, NodeProperty setter) {
        if (getter == null || setter == null) return false;
        String leftName = NodeProperty.CODEC_NAME_UNSET.equals(getter.codecName())
                ? null : getter.codecName();
        String rightName = NodeProperty.CODEC_NAME_UNSET.equals(setter.codecName())
                ? null : setter.codecName();
        if (leftName != null && rightName != null && !leftName.equals(rightName)) return true;
        String leftPattern = getter.codecPattern().isEmpty() ? null : getter.codecPattern();
        String rightPattern = setter.codecPattern().isEmpty() ? null : setter.codecPattern();
        return leftPattern != null && rightPattern != null && !leftPattern.equals(rightPattern);
    }

    private static String selectedCodecName(NodeProperty getter, NodeProperty setter) {
        String fromGetter = getter == null || NodeProperty.CODEC_NAME_UNSET.equals(getter.codecName())
                ? null : getter.codecName();
        String fromSetter = setter == null || NodeProperty.CODEC_NAME_UNSET.equals(setter.codecName())
                ? null : setter.codecName();
        return fromSetter != null ? fromSetter : fromGetter;
    }

    private static String selectedCodecPattern(NodeProperty getter, NodeProperty setter) {
        String fromGetter = getter == null || getter.codecPattern().isEmpty()
                ? null : getter.codecPattern();
        String fromSetter = setter == null || setter.codecPattern().isEmpty()
                ? null : setter.codecPattern();
        return fromSetter != null ? fromSetter : fromGetter;
    }

    private boolean hasMemberOneOf(PropertyAccess access) {
        return memberOneOf(access) != null;
    }

    private boolean compileElement(
            BindingValue value,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeMirror elementType;

        if (value.kind() == BindingValue.Kind.LIST) {
            elementType =
                    value.direction() ==
                            BindingPlan.Direction.READ_FROM
                            ? context.types.listWriteElementType(
                                    value.type())
                            : context.types.listReadElementType(
                                    value.type());
        } else {
            elementType =
                    value.direction() ==
                            BindingPlan.Direction.READ_FROM
                            ? context.types.setWriteElementType(
                                    value.type())
                            : context.types.setReadElementType(
                                    value.type());
        }

        BindingValue elementValue =
                compileValue(
                        value.direction(),
                        elementType,
                        method,
                        generated);

        if (elementValue == null) {
            return false;
        }

        value.elementValue(elementValue);
        return true;
    }

    private boolean compileMap(
            BindingValue value,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeMirror mapValueType =
                value.direction() ==
                        BindingPlan.Direction.READ_FROM
                        ? context.types.mapWriteValueType(
                                value.type())
                        : context.types.mapReadValueType(
                                value.type());

        BindingValue mapValue =
                compileValue(
                        value.direction(),
                        mapValueType,
                        method,
                        generated);

        if (mapValue == null) {
            return false;
        }

        value.mapValue(mapValue);
        return true;
    }

    private boolean directList(
            BindingPlan.Direction direction,
            TypeMirror type) {

        if (direction == BindingPlan.Direction.READ_FROM) {
            TypeMirror elementType =
                    context.types.listWriteElementType(type);

            return elementType != null &&
                    canAssignImplementation(
                            arrayListType,
                            type);
        }

        return context.types.listReadElementType(type) != null;
    }

    private boolean directSet(
            BindingPlan.Direction direction,
            TypeMirror type) {

        if (direction == BindingPlan.Direction.READ_FROM) {
            TypeMirror elementType =
                    context.types.setWriteElementType(type);

            return elementType != null &&
                    canAssignImplementation(
                            linkedHashSetType,
                            type);
        }

        return context.types.setReadElementType(type) != null;
    }

    private boolean directMap(
            BindingPlan.Direction direction,
            TypeMirror type) {

        if (direction == BindingPlan.Direction.READ_FROM) {
            TypeMirror keyType =
                    context.types.mapWriteKeyType(type);

            TypeMirror valueType =
                    context.types.mapWriteValueType(type);

            return keyType != null &&
                    valueType != null &&
                    context.typeUtils.isAssignable(
                            stringType,
                            keyType) &&
                    canAssignImplementation(
                            linkedHashMapType,
                            type);
        }

        TypeMirror keyType =
                context.types.mapReadKeyType(type);

        TypeMirror valueType =
                context.types.mapReadValueType(type);

        return keyType != null &&
                valueType != null &&
                context.types.isSameErasure(
                        keyType,
                        stringType);
    }

    private boolean canAssignImplementation(
            TypeMirror implementation,
            TypeMirror target) {

        return context.typeUtils.isAssignable(
                context.typeUtils.erasure(
                        implementation),
                context.typeUtils.erasure(
                        target));
    }

    private BindingValue.Kind unsupported(
            ExecutableElement method,
            GeneratedClass generated,
            TypeMirror type,
            String reason) {

        error(
                method,
                generated,
                "Cannot generate direct " +
                        "binding for " + type +
                        ": " + reason);

        return null;
    }

    private void error(
            ExecutableElement method,
            GeneratedClass generated,
            String message) {

        context.error(method, message);
        generated.invalidate();
    }

    private String helperSuggestion(
            BindingPlan.Direction direction,
            TypeMirror type) {

        TypeElement element =
                context.types.typeElement(type);

        String simple =
                element == null
                        ? "Value"
                        : element.getSimpleName()
                                .toString();

        return direction ==
                BindingPlan.Direction.READ_FROM
                ? "_read" + simple
                : "_write" + simple;
    }

    private TypeMirror requiredType(String name) {
        TypeElement element =
                context.elements.getTypeElement(name);

        if (element == null) {
            throw new IllegalStateException(
                    "Required type is not available: " +
                            name);
        }

        return element.asType();
    }

    static final class CompiledMethod {

        private final BindingPlan plan;
        private final BindingValue value;

        CompiledMethod(
                BindingPlan plan,
                BindingValue value) {

            this.plan = plan;
            this.value = value;
        }

        BindingPlan plan() {
            return plan;
        }

        BindingValue value() {
            return value;
        }
    }
}
