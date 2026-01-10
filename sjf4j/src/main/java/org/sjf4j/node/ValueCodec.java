package org.sjf4j.node;


public interface ValueCodec<N, R> {

    R encode(N value);

    N decode(R raw);

    Class<N> getValueClass();

    Class<R> getRawClass();

    default N copy(N value) {
        return value;
    }

}
