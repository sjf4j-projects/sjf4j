package org.sjf4j.supplier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Factory for creating Map instances used by JsonObject.
 */
public interface MapSupplier {

    /**
     * Creates an empty map.
     */
    <T> Map<String, T> create();

    /**
     * Creates an empty map with initial capacity hint.
     */
    <T> Map<String, T> create(int initialCapacity);

    /**
     * Creates a map initialized from target entries.
     */
    <T> Map<String, T> create(Map<String, T> target);


    /// Build-in Map Suppliers

    MapSupplier HashMapSupplier = new MapSupplier() {
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create() {
            return new HashMap<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create(int initialCapacity) {
            return new HashMap<>(initialCapacity);
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create(Map<String, T> target) {
            return new HashMap<>(target);
        }
    };

    MapSupplier LinkedHashMapSupplier = new MapSupplier() {
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create() {
            return new LinkedHashMap<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create(int initialCapacity) {
            return new LinkedHashMap<>(initialCapacity);
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create(Map<String, T> target) {
            return new LinkedHashMap<>(target);
        }
    };

    MapSupplier TreeMapSupplier = new MapSupplier() {

        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create() {
            return new TreeMap<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create(int initialCapacity) {
            return new TreeMap<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create(Map<String, T> target) {
            return new TreeMap<>(target);
        }
    };

    MapSupplier ConcurrentHashMapSupplier = new MapSupplier() {
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create() {
            return new ConcurrentHashMap<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create(int initialCapacity) {
            return new ConcurrentHashMap<>(initialCapacity);
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Map<String, T> create(Map<String, T> target) {
            return new ConcurrentHashMap<>(target);
        }
    };

}
