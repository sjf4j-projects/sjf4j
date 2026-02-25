package org.sjf4j.facade;

import org.sjf4j.JsonObject;
import java.util.Properties;


/**
 * Properties facade for mapping {@link java.util.Properties} to {@link JsonObject}.
 *
 * <p>Warning: {@link java.util.Properties} does not preserve key order.
 */
public interface PropertiesFacade {

    JsonObject readNode(Properties props);

    void writeNode(Properties props, Object node);

}
