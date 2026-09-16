package org.sjf4j.binding.simple;

import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.JsonBindingContractTest;
import org.sjf4j.binding.StreamingContext;

class SimpleJsonBindingContractTest extends JsonBindingContractTest {
    @Override
    protected JsonBinder<?, ?> binding(StreamingContext context) {
        return new SimpleJsonBinder(context);
    }
}
