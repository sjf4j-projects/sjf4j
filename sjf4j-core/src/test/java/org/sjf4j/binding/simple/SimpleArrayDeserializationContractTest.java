package org.sjf4j.binding.simple;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.contract.ArrayDeserializationContract;
class SimpleArrayDeserializationContractTest extends ArrayDeserializationContract {
     @Override
    protected Binder<?, ?> binding(NodeConfig config) {
        return new SimpleJsonBinder(config);
    }
 }
