package org.sjf4j.util;

@FunctionalInterface
public interface IdxPredicate<T> {
    boolean test(int idx, T value);
}
