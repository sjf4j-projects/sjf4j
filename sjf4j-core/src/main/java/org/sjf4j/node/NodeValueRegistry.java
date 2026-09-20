package org.sjf4j.node;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NodeValueRegistry {
    private NodeValueRegistry() {}

    private static final Map<Class<?>, NodeValueInfo> EXTERNAL_NODES = new ConcurrentHashMap<>();



}
