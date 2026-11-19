package org.sjf4j.value;


/** Test SPI provider, discovered without explicit registerAnnotated(). */
public final class TestAutoCodecProvider implements ValueCodecProvider {

    public TestAutoCodecProvider() {
    }

    public static final class AutoId {
        public final String value;

        public AutoId(String value) {
            this.value = value;
        }
    }

    public static ValueCodec<AutoId, String> autoId() {
        return new ValueCodec.SimpleValueCodec<>(AutoId.class, String.class,
                id -> id.value, AutoId::new);
    }
}
