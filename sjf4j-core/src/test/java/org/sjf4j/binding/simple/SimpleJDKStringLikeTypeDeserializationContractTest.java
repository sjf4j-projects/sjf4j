package org.sjf4j.binding.simple;
import org.sjf4j.binding.Binder;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.contract.JDKStringLikeTypeDeserializationContract;

class SimpleJDKStringLikeTypeDeserializationContractTest extends JDKStringLikeTypeDeserializationContract {

    @Override
    protected Binder<?, ?> binding(NodeConfig context) {
        return new SimpleJsonBinder(context);
    }

}
