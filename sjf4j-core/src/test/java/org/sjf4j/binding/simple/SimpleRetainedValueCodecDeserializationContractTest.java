package org.sjf4j.binding.simple;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.binding.contract.RetainedValueCodecDeserializationContract;

class SimpleRetainedValueCodecDeserializationContractTest extends RetainedValueCodecDeserializationContract {
    @Override
    protected StreamingBinder<?, ?> binding(RuntimeContext context) {
            return new SimpleJsonBinder(context);
        }
}
