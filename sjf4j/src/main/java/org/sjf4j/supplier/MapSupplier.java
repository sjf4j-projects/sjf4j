package org.sjf4j.supplier;

import org.sjf4j.exception.JsonException;

import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Factory for creating Map instances used by JsonObject.
 */
public interface MapSupplier {

    /**
     * Creates a map instance for the requested map type.
     */
    static <T> Map<String, T> create(Class<?> mapType) {
        return create(mapType, 0);
    }

    /**
     * Creates a map instance for the requested map type with an initial capacity
     * hint when the implementation supports it.
     */
    static <T> Map<String, T> create(Class<?> mapType, int initialCapacity) {
        if (mapType == null || mapType == Object.class || mapType == Map.class || mapType == AbstractMap.class) {
            return initialCapacity > 0 ? new LinkedHashMap<>(initialCapacity) : new LinkedHashMap<>();
        }
        if (mapType == HashMap.class) {
            return initialCapacity > 0 ? new HashMap<>(initialCapacity) : new HashMap<>();
        }
        if (mapType == LinkedHashMap.class) {
            return initialCapacity > 0 ? new LinkedHashMap<>(initialCapacity) : new LinkedHashMap<>();
        }
        if (mapType == TreeMap.class || mapType == SortedMap.class || mapType == NavigableMap.class) {
            return new TreeMap<>();
        }
        if (mapType == ConcurrentHashMap.class || mapType == ConcurrentMap.class) {
            return initialCapacity > 0 ? new ConcurrentHashMap<>(initialCapacity) : new ConcurrentHashMap<>();
        }
        if (mapType.isInterface() || Modifier.isAbstract(mapType.getModifiers())) {
            return initialCapacity > 0 ? new LinkedHashMap<>(initialCapacity) : new LinkedHashMap<>();
        }
        throw new JsonException("Unsupported Map target type '" + mapType.getName() + "'");
    }

    /**
     * Creates a map instance for the requested map type and copies entries into it.
     */
    static <T> Map<String, T> create(Class<?> mapClazz, Map<String, T> source) {
        if (source == null) return null;
        Map<String, T> map = create(mapClazz, source.size());
        map.putAll(source);
        return map;
    }


//    /**
//     * Creates an empty map.
//     */
//    <T> Map<String, T> create();
//
//    /**
//     * Creates an empty map with initial capacity hint.
//     */
//    <T> Map<String, T> create(int initialCapacity);
//
//    /**
//     * Creates a map initialized from target entries.
//     */
//    <T> Map<String, T> create(Map<String, T> target);
//
//
//    /// Build-in Map Suppliers
//
//    MapSupplier HashMapSupplier = new MapSupplier() {
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create() {
//            return new HashMap<>();
//        }
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create(int initialCapacity) {
//            return new HashMap<>(initialCapacity);
//        }
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create(Map<String, T> target) {
//            return new HashMap<>(target);
//        }
//    };
//
//    MapSupplier LinkedHashMapSupplier = new MapSupplier() {
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create() {
//            return new LinkedHashMap<>();
//        }
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create(int initialCapacity) {
//            return new LinkedHashMap<>(initialCapacity);
//        }
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create(Map<String, T> target) {
//            return new LinkedHashMap<>(target);
//        }
//    };
//
//    MapSupplier TreeMapSupplier = new MapSupplier() {
//
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create() {
//            return new TreeMap<>();
//        }
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create(int initialCapacity) {
//            return new TreeMap<>();
//        }
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create(Map<String, T> target) {
//            return new TreeMap<>(target);
//        }
//    };
//
//    MapSupplier ConcurrentHashMapSupplier = new MapSupplier() {
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create() {
//            return new ConcurrentHashMap<>();
//        }
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create(int initialCapacity) {
//            return new ConcurrentHashMap<>(initialCapacity);
//        }
//        /**
//         * Creates container instance from this supplier.
//         */
//        @Override
//        public <T> Map<String, T> create(Map<String, T> target) {
//            return new ConcurrentHashMap<>(target);
//        }
//    };

}
