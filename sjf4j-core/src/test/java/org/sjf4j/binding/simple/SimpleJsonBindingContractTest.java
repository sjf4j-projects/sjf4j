package org.sjf4j.binding.simple;

import org.sjf4j.binding.Binder;
import org.sjf4j.binding.contract.JsonBindingContract;
import org.sjf4j.RuntimeContext;

class SimpleJsonBindingContractTest extends JsonBindingContract {
    @Override
    protected Binder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
}
