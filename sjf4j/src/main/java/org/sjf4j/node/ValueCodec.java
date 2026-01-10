package org.sjf4j.node;


public interface NodeValueCodec<N, R> {

    R encode(N node);

    N decode(R raw);

    Class<N> getNodeClass();

    Class<R> getRawClass();

    default N copy(N node) {
        return node;
    }

}
