package org.sjf4j.binding.simple;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.access.UnknownPropertyDeserializationContract;
class SimpleUnknownPropertyDeserializationContractTest extends UnknownPropertyDeserializationContract {
     @Override
     protected JsonBinder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
 }
