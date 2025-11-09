package org.sjf4j;

public interface ObjectConverter<O, N> {

    Class<O> getObjectType();

    Class<N> getNodeType();

    N object2Node(O object);

    O node2Object(N node);

}
