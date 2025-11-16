package org.sjf4j.facades;


import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.facades.jackson.JacksonReader;
import org.sjf4j.facades.jackson.JacksonWriter;

public interface JsonFacade<R extends FacadeReader, W extends FacadeWriter> extends StreamingFacade<R, W> {
    // Nothing
}
