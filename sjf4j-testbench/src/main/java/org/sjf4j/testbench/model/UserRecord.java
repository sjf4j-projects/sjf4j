package org.sjf4j.testbench.model;

/** Small immutable user for record binding benchmarks. */
public record UserRecord(long id, int age, String username, boolean active) {
}
