package org.sjf4j.testbench.schema.official;

import org.sjf4j.JsonObject;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.BinderFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads upstream schema documents, instances and annotations without rounding numeric literals. */
final class OfficialFixtures {
    private static final Binder<?, ?> JSON = BinderFactory.jsonProvider().create(NodeConfig.EXACT_NUMBERS);

    private OfficialFixtures() {}

    static JsonObject readObject(Path file) throws IOException {
        return (JsonObject) JSON.readNode(Files.readAllBytes(file), JsonObject.class);
    }

    static Object readNode(Path file) throws IOException {
        return JSON.readNode(Files.readAllBytes(file), Object.class);
    }
}
