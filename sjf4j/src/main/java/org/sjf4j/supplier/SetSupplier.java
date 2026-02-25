package org.sjf4j.supplier;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;


/**
 * Factory for creating Set instances used by JsonArray set conversions.
 */
public interface SetSupplier {

    /**
     * Creates an empty set.
     */
    <T> Set<T> create();

    /**
     * Creates an empty set with initial capacity hint.
     */
    <T> Set<T> create(int initialCapacity);

    /**
     * Creates a set initialized from target elements.
     */
    <T> Set<T> create(Collection<T> target);


    /// Build-in List Suppliers

    SetSupplier HashSetSupplier = new SetSupplier() {
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Set<T> create() {
            return new HashSet<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Set<T> create(int initialCapacity) {
            return new HashSet<>(initialCapacity);
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Set<T> create(Collection<T> target) {
            return new HashSet<>(target);
        }
    };

    SetSupplier LinkedHashSetSupplier = new SetSupplier() {
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Set<T> create() {
            return new LinkedHashSet<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Set<T> create(int initialCapacity) {
            return new LinkedHashSet<>(initialCapacity);
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> Set<T> create(Collection<T> target) {
            return new LinkedHashSet<>(target);
        }
    };

}
