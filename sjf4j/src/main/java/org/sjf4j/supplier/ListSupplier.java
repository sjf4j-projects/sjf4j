package org.sjf4j.supplier;

import org.sjf4j.Sjf4jConfig;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public interface ListSupplier {

    <T> List<T> create();

    <T> List<T> create(int initialCapacity);

    <T> List<T> create(Collection<T> target);

    default <T> List<T> create(T[] target) {
        List<T> list = create(target.length);
        Collections.addAll(list, target);
        return list;
    }


    /// Build-in List Suppliers

    ListSupplier ArrayListSupplier = new ListSupplier() {
        @Override
        public <T> List<T> create() {
            return new ArrayList<>();
        }
        @Override
        public <T> List<T> create(int initialCapacity) {
            return new ArrayList<>(initialCapacity);
        }
        @Override
        public <T> List<T> create(Collection<T> target) {
            return new ArrayList<>(target);
        }
    };

    ListSupplier LinkedListSupplier = new ListSupplier() {
        @Override
        public <T> List<T> create() {
            return new LinkedList<>();
        }
        @Override
        public <T> List<T> create(int initialCapacity) {
            return new LinkedList<>();
        }
        @Override
        public <T> List<T> create(Collection<T> target) {
            return new LinkedList<>(target);
        }
    };

    ListSupplier CopyOnWriteArrayListSupplier = new ListSupplier() {
        @Override
        public <T> List<T> create() {
            return new CopyOnWriteArrayList<>();
        }
        @Override
        public <T> List<T> create(int initialCapacity) {
            return new CopyOnWriteArrayList<>();
        }
        @Override
        public <T> List<T> create(Collection<T> target) {
            return new CopyOnWriteArrayList<>(target);
        }
    };

}