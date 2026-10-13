package org.sjf4j.fixture;

/** Bean shared by Nodes and patch tests. */
public final class NodesBeanFixture {
    private NodesBeanFixture() {}

    public static class Bean {
        private String name;
        private int count;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}
