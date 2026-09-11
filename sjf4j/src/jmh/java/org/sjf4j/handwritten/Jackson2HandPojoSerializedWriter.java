package org.sjf4j.handwritten;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/** Direct Jackson2 streaming writer using pre-serialized field names for {@link User}. */
public final class Jackson2HandPojoSerializedWriter {

    private Jackson2HandPojoSerializedWriter() {
    }

    public static void writeUser(JsonGenerator generator, User user) throws IOException {
        Jackson2HandLargeWriter.writeUser(generator, user);
    }
}
