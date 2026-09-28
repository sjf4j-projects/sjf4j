package org.sjf4j.binding.simple;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.contract.ValueCodecDeserializationContract;
class SimpleValueCodecDeserializationContractTest extends ValueCodecDeserializationContract {
     @Override
    protected StreamingBinder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
 }
