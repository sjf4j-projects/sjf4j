package org.sjf4j.testbench.handwritten;

import lombok.Data;

@Data
public class Friend {
    private long id;
    private long since;
    private String name;
    private boolean close;

    public Friend() {
    }

    public Friend(long id, String name, long since, boolean close) {
        this.id = id;
        this.name = name;
        this.since = since;
        this.close = close;
    }

}
