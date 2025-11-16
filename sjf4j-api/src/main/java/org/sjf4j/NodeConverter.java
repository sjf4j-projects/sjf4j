package org.sjf4j;

public interface NodeConverter<W, P> {

    Class<W> getWrapType();

    Class<P> getPureType();

    P wrap2Pure(W wrap);

    W pure2Wrap(P pure);

}
