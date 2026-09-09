package org.sjf4j.handwritten;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Direct Jackson2 parser baseline for {@link HandReadBenchmark.UserPojo}. */
public final class Jackson2HandPojoReader {

    private Jackson2HandPojoReader() {}

    public static HandReadBenchmark.UserPojo readUser(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }
        if (token != JsonToken.START_OBJECT) {
            throw new IllegalStateException("expected object, but was " + token);
        }

        HandReadBenchmark.UserPojo user = new HandReadBenchmark.UserPojo();
        token = parser.nextToken();
        while (token != JsonToken.END_OBJECT) {
            if (token != JsonToken.FIELD_NAME) {
                throw new IllegalStateException("expected field name, but was " + token);
            }
            String name = parser.currentName();
            token = parser.nextToken();
            if ("name".equals(name)) {
                user.setName(token == JsonToken.VALUE_NULL ? null : parser.getText());
                token = parser.nextToken();
            } else if ("friends".equals(name)) {
                user.setFriends(readUsers(parser, token));
                token = parser.currentToken();
            } else {
                parser.skipChildren();
                token = parser.nextToken();
            }
        }
        parser.nextToken();
        return user;
    }

    private static List<HandReadBenchmark.UserPojo> readUsers(JsonParser parser, JsonToken token)
            throws IOException {
        if (token == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return null;
        }
        if (token != JsonToken.START_ARRAY) {
            throw new IllegalStateException("expected array, but was " + token);
        }

        List<HandReadBenchmark.UserPojo> users = new ArrayList<>();
        token = parser.nextToken();
        while (token != JsonToken.END_ARRAY) {
            users.add(readUser(parser));
            token = parser.currentToken();
        }
        parser.nextToken();
        return users;
    }
}
