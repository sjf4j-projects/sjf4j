package org.sjf4j.binding.simple;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.contract.JDKAtomicTypesDeserializationContract;
class SimpleJDKAtomicTypesDeserializationContractTest extends JDKAtomicTypesDeserializationContract {
     @Override
    protected Binder<?, ?> binding(NodeConfig config) {
        return new SimpleJsonBinder(config);
    }
 }
