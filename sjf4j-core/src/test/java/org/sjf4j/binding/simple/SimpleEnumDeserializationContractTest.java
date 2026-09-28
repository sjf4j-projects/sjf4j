package org.sjf4j.binding.simple;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.enums.EnumDeserializationContract;
class SimpleEnumDeserializationContractTest extends EnumDeserializationContract {
     @Override
    protected Binder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
 }
