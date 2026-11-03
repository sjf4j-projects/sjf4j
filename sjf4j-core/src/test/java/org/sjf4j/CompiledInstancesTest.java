package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.StreamingGenerator;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.Reader;
import java.io.Writer;

import static org.junit.jupiter.api.Assertions.*;

class CompiledInstancesTest {

    public interface BackendCase {
        BaseTestBinder backend();
    }

    /** Uses the same nested-name convention as annotation-processor generated implementations. */
    public static final class BackendCase_Impl implements BackendCase {
        private final BaseTestBinder binder;

        public BackendCase_Impl(RuntimeContext context) {
            this.binder = null;
        }

        public BackendCase_Impl(BaseTestBinder binder) {
            this.binder = binder;
        }

        @Override
        public BaseTestBinder backend() {
            return binder;
        }
    }

    public abstract static class BaseTestBinder extends Binder<StreamingParser, StreamingGenerator> {
        protected BaseTestBinder() {
            super(RuntimeContext.EMPTY);
        }
    }

    public static final class ChildTestBinder extends BaseTestBinder {
        @Override
        public StreamingParser createParser(Reader input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamingGenerator createGenerator(Writer output) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void injectedBinderMayBeSubclassOfDeclaredBackendType() {
        ChildTestBinder binder = new ChildTestBinder();
        assertSame(binder, CompiledInstances.create(BackendCase.class, binder).backend());
    }

    @Test
    void runtimeContextConstructorStillUsesExactMatch() {
        assertNull(CompiledInstances.create(BackendCase.class, RuntimeContext.EMPTY).backend());
    }

    @Test
    void incompatibleBackendBinderReportsExpectedType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CompiledInstances.create(BackendCase.class, new SimpleJsonBinder()));
        assertTrue(error.getMessage().contains(SimpleJsonBinder.class.getName()), error.getMessage());
        assertTrue(error.getMessage().contains(BaseTestBinder.class.getName()), error.getMessage());
    }

    @Test
    void nonBinderInjectionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CompiledInstances.create(BackendCase.class, new Object()));
    }
}
