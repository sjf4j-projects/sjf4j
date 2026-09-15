package org.sjf4j.binding.simple;

import org.sjf4j.binding.JsonBinding;
import org.sjf4j.binding.JsonBindingContractTest;
import org.sjf4j.binding.StreamingContext;

class SimpleJsonBindingContractTest extends JsonBindingContractTest {
    @Override
    protected JsonBinding<?, ?> binding(StreamingContext context) {
        return new SimpleJsonBinding(context);
    }
}
