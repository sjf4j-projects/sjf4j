package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NamingStrategy;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.PropertyStrategy;
import org.sjf4j.binding.FieldWriter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cached binding metadata for an object type.
 *
 * <p>It describes construction, property access, naming, dynamic JSON-object
 * behavior, and whether the framework reader or writer is required.</p>
 */
public class PojoInfo {
    public final Class<?> clazz;
    public final CreatorInfo creatorInfo;
    public final NamingStrategy namingStrategy;
    public final PropertyStrategy propertyStrategy;
    public final boolean readDynamic;
    public final boolean writeDynamic;
    public final Map<String, FieldInfo> properties;
    public final int propertyCount;
    public final Map<String, FieldInfo> readableProperties;
    public final int readablePropertyCount;
    public final Map<String, FieldInfo> aliasProperties;
    public final boolean isJojo;
    public final boolean isJajo;
    public final boolean hasParentScopeOneOf;
    public final boolean hasExplicitBinding;
    public final boolean hasCreatorBinding;
    public final boolean hasNonPublicFields;
    public final boolean hasNonPublicReaderGap;
    public final boolean hasNonPublicWriterGap;
    public final boolean hasPropertyCodecNameBinding;
    public final boolean requiresPojoReader;
    public final boolean requiresPojoWriter;

    public final String[] fieldNames;
    public final FieldWriter[] fieldWriters;

    /**
     * Creates object binding metadata.
     */
    public PojoInfo(Class<?> clazz, CreatorInfo creatorInfo,
                    NamingStrategy namingStrategy,
                    PropertyStrategy propertyStrategy,
                    boolean readDynamic,
                    boolean writeDynamic,
                    Map<String, FieldInfo> properties,
                    Map<String, FieldInfo> aliasProperties,
                    boolean hasExplicitBinding,
                    boolean hasNonPublicFields,
                    boolean hasNonPublicReaderGap,
                    boolean hasNonPublicWriterGap,
                    String[] fieldNames,
                    FieldWriter[] fieldWriters) {
        this.clazz = clazz;
        this.creatorInfo = creatorInfo;
        this.namingStrategy = namingStrategy;
        this.propertyStrategy = propertyStrategy;
        this.readDynamic = readDynamic;
        this.writeDynamic = writeDynamic;
        this.properties = properties;
        this.propertyCount = properties.size();
        Map<String, FieldInfo> readableProperties = null;
        for (Map.Entry<String, FieldInfo> entry : properties.entrySet()) {
            if (!entry.getValue().hasGetter()) {
                continue;
            }
            if (readableProperties == null) {
                readableProperties = new LinkedHashMap<>();
            }
            readableProperties.put(entry.getKey(), entry.getValue());
        }
        this.readableProperties = readableProperties == null ? Collections.emptyMap() : readableProperties;
        this.readablePropertyCount = this.readableProperties.size();
        this.aliasProperties = aliasProperties;
        this.isJojo = JsonObject.class.isAssignableFrom(clazz);
        this.isJajo = JsonArray.class.isAssignableFrom(clazz);
        boolean hasParentScopeOneOf = false;
        for (FieldInfo fi : properties.values()) {
            OneOfInfo aoi = fi.oneOfInfo;
            if (aoi != null && aoi.scope == OneOf.Scope.PARENT) {
                hasParentScopeOneOf = true;
                break;
            }
        }
        this.hasParentScopeOneOf = hasParentScopeOneOf;
        this.hasExplicitBinding = hasExplicitBinding;
        this.hasCreatorBinding = creatorInfo != null && creatorInfo.argsCreator != null;
        this.hasNonPublicFields = hasNonPublicFields;
        this.hasNonPublicReaderGap = hasNonPublicReaderGap;
        this.hasNonPublicWriterGap = hasNonPublicWriterGap;
        boolean hasPropertyCodecNameBinding = false;
        for (FieldInfo fi : properties.values()) {
            if (fi.valueInfo != null) {
                hasPropertyCodecNameBinding = true;
                break;
            }
        }
        this.hasPropertyCodecNameBinding = hasPropertyCodecNameBinding;
        boolean hasTypeOwnedBinding = namingStrategy != null || propertyStrategy != PropertyStrategy.BEAN_FIELD;
        boolean hasCustomDynamicReader = this.isJojo && !readDynamic;
        boolean hasCustomDynamicWriter = this.isJojo && !writeDynamic;
        this.requiresPojoReader = hasTypeOwnedBinding || hasParentScopeOneOf
                || hasExplicitBinding || this.hasCreatorBinding
                || (creatorInfo != null && creatorInfo.hasCodecNameBinding)
                || hasNonPublicFields || hasNonPublicReaderGap || hasCustomDynamicReader || hasPropertyCodecNameBinding;
        this.requiresPojoWriter = hasTypeOwnedBinding || hasExplicitBinding || hasNonPublicFields || hasNonPublicWriterGap
                || hasCustomDynamicWriter || hasPropertyCodecNameBinding;

        this.fieldNames = fieldNames;
        this.fieldWriters = fieldWriters;
    }

}
