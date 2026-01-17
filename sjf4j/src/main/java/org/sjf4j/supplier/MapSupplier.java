package org.sjf4j.supplier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@FunctionalInterface
public interface MapSupplier {

    <T> Map<String, T> create();

    default <T> Map<String, T> create(Map<String, T> map) {
        Map<String, T> newMap = create();
        newMap.putAll(map);
        return newMap;
    }

    /// Build-in Map Suppliers

    MapSupplier HashMapSupplier = HashMap::new;

    MapSupplier LinkedHashMapSupplier = LinkedHashMap::new;

    MapSupplier TreeMapSupplier = TreeMap::new;

    MapSupplier ConcurrentHashMapSupplier = ConcurrentHashMap::new;

}
