package org.sjf4j.backend.gson.binding;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;


public final class GsonBinder extends JsonBinder<GsonReader, GsonWriter> {

    private final Gson gson;

    public GsonBinder() {
        this(new Gson(), StreamingContext.EMPTY);
    }

    public GsonBinder(Gson gson) {
        this(gson, StreamingContext.EMPTY);
    }

    public GsonBinder(Gson gson, StreamingContext context) {
        super(context);
        this.gson = Asserts.notNull(gson, "gson");
    }

    @Override
    public GsonReader createReader(Reader input) throws IOException {
        Asserts.notNull(input, "input");
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public GsonWriter createWriter(Writer output) throws IOException {
        Asserts.notNull(output, "output");
        JsonWriter writer = gson.newJsonWriter(output);
        writer.setSerializeNulls(true);
        return new GsonWriter(this, writer);
    }

}
