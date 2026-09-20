package org.sjf4j.binding;


public interface PreparedName {


    final class SimplePreparedName implements PreparedName {
        final String name;
        public SimplePreparedName(String name) {
            this.name = name;
        }
    }

}
