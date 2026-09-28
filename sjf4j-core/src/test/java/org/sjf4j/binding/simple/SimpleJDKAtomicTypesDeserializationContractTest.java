package org.sjf4j.binding.simple;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.contract.JDKAtomicTypesDeserializationContract;
class SimpleJDKAtomicTypesDeserializationContractTest extends JDKAtomicTypesDeserializationContract {
     @Override
    protected Binder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
 }
