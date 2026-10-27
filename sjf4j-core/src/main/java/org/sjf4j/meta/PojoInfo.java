package org.sjf4j.meta;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.binding.BackendCache;
import org.sjf4j.binding.PropertyParser;
import org.sjf4j.binding.PropertyGenerator;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Cached binding metadata for an object type.
 *
 * <p>It describes construction, property access, naming, dynamic JSON-object
 * behavior, and whether the framework reader or writer is required.</p>
 */
public class PojoInfo {
    public final Class<?> clazz;
    public final CreatorInfo creatorInfo;

    public final boolean readDynamic;
    public final boolean writeDynamic;
    public final boolean isJojo;
    public final boolean isJajo;
    public final boolean hasParentScopeOneOf;

    public final Map<String, PropertyInfo> propertyLookup;

    public final PropertyInfo[] settableProperties;
    public final PropertyParser[] propertyParsers;

    public final PropertyInfo[] gettableProperties;
    public final PropertyGenerator[] propertyGenerators;

    /** Name matching for argument-based creators and parent-scope OneOf. */
    public final String[] creatorMatchNames;
    public final int[] creatorMatchArgs;
    public final PropertyInfo[] creatorMatchProperties;

    public final BackendCache[] backendCache = new BackendCache[Backend.values().length];

    /**
     * Creates object binding metadata.
     */
    public PojoInfo(Class<?> clazz, CreatorInfo creatorInfo,
                    boolean readDynamic,
                    boolean writeDynamic,
                    Map<String, PropertyInfo> propertyLookup,
                    PropertyInfo[] settableProperties,
                    PropertyParser[] propertyParsers,
                    PropertyInfo[] gettableProperties,
                    PropertyGenerator[] propertyGenerators) {
        this.clazz = clazz;
        this.creatorInfo = creatorInfo;
        this.readDynamic = readDynamic;
        this.writeDynamic = writeDynamic;
        this.isJojo = JsonObject.class.isAssignableFrom(clazz);
        this.isJajo = JsonArray.class.isAssignableFrom(clazz);

        boolean hasParentScopeOneOf = false;
        for (PropertyInfo propertyInfo : gettableProperties) {
            if (propertyInfo.oneOfInfo != null && propertyInfo.oneOfInfo.scope == OneOf.Scope.PARENT) {
                hasParentScopeOneOf = true;
                break;
            }
        }
        this.hasParentScopeOneOf = hasParentScopeOneOf;
        this.propertyLookup = propertyLookup;
        this.gettableProperties = gettableProperties;
        this.settableProperties = settableProperties;
        this.propertyParsers = propertyParsers;
        this.propertyGenerators = propertyGenerators;

        if (hasParentScopeOneOf || !creatorInfo.hasNoArgsCreator()) {
            // Creator names and aliases take priority over declared properties.
            // Precompute index-aligned matcher names, argument indices, and properties.
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            if (creatorInfo.argNames != null) {
                Collections.addAll(keys, creatorInfo.argNames);
            }
            if (creatorInfo.aliasMap != null) {
                keys.addAll(creatorInfo.aliasMap.keySet());
            }
            keys.addAll(propertyLookup.keySet());

            this.creatorMatchNames = keys.toArray(new String[0]);
            this.creatorMatchArgs = new int[creatorMatchNames.length];
            this.creatorMatchProperties = new PropertyInfo[creatorMatchNames.length];
            for (int i = 0; i < creatorMatchNames.length; i++) {
                String name = creatorMatchNames[i];
                int arg = creatorInfo.getArgIndexOrAlias(name);
                creatorMatchArgs[i] = arg;
                if (arg < 0) {
                    creatorMatchProperties[i] = propertyLookup.get(name);
                }
            }
        } else {
            this.creatorMatchNames = null;
            this.creatorMatchArgs = null;
            this.creatorMatchProperties = null;
        }
    }


    /**
     * Returns a read-only key view of declared readable property names.
     * Alias names are excluded.
     */
    public Set<String> gettablePropertyNameSet() {
        if (gettableProperties.length == 0) {
            return Collections.emptySet();
        }

        return new AbstractSet<String>() {
            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    private int index;

                    @Override
                    public boolean hasNext() {
                        return index < gettableProperties.length;
                    }

                    @Override
                    public String next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        return gettableProperties[index++].name;
                    }
                };
            }

            @Override
            public int size() {
                return gettableProperties.length;
            }

            @Override
            public boolean contains(Object key) {
                if (!(key instanceof String)) {
                    return false;
                }

                PropertyInfo property = getPropertyNoAlias((String) key);
                return property != null && property.gettable;
            }
        };
    }

    /**
     * Returns a declared property only when {@code key} is its primary name;
     * aliases accepted during binding are excluded from structural access.
     */
    public PropertyInfo getPropertyNoAlias(String key) {
        PropertyInfo propertyInfo = propertyLookup.get(key);
        if (propertyInfo != null && (propertyInfo.alias.length == 0 || propertyInfo.name.equals(key))) {
            return propertyInfo;
        }
        return null;
    }

    /** Returns the lazily created, reusable cache for the given backend. */
    public BackendCache backendCache(Backend backend) {
        int index = backend.ordinal();
        BackendCache cache = backendCache[index];
        if (cache == null) {
            cache = new BackendCache();
            backendCache[index] = cache;
        }
        return cache;
    }

}
