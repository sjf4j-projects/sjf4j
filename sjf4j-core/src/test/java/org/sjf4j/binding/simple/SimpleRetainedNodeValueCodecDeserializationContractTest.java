package org.sjf4j.binding.simple;

import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.contract.RetainedNodeValueCodecDeserializationContract;

class SimpleRetainedNodeValueCodecDeserializationContractTest extends RetainedNodeValueCodecDeserializationContract {
    @Override protected JsonBinder<?, ?> binding(StreamingContext context) { return new SimpleJsonBinder(context); }
}
