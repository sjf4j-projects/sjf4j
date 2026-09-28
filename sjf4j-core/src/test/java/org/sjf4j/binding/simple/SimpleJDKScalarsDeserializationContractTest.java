package org.sjf4j.binding.simple;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.contract.JDKScalarsDeserializationContract;
class SimpleJDKScalarsDeserializationContractTest extends JDKScalarsDeserializationContract {
     @Override
    protected StreamingBinder<?, ?> binding(RuntimeContext context) {
        return new SimpleJsonBinder(context);
    }
 }
