package org.sjf4j.binding.simple;

import org.sjf4j.binding.Binder;
import org.sjf4j.binding.contract.JsonBindingContract;
import org.sjf4j.NodeConfig;

class SimpleJsonBindingContractTest extends JsonBindingContract {
    @Override
    protected Binder<?, ?> binding(NodeConfig context) {
        return new SimpleJsonBinder(context);
    }
}
