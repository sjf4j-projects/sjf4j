package org.sjf4j.supplier;

import org.sjf4j.Sjf4jConfig;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Factory for creating List instances used by JsonArray.
 */
public interface ListSupplier {

    /**
     * Creates an empty list.
     */
    <T> List<T> create();

    /**
     * Creates an empty list with initial capacity hint.
     */
    <T> List<T> create(int initialCapacity);

    /**
     * Creates a list initialized from target elements.
     */
    <T> List<T> create(Collection<T> target);

    /**
     * Creates a list initialized from target array.
     */
    default <T> List<T> create(T[] target) {
        List<T> list = create(target.length);
        Collections.addAll(list, target);
        return list;
    }


    /// Build-in List Suppliers

    ListSupplier ArrayListSupplier = new ListSupplier() {
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create() {
            return new ArrayList<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create(int initialCapacity) {
            return new ArrayList<>(initialCapacity);
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create(Collection<T> target) {
            return new ArrayList<>(target);
        }
    };

    ListSupplier LinkedListSupplier = new ListSupplier() {
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create() {
            return new LinkedList<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create(int initialCapacity) {
            return new LinkedList<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create(Collection<T> target) {
            return new LinkedList<>(target);
        }
    };

    ListSupplier CopyOnWriteArrayListSupplier = new ListSupplier() {
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create() {
            return new CopyOnWriteArrayList<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create(int initialCapacity) {
            return new CopyOnWriteArrayList<>();
        }
        /**
         * Creates container instance from this supplier.
         */
        @Override
        public <T> List<T> create(Collection<T> target) {
            return new CopyOnWriteArrayList<>(target);
        }
    };

}
