package org.sjf4j.testbench.handwritten;

import com.google.gson.stream.JsonWriter;
import org.sjf4j.testbench.handwritten.GsonHandLargeWriter;
import org.sjf4j.testbench.handwritten.User;

import java.io.IOException;

/** Direct Gson streaming writer for {@link User}. */
public final class GsonHandPojoWriter {

    private GsonHandPojoWriter() {
    }

    public static void writeUser(JsonWriter writer, User user) throws IOException {
        GsonHandLargeWriter.writeUser(writer, user);
    }
}
