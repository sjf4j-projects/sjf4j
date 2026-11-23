package org.sjf4j.testbench.binding.fabien;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3CompiledName;
import org.sjf4j.backend.jackson3.binding.Jackson3Generator;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;
import java.io.IOException;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.TimeUnit;

/**
 * HANDWRITTEN Users POJO write ablations; no generated serialization body.
 * Each case performs complete byte[] materialization with the same pooled output.
 * Independent comparisons:
 *   holderHelperWrapped vs holderHelperRaw (wrapper only);
 *   holderHelperWrapped vs serializedHelperWrapped (name-holder only);
 *   holderHelperWrapped vs holderInlineWrapped (string-member call only).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(2)
public class HandwrittenJackson3UsersWriteBenchmark {
    @Param({"native", "blackbird", "compiled",
            "holderHelperWrapped", "holderHelperRaw",
            "serializedHelperWrapped", "holderInlineWrapped",
            "serializedInlineWrapped", "serializedInlineRaw"})
    public String implementation;

    @Param({"1", "10", "100"})
    public int sizeKb;

    @Param({"12345", "324"})
    public long seed;

    @FunctionalInterface
    private interface Writer {
        byte[] write(Users value) throws Exception;
    }

    private static final ObjectMapper VERIFY = new ObjectMapper();
    private static final ObjectWriteContext WRITE_CONTEXT = ObjectWriteContext.empty();
    private static final JsonFactory FACTORY = new JsonFactory().rebuild()
            .disable(TokenStreamFactory.Feature.CHARSET_DETECTION)
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .build();
    private static final Jackson3Binder BINDER = new Jackson3Binder(FACTORY);
    private static final boolean INCLUDE_NULLS = true;

    private Users value;
    private Writer writer;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        value = FabienDataGenerator.users(Math.multiplyExact(sizeKb, 1000), seed);
        switch (implementation) {
            case "native":
                writer = value -> FabienBindings.writer("jackson3", "native", "users", "pojo").write(value);
                break;
            case "blackbird":
                writer = value -> FabienBindings.writer("jackson3", "blackbird", "users", "pojo").write(value);
                break;
            case "compiled":
                writer = value -> FabienBindings.writer("jackson3", "compiled", "users", "pojo").write(value);
                break;
            case "holderHelperWrapped": writer = HandwrittenJackson3UsersWriteBenchmark::holderHelperWrapped; break;
            case "holderHelperRaw": writer = HandwrittenJackson3UsersWriteBenchmark::holderHelperRaw; break;
            case "serializedHelperWrapped": writer = HandwrittenJackson3UsersWriteBenchmark::serializedHelperWrapped; break;
            case "holderInlineWrapped": writer = HandwrittenJackson3UsersWriteBenchmark::holderInlineWrapped; break;
            case "serializedInlineWrapped": writer = HandwrittenJackson3UsersWriteBenchmark::serializedInlineWrapped; break;
            case "serializedInlineRaw": writer = HandwrittenJackson3UsersWriteBenchmark::serializedInlineRaw; break;
            default: throw new IllegalArgumentException(implementation);
        }
        verify(value);
        verify(Users.sample()); // includes null fields, unicode, escaping, null list elements
        verify(new Users()); // null property
        verify(null); // null root
    }

    private void verify(Users sample) throws Exception {
        byte[] expected = FabienBindings.writer("jackson3", "native", "users", "pojo").write(sample);
        byte[] actual = writer.write(sample);
        JsonNode a = VERIFY.readTree(expected);
        JsonNode b = VERIFY.readTree(actual);
        if (!a.equals(b)) {
            throw new IllegalStateException("JSON mismatch impl=" + implementation + " size="
                    + sizeKb + " seed=" + seed + " expected=" + a + " actual=" + b);
        }
    }

    @Benchmark
    public byte[] write() throws Exception {
        return writer.write(value);
    }

    private static final class NamesHolder {
        private static final Jackson3CompiledName USERS_USERS = new Jackson3CompiledName("users");
        private static final Jackson3CompiledName USER_ID = new Jackson3CompiledName("id");
        private static final Jackson3CompiledName USER_INDEX = new Jackson3CompiledName("index");
        private static final Jackson3CompiledName USER_GUID = new Jackson3CompiledName("guid");
        private static final Jackson3CompiledName USER_IS_ACTIVE = new Jackson3CompiledName("isActive");
        private static final Jackson3CompiledName USER_BALANCE = new Jackson3CompiledName("balance");
        private static final Jackson3CompiledName USER_PICTURE = new Jackson3CompiledName("picture");
        private static final Jackson3CompiledName USER_AGE = new Jackson3CompiledName("age");
        private static final Jackson3CompiledName USER_EYE_COLOR = new Jackson3CompiledName("eyeColor");
        private static final Jackson3CompiledName USER_NAME = new Jackson3CompiledName("name");
        private static final Jackson3CompiledName USER_GENDER = new Jackson3CompiledName("gender");
        private static final Jackson3CompiledName USER_COMPANY = new Jackson3CompiledName("company");
        private static final Jackson3CompiledName USER_EMAIL = new Jackson3CompiledName("email");
        private static final Jackson3CompiledName USER_PHONE = new Jackson3CompiledName("phone");
        private static final Jackson3CompiledName USER_ADDRESS = new Jackson3CompiledName("address");
        private static final Jackson3CompiledName USER_ABOUT = new Jackson3CompiledName("about");
        private static final Jackson3CompiledName USER_REGISTERED = new Jackson3CompiledName("registered");
        private static final Jackson3CompiledName USER_LATITUDE = new Jackson3CompiledName("latitude");
        private static final Jackson3CompiledName USER_LONGITUDE = new Jackson3CompiledName("longitude");
        private static final Jackson3CompiledName USER_TAGS = new Jackson3CompiledName("tags");
        private static final Jackson3CompiledName USER_FRIENDS = new Jackson3CompiledName("friends");
        private static final Jackson3CompiledName USER_GREETING = new Jackson3CompiledName("greeting");
        private static final Jackson3CompiledName USER_FAVORITE_FRUIT = new Jackson3CompiledName("favoriteFruit");
        private static final Jackson3CompiledName FRIEND_ID = new Jackson3CompiledName("id");
        private static final Jackson3CompiledName FRIEND_NAME = new Jackson3CompiledName("name");
    }

    private static final class NamesSerialized {
        private static final SerializedString USERS_USERS = new SerializedString("users");
        private static final SerializedString USER_ID = new SerializedString("id");
        private static final SerializedString USER_INDEX = new SerializedString("index");
        private static final SerializedString USER_GUID = new SerializedString("guid");
        private static final SerializedString USER_IS_ACTIVE = new SerializedString("isActive");
        private static final SerializedString USER_BALANCE = new SerializedString("balance");
        private static final SerializedString USER_PICTURE = new SerializedString("picture");
        private static final SerializedString USER_AGE = new SerializedString("age");
        private static final SerializedString USER_EYE_COLOR = new SerializedString("eyeColor");
        private static final SerializedString USER_NAME = new SerializedString("name");
        private static final SerializedString USER_GENDER = new SerializedString("gender");
        private static final SerializedString USER_COMPANY = new SerializedString("company");
        private static final SerializedString USER_EMAIL = new SerializedString("email");
        private static final SerializedString USER_PHONE = new SerializedString("phone");
        private static final SerializedString USER_ADDRESS = new SerializedString("address");
        private static final SerializedString USER_ABOUT = new SerializedString("about");
        private static final SerializedString USER_REGISTERED = new SerializedString("registered");
        private static final SerializedString USER_LATITUDE = new SerializedString("latitude");
        private static final SerializedString USER_LONGITUDE = new SerializedString("longitude");
        private static final SerializedString USER_TAGS = new SerializedString("tags");
        private static final SerializedString USER_FRIENDS = new SerializedString("friends");
        private static final SerializedString USER_GREETING = new SerializedString("greeting");
        private static final SerializedString USER_FAVORITE_FRUIT = new SerializedString("favoriteFruit");
        private static final SerializedString FRIEND_ID = new SerializedString("id");
        private static final SerializedString FRIEND_NAME = new SerializedString("name");
    }

    private static void writeStringMember(JsonGenerator g, SerializedString name,
                                          String value, boolean includeNulls) throws IOException {
        if (value == null) {
            if (includeNulls) {
                g.writeName(name);
                g.writeNull();
            }
        } else {
            g.writeName(name);
            g.writeString(value);
        }
    }

    private static byte[] holderHelperWrapped(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (Jackson3Generator wrapper = BINDER.createGenerator(output)) {
                writeUsersHolderHelper(wrapper.nativeGenerator(), value);
            }
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    private static byte[] holderHelperRaw(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (JsonGenerator g = FACTORY.createGenerator(WRITE_CONTEXT, output)) {
                writeUsersHolderHelper(g, value);
            }
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    private static byte[] serializedHelperWrapped(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (Jackson3Generator wrapper = BINDER.createGenerator(output)) {
                writeUsersSerializedHelper(wrapper.nativeGenerator(), value);
            }
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    private static byte[] holderInlineWrapped(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (Jackson3Generator wrapper = BINDER.createGenerator(output)) {
                writeUsersHolderInline(wrapper.nativeGenerator(), value);
            }
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    private static byte[] serializedInlineWrapped(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (Jackson3Generator wrapper = BINDER.createGenerator(output)) {
                writeUsersSerializedInline(wrapper.nativeGenerator(), value);
            }
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    private static byte[] serializedInlineRaw(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (JsonGenerator g = FACTORY.createGenerator(WRITE_CONTEXT, output)) {
                writeUsersSerializedInline(g, value);
            }
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    private static void writeUsersHolderHelper(JsonGenerator g, Users value) throws IOException {
        if (value == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        List<Users.User> items = value.getUsers();
        if (items == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USERS_USERS.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USERS_USERS.serializedName);
            g.writeStartArray();
            if (items instanceof RandomAccess) {
                for (int i = 0, n = items.size(); i < n; i++) {
                    Users.User item = items.get(i);
                    if (item == null) g.writeNull();
                    else writeUserHolderHelper(g, item);
                }
            } else {
                for (Users.User item : items) {
                    if (item == null) g.writeNull();
                    else writeUserHolderHelper(g, item);
                }
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    private static void writeUserHolderHelper(JsonGenerator g, Users.User value) throws IOException {
        g.writeStartObject();
        writeStringMember(g, NamesHolder.USER_ID.serializedName, value.getId(), INCLUDE_NULLS);
        g.writeName(NamesHolder.USER_INDEX.serializedName);
        g.writeNumber(value.getIndex());
        writeStringMember(g, NamesHolder.USER_GUID.serializedName, value.getGuid(), INCLUDE_NULLS);
        g.writeName(NamesHolder.USER_IS_ACTIVE.serializedName);
        g.writeBoolean(value.getIsActive());
        writeStringMember(g, NamesHolder.USER_BALANCE.serializedName, value.getBalance(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_PICTURE.serializedName, value.getPicture(), INCLUDE_NULLS);
        g.writeName(NamesHolder.USER_AGE.serializedName);
        g.writeNumber(value.getAge());
        writeStringMember(g, NamesHolder.USER_EYE_COLOR.serializedName, value.getEyeColor(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_NAME.serializedName, value.getName(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_GENDER.serializedName, value.getGender(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_COMPANY.serializedName, value.getCompany(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_EMAIL.serializedName, value.getEmail(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_PHONE.serializedName, value.getPhone(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_ADDRESS.serializedName, value.getAddress(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_ABOUT.serializedName, value.getAbout(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_REGISTERED.serializedName, value.getRegistered(), INCLUDE_NULLS);
        g.writeName(NamesHolder.USER_LATITUDE.serializedName);
        g.writeNumber(value.getLatitude());
        g.writeName(NamesHolder.USER_LONGITUDE.serializedName);
        g.writeNumber(value.getLongitude());
        List<String> tags = value.getTags();
        if (tags == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_TAGS.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_TAGS.serializedName);
            g.writeStartArray();
            if (tags instanceof RandomAccess) {
                for (int i = 0, n = tags.size(); i < n; i++) {
                    String element = tags.get(i);
                    if (element == null) g.writeNull();
                    else g.writeString(element);
                }
            } else {
                for (String element : tags) {
                    if (element == null) g.writeNull();
                    else g.writeString(element);
                }
            }
            g.writeEndArray();
        }
        List<Users.Friend> friends = value.getFriends();
        if (friends == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_FRIENDS.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_FRIENDS.serializedName);
            g.writeStartArray();
            if (friends instanceof RandomAccess) {
                for (int i = 0, n = friends.size(); i < n; i++) {
                    Users.Friend element = friends.get(i);
                    if (element == null) g.writeNull();
                    else writeFriendHolderHelper(g, element);
                }
            } else {
                for (Users.Friend element : friends) {
                    if (element == null) g.writeNull();
                    else writeFriendHolderHelper(g, element);
                }
            }
            g.writeEndArray();
        }
        writeStringMember(g, NamesHolder.USER_GREETING.serializedName, value.getGreeting(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.USER_FAVORITE_FRUIT.serializedName, value.getFavoriteFruit(), INCLUDE_NULLS);
        g.writeEndObject();
    }

    private static void writeFriendHolderHelper(JsonGenerator g, Users.Friend value) throws IOException {
        g.writeStartObject();
        writeStringMember(g, NamesHolder.FRIEND_ID.serializedName, value.getId(), INCLUDE_NULLS);
        writeStringMember(g, NamesHolder.FRIEND_NAME.serializedName, value.getName(), INCLUDE_NULLS);
        g.writeEndObject();
    }

    private static void writeUsersHolderInline(JsonGenerator g, Users value) throws IOException {
        if (value == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        List<Users.User> items = value.getUsers();
        if (items == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USERS_USERS.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USERS_USERS.serializedName);
            g.writeStartArray();
            if (items instanceof RandomAccess) {
                for (int i = 0, n = items.size(); i < n; i++) {
                    Users.User item = items.get(i);
                    if (item == null) g.writeNull();
                    else writeUserHolderInline(g, item);
                }
            } else {
                for (Users.User item : items) {
                    if (item == null) g.writeNull();
                    else writeUserHolderInline(g, item);
                }
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    private static void writeUserHolderInline(JsonGenerator g, Users.User value) throws IOException {
        g.writeStartObject();
        String propertyID = value.getId();
        if (propertyID == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_ID.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_ID.serializedName);
            g.writeString(propertyID);
        }
        g.writeName(NamesHolder.USER_INDEX.serializedName);
        g.writeNumber(value.getIndex());
        String propertyGUID = value.getGuid();
        if (propertyGUID == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_GUID.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_GUID.serializedName);
            g.writeString(propertyGUID);
        }
        g.writeName(NamesHolder.USER_IS_ACTIVE.serializedName);
        g.writeBoolean(value.getIsActive());
        String propertyBALANCE = value.getBalance();
        if (propertyBALANCE == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_BALANCE.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_BALANCE.serializedName);
            g.writeString(propertyBALANCE);
        }
        String propertyPICTURE = value.getPicture();
        if (propertyPICTURE == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_PICTURE.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_PICTURE.serializedName);
            g.writeString(propertyPICTURE);
        }
        g.writeName(NamesHolder.USER_AGE.serializedName);
        g.writeNumber(value.getAge());
        String propertyEYE_COLOR = value.getEyeColor();
        if (propertyEYE_COLOR == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_EYE_COLOR.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_EYE_COLOR.serializedName);
            g.writeString(propertyEYE_COLOR);
        }
        String propertyNAME = value.getName();
        if (propertyNAME == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_NAME.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_NAME.serializedName);
            g.writeString(propertyNAME);
        }
        String propertyGENDER = value.getGender();
        if (propertyGENDER == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_GENDER.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_GENDER.serializedName);
            g.writeString(propertyGENDER);
        }
        String propertyCOMPANY = value.getCompany();
        if (propertyCOMPANY == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_COMPANY.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_COMPANY.serializedName);
            g.writeString(propertyCOMPANY);
        }
        String propertyEMAIL = value.getEmail();
        if (propertyEMAIL == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_EMAIL.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_EMAIL.serializedName);
            g.writeString(propertyEMAIL);
        }
        String propertyPHONE = value.getPhone();
        if (propertyPHONE == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_PHONE.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_PHONE.serializedName);
            g.writeString(propertyPHONE);
        }
        String propertyADDRESS = value.getAddress();
        if (propertyADDRESS == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_ADDRESS.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_ADDRESS.serializedName);
            g.writeString(propertyADDRESS);
        }
        String propertyABOUT = value.getAbout();
        if (propertyABOUT == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_ABOUT.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_ABOUT.serializedName);
            g.writeString(propertyABOUT);
        }
        String propertyREGISTERED = value.getRegistered();
        if (propertyREGISTERED == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_REGISTERED.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_REGISTERED.serializedName);
            g.writeString(propertyREGISTERED);
        }
        g.writeName(NamesHolder.USER_LATITUDE.serializedName);
        g.writeNumber(value.getLatitude());
        g.writeName(NamesHolder.USER_LONGITUDE.serializedName);
        g.writeNumber(value.getLongitude());
        List<String> tags = value.getTags();
        if (tags == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_TAGS.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_TAGS.serializedName);
            g.writeStartArray();
            if (tags instanceof RandomAccess) {
                for (int i = 0, n = tags.size(); i < n; i++) {
                    String element = tags.get(i);
                    if (element == null) g.writeNull();
                    else g.writeString(element);
                }
            } else {
                for (String element : tags) {
                    if (element == null) g.writeNull();
                    else g.writeString(element);
                }
            }
            g.writeEndArray();
        }
        List<Users.Friend> friends = value.getFriends();
        if (friends == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_FRIENDS.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_FRIENDS.serializedName);
            g.writeStartArray();
            if (friends instanceof RandomAccess) {
                for (int i = 0, n = friends.size(); i < n; i++) {
                    Users.Friend element = friends.get(i);
                    if (element == null) g.writeNull();
                    else writeFriendHolderInline(g, element);
                }
            } else {
                for (Users.Friend element : friends) {
                    if (element == null) g.writeNull();
                    else writeFriendHolderInline(g, element);
                }
            }
            g.writeEndArray();
        }
        String propertyGREETING = value.getGreeting();
        if (propertyGREETING == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_GREETING.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_GREETING.serializedName);
            g.writeString(propertyGREETING);
        }
        String propertyFAVORITE_FRUIT = value.getFavoriteFruit();
        if (propertyFAVORITE_FRUIT == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.USER_FAVORITE_FRUIT.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.USER_FAVORITE_FRUIT.serializedName);
            g.writeString(propertyFAVORITE_FRUIT);
        }
        g.writeEndObject();
    }

    private static void writeFriendHolderInline(JsonGenerator g, Users.Friend value) throws IOException {
        g.writeStartObject();
        String propertyFriendId = value.getId();
        if (propertyFriendId == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.FRIEND_ID.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.FRIEND_ID.serializedName);
            g.writeString(propertyFriendId);
        }
        String propertyFriendName = value.getName();
        if (propertyFriendName == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesHolder.FRIEND_NAME.serializedName);
                g.writeNull();
            }
        } else {
            g.writeName(NamesHolder.FRIEND_NAME.serializedName);
            g.writeString(propertyFriendName);
        }
        g.writeEndObject();
    }

    private static void writeUsersSerializedHelper(JsonGenerator g, Users value) throws IOException {
        if (value == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        List<Users.User> items = value.getUsers();
        if (items == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USERS_USERS);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USERS_USERS);
            g.writeStartArray();
            if (items instanceof RandomAccess) {
                for (int i = 0, n = items.size(); i < n; i++) {
                    Users.User item = items.get(i);
                    if (item == null) g.writeNull();
                    else writeUserSerializedHelper(g, item);
                }
            } else {
                for (Users.User item : items) {
                    if (item == null) g.writeNull();
                    else writeUserSerializedHelper(g, item);
                }
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    private static void writeUserSerializedHelper(JsonGenerator g, Users.User value) throws IOException {
        g.writeStartObject();
        writeStringMember(g, NamesSerialized.USER_ID, value.getId(), INCLUDE_NULLS);
        g.writeName(NamesSerialized.USER_INDEX);
        g.writeNumber(value.getIndex());
        writeStringMember(g, NamesSerialized.USER_GUID, value.getGuid(), INCLUDE_NULLS);
        g.writeName(NamesSerialized.USER_IS_ACTIVE);
        g.writeBoolean(value.getIsActive());
        writeStringMember(g, NamesSerialized.USER_BALANCE, value.getBalance(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_PICTURE, value.getPicture(), INCLUDE_NULLS);
        g.writeName(NamesSerialized.USER_AGE);
        g.writeNumber(value.getAge());
        writeStringMember(g, NamesSerialized.USER_EYE_COLOR, value.getEyeColor(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_NAME, value.getName(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_GENDER, value.getGender(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_COMPANY, value.getCompany(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_EMAIL, value.getEmail(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_PHONE, value.getPhone(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_ADDRESS, value.getAddress(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_ABOUT, value.getAbout(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_REGISTERED, value.getRegistered(), INCLUDE_NULLS);
        g.writeName(NamesSerialized.USER_LATITUDE);
        g.writeNumber(value.getLatitude());
        g.writeName(NamesSerialized.USER_LONGITUDE);
        g.writeNumber(value.getLongitude());
        List<String> tags = value.getTags();
        if (tags == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_TAGS);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_TAGS);
            g.writeStartArray();
            if (tags instanceof RandomAccess) {
                for (int i = 0, n = tags.size(); i < n; i++) {
                    String element = tags.get(i);
                    if (element == null) g.writeNull();
                    else g.writeString(element);
                }
            } else {
                for (String element : tags) {
                    if (element == null) g.writeNull();
                    else g.writeString(element);
                }
            }
            g.writeEndArray();
        }
        List<Users.Friend> friends = value.getFriends();
        if (friends == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_FRIENDS);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_FRIENDS);
            g.writeStartArray();
            if (friends instanceof RandomAccess) {
                for (int i = 0, n = friends.size(); i < n; i++) {
                    Users.Friend element = friends.get(i);
                    if (element == null) g.writeNull();
                    else writeFriendSerializedHelper(g, element);
                }
            } else {
                for (Users.Friend element : friends) {
                    if (element == null) g.writeNull();
                    else writeFriendSerializedHelper(g, element);
                }
            }
            g.writeEndArray();
        }
        writeStringMember(g, NamesSerialized.USER_GREETING, value.getGreeting(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.USER_FAVORITE_FRUIT, value.getFavoriteFruit(), INCLUDE_NULLS);
        g.writeEndObject();
    }

    private static void writeFriendSerializedHelper(JsonGenerator g, Users.Friend value) throws IOException {
        g.writeStartObject();
        writeStringMember(g, NamesSerialized.FRIEND_ID, value.getId(), INCLUDE_NULLS);
        writeStringMember(g, NamesSerialized.FRIEND_NAME, value.getName(), INCLUDE_NULLS);
        g.writeEndObject();
    }

    private static void writeUsersSerializedInline(JsonGenerator g, Users value) throws IOException {
        if (value == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        List<Users.User> items = value.getUsers();
        if (items == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USERS_USERS);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USERS_USERS);
            g.writeStartArray();
            if (items instanceof RandomAccess) {
                for (int i = 0, n = items.size(); i < n; i++) {
                    Users.User item = items.get(i);
                    if (item == null) g.writeNull();
                    else writeUserSerializedInline(g, item);
                }
            } else {
                for (Users.User item : items) {
                    if (item == null) g.writeNull();
                    else writeUserSerializedInline(g, item);
                }
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    private static void writeUserSerializedInline(JsonGenerator g, Users.User value) throws IOException {
        g.writeStartObject();
        String propertyID = value.getId();
        if (propertyID == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_ID);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_ID);
            g.writeString(propertyID);
        }
        g.writeName(NamesSerialized.USER_INDEX);
        g.writeNumber(value.getIndex());
        String propertyGUID = value.getGuid();
        if (propertyGUID == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_GUID);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_GUID);
            g.writeString(propertyGUID);
        }
        g.writeName(NamesSerialized.USER_IS_ACTIVE);
        g.writeBoolean(value.getIsActive());
        String propertyBALANCE = value.getBalance();
        if (propertyBALANCE == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_BALANCE);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_BALANCE);
            g.writeString(propertyBALANCE);
        }
        String propertyPICTURE = value.getPicture();
        if (propertyPICTURE == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_PICTURE);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_PICTURE);
            g.writeString(propertyPICTURE);
        }
        g.writeName(NamesSerialized.USER_AGE);
        g.writeNumber(value.getAge());
        String propertyEYE_COLOR = value.getEyeColor();
        if (propertyEYE_COLOR == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_EYE_COLOR);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_EYE_COLOR);
            g.writeString(propertyEYE_COLOR);
        }
        String propertyNAME = value.getName();
        if (propertyNAME == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_NAME);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_NAME);
            g.writeString(propertyNAME);
        }
        String propertyGENDER = value.getGender();
        if (propertyGENDER == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_GENDER);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_GENDER);
            g.writeString(propertyGENDER);
        }
        String propertyCOMPANY = value.getCompany();
        if (propertyCOMPANY == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_COMPANY);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_COMPANY);
            g.writeString(propertyCOMPANY);
        }
        String propertyEMAIL = value.getEmail();
        if (propertyEMAIL == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_EMAIL);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_EMAIL);
            g.writeString(propertyEMAIL);
        }
        String propertyPHONE = value.getPhone();
        if (propertyPHONE == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_PHONE);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_PHONE);
            g.writeString(propertyPHONE);
        }
        String propertyADDRESS = value.getAddress();
        if (propertyADDRESS == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_ADDRESS);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_ADDRESS);
            g.writeString(propertyADDRESS);
        }
        String propertyABOUT = value.getAbout();
        if (propertyABOUT == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_ABOUT);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_ABOUT);
            g.writeString(propertyABOUT);
        }
        String propertyREGISTERED = value.getRegistered();
        if (propertyREGISTERED == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_REGISTERED);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_REGISTERED);
            g.writeString(propertyREGISTERED);
        }
        g.writeName(NamesSerialized.USER_LATITUDE);
        g.writeNumber(value.getLatitude());
        g.writeName(NamesSerialized.USER_LONGITUDE);
        g.writeNumber(value.getLongitude());
        List<String> tags = value.getTags();
        if (tags == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_TAGS);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_TAGS);
            g.writeStartArray();
            if (tags instanceof RandomAccess) {
                for (int i = 0, n = tags.size(); i < n; i++) {
                    String element = tags.get(i);
                    if (element == null) g.writeNull();
                    else g.writeString(element);
                }
            } else {
                for (String element : tags) {
                    if (element == null) g.writeNull();
                    else g.writeString(element);
                }
            }
            g.writeEndArray();
        }
        List<Users.Friend> friends = value.getFriends();
        if (friends == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_FRIENDS);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_FRIENDS);
            g.writeStartArray();
            if (friends instanceof RandomAccess) {
                for (int i = 0, n = friends.size(); i < n; i++) {
                    Users.Friend element = friends.get(i);
                    if (element == null) g.writeNull();
                    else writeFriendSerializedInline(g, element);
                }
            } else {
                for (Users.Friend element : friends) {
                    if (element == null) g.writeNull();
                    else writeFriendSerializedInline(g, element);
                }
            }
            g.writeEndArray();
        }
        String propertyGREETING = value.getGreeting();
        if (propertyGREETING == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_GREETING);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_GREETING);
            g.writeString(propertyGREETING);
        }
        String propertyFAVORITE_FRUIT = value.getFavoriteFruit();
        if (propertyFAVORITE_FRUIT == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.USER_FAVORITE_FRUIT);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.USER_FAVORITE_FRUIT);
            g.writeString(propertyFAVORITE_FRUIT);
        }
        g.writeEndObject();
    }

    private static void writeFriendSerializedInline(JsonGenerator g, Users.Friend value) throws IOException {
        g.writeStartObject();
        String propertyFriendId = value.getId();
        if (propertyFriendId == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.FRIEND_ID);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.FRIEND_ID);
            g.writeString(propertyFriendId);
        }
        String propertyFriendName = value.getName();
        if (propertyFriendName == null) {
            if (INCLUDE_NULLS) {
                g.writeName(NamesSerialized.FRIEND_NAME);
                g.writeNull();
            }
        } else {
            g.writeName(NamesSerialized.FRIEND_NAME);
            g.writeString(propertyFriendName);
        }
        g.writeEndObject();
    }

}
