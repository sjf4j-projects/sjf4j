package org.sjf4j;


public interface NodeConverter<N, R> {

    R convert(N node);

    N unconvert(R raw);

    Class<N> getNodeClass();

    Class<R> getRawClass();

    default N copy(N node) {
        return node;
    }

}
