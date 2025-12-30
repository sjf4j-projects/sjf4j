package org.sjf4j.facade;

import java.lang.reflect.Type;

public interface NodeFacade {

    Object readNode(Object node, Type type, boolean deepCopy);

}
