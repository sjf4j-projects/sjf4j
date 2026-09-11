package org.sjf4j.handwritten;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/** Direct Gson streaming writer for {@link User}. */
public final class GsonHandPojoWriter {

    private GsonHandPojoWriter() {
    }

    public static void writeUser(JsonWriter writer, User user) throws IOException {
        GsonHandLargeWriter.writeUser(writer, user);
    }
}
