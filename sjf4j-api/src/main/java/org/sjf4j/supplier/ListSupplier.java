package org.sjf4j.supplier;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@FunctionalInterface
public interface ListSupplier {

    <T> List<T> create();


    /// Build-in List Suppliers

    ListSupplier ArrayListSupplier = ArrayList::new;

    ListSupplier LinkedListSupplier = LinkedList::new;

    ListSupplier CopyOnWriteArrayListSupplier = CopyOnWriteArrayList::new;

}