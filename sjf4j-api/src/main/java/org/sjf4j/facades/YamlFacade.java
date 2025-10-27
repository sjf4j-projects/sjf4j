package org.sjf4j.facades;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.io.Reader;
import java.io.Writer;

public interface YamlFacade {

    JsonObject readObject(Reader input);

    JsonArray readArray(Reader input);

    void writeObject(Writer output, JsonObject jo);

    void writeArray(Writer output, JsonArray ja);

}
