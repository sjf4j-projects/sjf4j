package org.sjf4j.schema;

/**
 * Generated, type-specific access to the readable properties of a closed POJO.
 * Names and member signatures are checked against the runtime OBNT metadata
 * before this accessor is installed. Never used for dynamic object nodes.
 */
public interface CompiledPropertyAccess {
    Class<?> type();
    String[] names();
    String[] getterNames();
    Object read(Object node, int propertyIndex);
}
