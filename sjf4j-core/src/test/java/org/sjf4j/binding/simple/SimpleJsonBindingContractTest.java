package org.sjf4j.binding.simple;

import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.binding.contract.JsonBindingContract;
import org.sjf4j.RuntimeContext;

class SimpleJsonBindingContractTest extends JsonBindingContract {
    @Override
    protected StreamingBinder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
}
