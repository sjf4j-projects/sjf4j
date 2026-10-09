package org.sjf4j.node;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.binding.BackendCache;
import org.sjf4j.binding.PropertyReader;
import org.sjf4j.binding.PropertyWriter;

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

    public final PropertyInfo[] writableProperties;
    public final PropertyReader[] propertyReaders;

    public final PropertyInfo[] readableProperties;
    public final PropertyWriter[] propertyWriters;

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
                    PropertyInfo[] writableProperties,
                    PropertyReader[] propertyReaders,
                    PropertyInfo[] readableProperties,
                    PropertyWriter[] propertyWriters) {
        this.clazz = clazz;
        this.creatorInfo = creatorInfo;
        this.readDynamic = readDynamic;
        this.writeDynamic = writeDynamic;
        this.isJojo = JsonObject.class.isAssignableFrom(clazz);
        this.isJajo = JsonArray.class.isAssignableFrom(clazz);

        boolean hasParentScopeOneOf = false;
        for (PropertyInfo propertyInfo : readableProperties) {
            if (propertyInfo.oneOfInfo != null && propertyInfo.oneOfInfo.scope == OneOf.Scope.PARENT) {
                hasParentScopeOneOf = true;
                break;
            }
        }
        this.hasParentScopeOneOf = hasParentScopeOneOf;
        this.propertyLookup = propertyLookup;
        this.readableProperties = readableProperties;
        this.writableProperties = writableProperties;
        this.propertyReaders = propertyReaders;
        this.propertyWriters = propertyWriters;

        if (hasParentScopeOneOf || !creatorInfo.hasNoArgsCreator()) {
            // The creator has priority over declared properties, including
            // aliases. Compute that resolution once, not per streamed field.
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


    public Set<String> readablePropertyNameSet() {
        if (readableProperties.length == 0) {
            return Collections.emptySet();
        }

        return new AbstractSet<String>() {
            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    private int index;

                    @Override
                    public boolean hasNext() {
                        return index < readableProperties.length;
                    }

                    @Override
                    public String next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        return readableProperties[index++].name;
                    }
                };
            }

            @Override
            public int size() {
                return readableProperties.length;
            }

            @Override
            public boolean contains(Object key) {
                if (!(key instanceof String)) {
                    return false;
                }

                PropertyInfo property = getPropertyNoAlias((String) key);
                return property != null && property.readable;
            }
        };
    }

    public PropertyInfo getPropertyNoAlias(String key) {
        PropertyInfo propertyInfo = propertyLookup.get(key);
        if (propertyInfo != null && (propertyInfo.alias.length == 0 || propertyInfo.name.equals(key))) {
            return propertyInfo;
        }
        return null;
    }

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
