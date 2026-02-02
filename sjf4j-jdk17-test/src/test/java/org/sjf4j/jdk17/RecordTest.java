package org.sjf4j.jdk17;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

public class RecordTest {

    public record Person(String name, int age) {}

    @Test
    public void testRecord1() {

    }
}
