package org.sjf4j.facades;

import org.sjf4j.JsonObject;
import java.util.Properties;


/**
 * Warning: java.util.Properties does not maintain key order (extends Hashtable).
 * As a result, converting Properties to and from JSON may lead to mismatches
 */
public interface PropertiesFacade {

    JsonObject readNode(Properties props);

    void writeNode(Properties props, JsonObject node);

}
