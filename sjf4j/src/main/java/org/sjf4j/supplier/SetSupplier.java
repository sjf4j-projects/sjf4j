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
        @Override
        public <T> Set<T> create() {
            return new HashSet<>();
        }
        @Override
        public <T> Set<T> create(int initialCapacity) {
            return new HashSet<>(initialCapacity);
        }
        @Override
        public <T> Set<T> create(Collection<T> target) {
            return new HashSet<>(target);
        }
    };

    SetSupplier LinkedHashSetSupplier = new SetSupplier() {
        @Override
        public <T> Set<T> create() {
            return new LinkedHashSet<>();
        }
        @Override
        public <T> Set<T> create(int initialCapacity) {
            return new LinkedHashSet<>(initialCapacity);
        }
        @Override
        public <T> Set<T> create(Collection<T> target) {
            return new LinkedHashSet<>(target);
        }
    };

}
