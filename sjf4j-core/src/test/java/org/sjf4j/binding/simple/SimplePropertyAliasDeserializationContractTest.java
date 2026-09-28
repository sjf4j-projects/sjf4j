package org.sjf4j.binding.simple;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.contract.PropertyAliasDeserializationContract;
class SimplePropertyAliasDeserializationContractTest extends PropertyAliasDeserializationContract {
     @Override
    protected Binder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
 }
