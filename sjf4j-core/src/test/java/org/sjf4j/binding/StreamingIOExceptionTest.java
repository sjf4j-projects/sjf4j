package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamingIOExceptionTest {

    private final SimpleJsonBinder binder = new SimpleJsonBinder(RuntimeContext.EMPTY);

    @Test
    void readPropagatesIOException() throws IOException {
        IOException failure = new IOException("read failure");
        StreamingReader reader = binder.createReader(failingReader(failure));
        assertSame(failure, assertThrows(IOException.class,
                () -> StreamingIO.readNode(reader, String.class, RuntimeContext.EMPTY)));
    }

    @Test
    void writePropagatesIOException() throws IOException {
        IOException failure = new IOException("write failure");
        StreamingWriter writer = binder.createWriter(failingWriter(failure));
        assertSame(failure, assertThrows(IOException.class,
                () -> StreamingIO.writeNode(writer, "value", RuntimeContext.EMPTY)));
    }

    @Test
    void readWrapsErrorAsBindingException() throws IOException {
        AssertionError failure = new AssertionError("read error");
        StreamingReader reader = binder.createReader(failingReader(failure));
        BindingException thrown = assertThrows(BindingException.class,
                () -> StreamingIO.readNode(reader, String.class, RuntimeContext.EMPTY));
        assertSame(failure, thrown.getCause());
    }

    @Test
    void writeWrapsErrorAsBindingException() throws IOException {
        AssertionError failure = new AssertionError("write error");
        StreamingWriter writer = binder.createWriter(failingWriter(failure));
        BindingException thrown = assertThrows(BindingException.class,
                () -> StreamingIO.writeNode(writer, "value", RuntimeContext.EMPTY));
        assertSame(failure, thrown.getCause());
    }

    private static Reader failingReader(Throwable failure) {
        return new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                if (failure instanceof IOException) throw (IOException) failure;
                if (failure instanceof Error) throw (Error) failure;
                throw new AssertionError(failure);
            }

            @Override
            public void close() {
            }
        };
    }

    private static Writer failingWriter(Throwable failure) {
        return new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) throws IOException {
                if (failure instanceof IOException) throw (IOException) failure;
                if (failure instanceof Error) throw (Error) failure;
                throw new AssertionError(failure);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }
}
