package org.sjf4j.util;

@FunctionalInterface
public interface IdxConsumer<T> {
    void accept(int index, T value);
}
