package org.sjf4j.testbench.nativeimage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.sjf4j.testbench.model.fabien.Clients;
import org.sjf4j.testbench.model.fabien.Users;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates canonical input on the host JVM, outside the native executable.
 * Using Jackson here does not introduce Jackson into a different backend's image.
 */
public final class NativeCompatibilityFixtures {
    private NativeCompatibilityFixtures() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output directory");
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);

        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Files.write(directory.resolve("users.json"), json.writeValueAsBytes(Users.sample()));
        Files.write(directory.resolve("clients.json"), json.writeValueAsBytes(Clients.sample()));
    }
}
