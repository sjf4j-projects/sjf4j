package org.sjf4j.binding.simple;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.access.AccessDeserializationContract;

class SimpleAccessDeserializationContractTest extends AccessDeserializationContract {
    @Override
    protected JsonBinder<?, ?> binding(RuntimeContext context) {
            return new SimpleJsonBinder(context);
        }
}
