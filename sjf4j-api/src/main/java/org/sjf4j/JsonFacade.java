package org.sjf4j;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public interface JsonFacade {

    JsonObject readObject(Reader input);

    JsonArray readArray(Reader input);

    void write(Writer output, JsonObject jo);

    void write(Writer output, JsonArray ja);

}
