package org.sjf4j.binding.simple;

import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.contract.JsonBindingContract;
import org.sjf4j.RuntimeContext;

class SimpleJsonBindingContractTest extends JsonBindingContract {
    @Override
    protected JsonBinder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
}
