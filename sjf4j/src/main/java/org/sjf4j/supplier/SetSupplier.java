package org.sjf4j.supplier;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;


public interface SetSupplier {

    <T> Set<T> create();

    <T> Set<T> create(int initialCapacity);

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