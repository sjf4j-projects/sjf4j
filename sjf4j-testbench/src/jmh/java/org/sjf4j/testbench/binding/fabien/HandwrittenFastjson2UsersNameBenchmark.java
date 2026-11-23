package org.sjf4j.testbench.binding.fabien;

import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Generator;
import org.sjf4j.testbench.model.fabien.FabienDataGenerator;
import org.sjf4j.testbench.model.fabien.Users;

import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.TimeUnit;

/** Handwritten Users UTF-8 write A/B: Fastjson2CompiledName holder vs static final native packed fields.
 * All use Fastjson2Binder.createGeneratorUtf8(), identical property ordering,
 * writeNameXRaw() calls, and output lifecycle. Literal packs bytes at code generation time; no APT changes.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class HandwrittenFastjson2UsersNameBenchmark {
    @Param({"holder", "direct", "literal", "native", "compiled"})
    public String implementation;

    @Param({"1", "10", "100"})
    public int sizeKb;

    @Param({"12345", "324"})
    public long seed;

    @FunctionalInterface
    private interface Writer { byte[] write(Users value) throws Exception; }

    private static final Fastjson2Binder BINDER = new Fastjson2Binder();
    private static final ObjectMapper VERIFY = new ObjectMapper();
    private static final boolean INCLUDE_NULLS = true;

    private Users value;
    private Writer writer;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        if (java.nio.ByteOrder.nativeOrder() != java.nio.ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalStateException("Literal packed-name experiment requires little-endian CPU");
        }
        value = FabienDataGenerator.users(Math.multiplyExact(sizeKb, 1000), seed);
        switch (implementation) {
            case "holder": writer = HandwrittenFastjson2UsersNameBenchmark::writeHolder; break;
            case "direct": writer = HandwrittenFastjson2UsersNameBenchmark::writeDirect; break;
            case "literal": writer = HandwrittenFastjson2UsersNameBenchmark::writeLiteral; break;
            case "native": {
                FabienBindings.Writer nativeWriter = FabienBindings.writer("fastjson2", "native", "users", "pojo");
                writer = x -> nativeWriter.write(x);
                break;
            }
            case "compiled": {
                FabienBindings.Writer compiledWriter = FabienBindings.writer("fastjson2", "compiled", "users", "pojo");
                writer = x -> compiledWriter.write(x);
                break;
            }
            default: throw new IllegalArgumentException(implementation);
        }
        verify(value);
        verify(Users.sample()); // Unicode, escapes and null fields
        verify(new Users());
        verify(null);
    }

    private void verify(Users sample) throws Exception {
        byte[] actual = writer.write(sample);
        byte[] expected = FabienBindings.writer("fastjson2", "native", "users", "pojo").write(sample);
        JsonNode a = VERIFY.readTree(expected);
        JsonNode b = VERIFY.readTree(actual);
        if (!a.equals(b)) {
            throw new IllegalStateException("Fastjson2 JSON mismatch impl=" + implementation
                    + " size=" + sizeKb + " seed=" + seed + " expected=" + a + " actual=" + b);
        }
    }

    @Benchmark
    public byte[] write() throws Exception {
        return writer.write(value);
    }


    private static final class Holder {
        static final Fastjson2CompiledName USERS_USERS = new Fastjson2CompiledName("users");
        static final Fastjson2CompiledName USER_ID = new Fastjson2CompiledName("id");
        static final Fastjson2CompiledName USER_INDEX = new Fastjson2CompiledName("index");
        static final Fastjson2CompiledName USER_GUID = new Fastjson2CompiledName("guid");
        static final Fastjson2CompiledName USER_IS_ACTIVE = new Fastjson2CompiledName("isActive");
        static final Fastjson2CompiledName USER_BALANCE = new Fastjson2CompiledName("balance");
        static final Fastjson2CompiledName USER_PICTURE = new Fastjson2CompiledName("picture");
        static final Fastjson2CompiledName USER_AGE = new Fastjson2CompiledName("age");
        static final Fastjson2CompiledName USER_EYE_COLOR = new Fastjson2CompiledName("eyeColor");
        static final Fastjson2CompiledName USER_NAME = new Fastjson2CompiledName("name");
        static final Fastjson2CompiledName USER_GENDER = new Fastjson2CompiledName("gender");
        static final Fastjson2CompiledName USER_COMPANY = new Fastjson2CompiledName("company");
        static final Fastjson2CompiledName USER_EMAIL = new Fastjson2CompiledName("email");
        static final Fastjson2CompiledName USER_PHONE = new Fastjson2CompiledName("phone");
        static final Fastjson2CompiledName USER_ADDRESS = new Fastjson2CompiledName("address");
        static final Fastjson2CompiledName USER_ABOUT = new Fastjson2CompiledName("about");
        static final Fastjson2CompiledName USER_REGISTERED = new Fastjson2CompiledName("registered");
        static final Fastjson2CompiledName USER_LATITUDE = new Fastjson2CompiledName("latitude");
        static final Fastjson2CompiledName USER_LONGITUDE = new Fastjson2CompiledName("longitude");
        static final Fastjson2CompiledName USER_TAGS = new Fastjson2CompiledName("tags");
        static final Fastjson2CompiledName USER_FRIENDS = new Fastjson2CompiledName("friends");
        static final Fastjson2CompiledName USER_GREETING = new Fastjson2CompiledName("greeting");
        static final Fastjson2CompiledName USER_FAVORITE_FRUIT = new Fastjson2CompiledName("favoriteFruit");
        static final Fastjson2CompiledName FRIEND_ID = new Fastjson2CompiledName("id");
        static final Fastjson2CompiledName FRIEND_NAME = new Fastjson2CompiledName("name");
    }

    private static final class Direct {
        static final long USERS_USERS_0 = new Fastjson2CompiledName("users").rawName0;
        static final long USER_ID_0 = new Fastjson2CompiledName("id").rawName0;
        static final long USER_INDEX_0 = new Fastjson2CompiledName("index").rawName0;
        static final long USER_GUID_0 = new Fastjson2CompiledName("guid").rawName0;
        static final long USER_IS_ACTIVE_0 = new Fastjson2CompiledName("isActive").rawName0;
        static final long USER_BALANCE_0 = new Fastjson2CompiledName("balance").rawName0;
        static final long USER_PICTURE_0 = new Fastjson2CompiledName("picture").rawName0;
        static final long USER_AGE_0 = new Fastjson2CompiledName("age").rawName0;
        static final long USER_EYE_COLOR_0 = new Fastjson2CompiledName("eyeColor").rawName0;
        static final long USER_NAME_0 = new Fastjson2CompiledName("name").rawName0;
        static final long USER_GENDER_0 = new Fastjson2CompiledName("gender").rawName0;
        static final long USER_COMPANY_0 = new Fastjson2CompiledName("company").rawName0;
        static final long USER_EMAIL_0 = new Fastjson2CompiledName("email").rawName0;
        static final long USER_PHONE_0 = new Fastjson2CompiledName("phone").rawName0;
        static final long USER_ADDRESS_0 = new Fastjson2CompiledName("address").rawName0;
        static final long USER_ABOUT_0 = new Fastjson2CompiledName("about").rawName0;
        static final long USER_REGISTERED_0 = new Fastjson2CompiledName("registered").rawName0;
        static final long USER_REGISTERED_1 = new Fastjson2CompiledName("registered").rawName1;
        static final long USER_LATITUDE_0 = new Fastjson2CompiledName("latitude").rawName0;
        static final long USER_LONGITUDE_0 = new Fastjson2CompiledName("longitude").rawName0;
        static final long USER_LONGITUDE_1 = new Fastjson2CompiledName("longitude").rawName1;
        static final long USER_TAGS_0 = new Fastjson2CompiledName("tags").rawName0;
        static final long USER_FRIENDS_0 = new Fastjson2CompiledName("friends").rawName0;
        static final long USER_GREETING_0 = new Fastjson2CompiledName("greeting").rawName0;
        static final long USER_FAVORITE_FRUIT_0 = new Fastjson2CompiledName("favoriteFruit").rawName0;
        static final long USER_FAVORITE_FRUIT_1 = new Fastjson2CompiledName("favoriteFruit").rawName1;
        static final long FRIEND_ID_0 = new Fastjson2CompiledName("id").rawName0;
        static final long FRIEND_NAME_0 = new Fastjson2CompiledName("name").rawName0;
    }

    private static byte[] writeHolder(Users value) throws Exception {
        try (Fastjson2Generator wrapper = BINDER.createGeneratorUtf8()) {
            JSONWriter w = wrapper.nativeGenerator();
            writeUsersHolder(w, value);
            return wrapper.toBytes();
        }
    }

    private static void writeUsersHolder(JSONWriter w, Users value) {
        if (value == null) {
            w.writeNull();
            return;
        }
        w.startObject();
        List<Users.User> users = value.getUsers();
        if (users == null) {
            if (INCLUDE_NULLS) {
                w.writeName5Raw(Holder.USERS_USERS.rawName0);
                w.writeNull();
            }
        } else {
            w.writeName5Raw(Holder.USERS_USERS.rawName0);
            w.startArray();
            if (users instanceof RandomAccess) {
                for (int i = 0, n = users.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    Users.User item = users.get(i);
                    if (item == null) w.writeNull();
                    else writeUserHolder(w, item);
                }
            } else {
                int index = 0;
                for (Users.User item : users) {
                    if (index++ != 0) w.writeComma();
                    if (item == null) w.writeNull();
                    else writeUserHolder(w, item);
                }
            }
            w.endArray();
        }
        w.endObject();
    }

    private static void writeUserHolder(JSONWriter w, Users.User value) {
        w.startObject();
        String propertyID = value.getId();
        if (propertyID != null || INCLUDE_NULLS) {
            w.writeName2Raw(Holder.USER_ID.rawName0);
            if (propertyID == null) w.writeNull();
            else w.writeString(propertyID);
        }
        w.writeName5Raw(Holder.USER_INDEX.rawName0);
        w.writeInt32(value.getIndex());
        String propertyGUID = value.getGuid();
        if (propertyGUID != null || INCLUDE_NULLS) {
            w.writeName4Raw(Holder.USER_GUID.rawName0);
            if (propertyGUID == null) w.writeNull();
            else w.writeString(propertyGUID);
        }
        w.writeName8Raw(Holder.USER_IS_ACTIVE.rawName0);
        w.writeBool(value.getIsActive());
        String propertyBALANCE = value.getBalance();
        if (propertyBALANCE != null || INCLUDE_NULLS) {
            w.writeName7Raw(Holder.USER_BALANCE.rawName0);
            if (propertyBALANCE == null) w.writeNull();
            else w.writeString(propertyBALANCE);
        }
        String propertyPICTURE = value.getPicture();
        if (propertyPICTURE != null || INCLUDE_NULLS) {
            w.writeName7Raw(Holder.USER_PICTURE.rawName0);
            if (propertyPICTURE == null) w.writeNull();
            else w.writeString(propertyPICTURE);
        }
        w.writeName3Raw(Holder.USER_AGE.rawName0);
        w.writeInt32(value.getAge());
        String propertyEYE_COLOR = value.getEyeColor();
        if (propertyEYE_COLOR != null || INCLUDE_NULLS) {
            w.writeName8Raw(Holder.USER_EYE_COLOR.rawName0);
            if (propertyEYE_COLOR == null) w.writeNull();
            else w.writeString(propertyEYE_COLOR);
        }
        String propertyNAME = value.getName();
        if (propertyNAME != null || INCLUDE_NULLS) {
            w.writeName4Raw(Holder.USER_NAME.rawName0);
            if (propertyNAME == null) w.writeNull();
            else w.writeString(propertyNAME);
        }
        String propertyGENDER = value.getGender();
        if (propertyGENDER != null || INCLUDE_NULLS) {
            w.writeName6Raw(Holder.USER_GENDER.rawName0);
            if (propertyGENDER == null) w.writeNull();
            else w.writeString(propertyGENDER);
        }
        String propertyCOMPANY = value.getCompany();
        if (propertyCOMPANY != null || INCLUDE_NULLS) {
            w.writeName7Raw(Holder.USER_COMPANY.rawName0);
            if (propertyCOMPANY == null) w.writeNull();
            else w.writeString(propertyCOMPANY);
        }
        String propertyEMAIL = value.getEmail();
        if (propertyEMAIL != null || INCLUDE_NULLS) {
            w.writeName5Raw(Holder.USER_EMAIL.rawName0);
            if (propertyEMAIL == null) w.writeNull();
            else w.writeString(propertyEMAIL);
        }
        String propertyPHONE = value.getPhone();
        if (propertyPHONE != null || INCLUDE_NULLS) {
            w.writeName5Raw(Holder.USER_PHONE.rawName0);
            if (propertyPHONE == null) w.writeNull();
            else w.writeString(propertyPHONE);
        }
        String propertyADDRESS = value.getAddress();
        if (propertyADDRESS != null || INCLUDE_NULLS) {
            w.writeName7Raw(Holder.USER_ADDRESS.rawName0);
            if (propertyADDRESS == null) w.writeNull();
            else w.writeString(propertyADDRESS);
        }
        String propertyABOUT = value.getAbout();
        if (propertyABOUT != null || INCLUDE_NULLS) {
            w.writeName5Raw(Holder.USER_ABOUT.rawName0);
            if (propertyABOUT == null) w.writeNull();
            else w.writeString(propertyABOUT);
        }
        String propertyREGISTERED = value.getRegistered();
        if (propertyREGISTERED != null || INCLUDE_NULLS) {
            w.writeName10Raw(Holder.USER_REGISTERED.rawName0, Holder.USER_REGISTERED.rawName1);
            if (propertyREGISTERED == null) w.writeNull();
            else w.writeString(propertyREGISTERED);
        }
        w.writeName8Raw(Holder.USER_LATITUDE.rawName0);
        w.writeDouble(value.getLatitude());
        w.writeName9Raw(Holder.USER_LONGITUDE.rawName0, (int) Holder.USER_LONGITUDE.rawName1);
        w.writeDouble(value.getLongitude());
        List<String> tags = value.getTags();
        if (tags == null) {
            if (INCLUDE_NULLS) {
                w.writeName4Raw(Holder.USER_TAGS.rawName0);
                w.writeNull();
            }
        } else {
            w.writeName4Raw(Holder.USER_TAGS.rawName0);
            w.startArray();
            if (tags instanceof RandomAccess) {
                for (int i = 0, n = tags.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    String element = tags.get(i);
                    if (element == null) w.writeNull();
                    else w.writeString(element);
                }
            } else {
                int index = 0;
                for (String element : tags) {
                    if (index++ != 0) w.writeComma();
                    if (element == null) w.writeNull();
                    else w.writeString(element);
                }
            }
            w.endArray();
        }
        List<Users.Friend> friends = value.getFriends();
        if (friends == null) {
            if (INCLUDE_NULLS) {
                w.writeName7Raw(Holder.USER_FRIENDS.rawName0);
                w.writeNull();
            }
        } else {
            w.writeName7Raw(Holder.USER_FRIENDS.rawName0);
            w.startArray();
            if (friends instanceof RandomAccess) {
                for (int i = 0, n = friends.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    Users.Friend element = friends.get(i);
                    if (element == null) w.writeNull();
                    else writeFriendHolder(w, element);
                }
            } else {
                int index = 0;
                for (Users.Friend element : friends) {
                    if (index++ != 0) w.writeComma();
                    if (element == null) w.writeNull();
                    else writeFriendHolder(w, element);
                }
            }
            w.endArray();
        }
        String propertyGREETING = value.getGreeting();
        if (propertyGREETING != null || INCLUDE_NULLS) {
            w.writeName8Raw(Holder.USER_GREETING.rawName0);
            if (propertyGREETING == null) w.writeNull();
            else w.writeString(propertyGREETING);
        }
        String propertyFAVORITE_FRUIT = value.getFavoriteFruit();
        if (propertyFAVORITE_FRUIT != null || INCLUDE_NULLS) {
            w.writeName13Raw(Holder.USER_FAVORITE_FRUIT.rawName0, Holder.USER_FAVORITE_FRUIT.rawName1);
            if (propertyFAVORITE_FRUIT == null) w.writeNull();
            else w.writeString(propertyFAVORITE_FRUIT);
        }
        w.endObject();
    }

    private static void writeFriendHolder(JSONWriter w, Users.Friend value) {
        w.startObject();
        String id = value.getId();
        if (id != null || INCLUDE_NULLS) {
            w.writeName2Raw(Holder.FRIEND_ID.rawName0);
            if (id == null) w.writeNull();
            else w.writeString(id);
        }
        String name = value.getName();
        if (name != null || INCLUDE_NULLS) {
            w.writeName4Raw(Holder.FRIEND_NAME.rawName0);
            if (name == null) w.writeNull();
            else w.writeString(name);
        }
        w.endObject();
    }

    private static byte[] writeDirect(Users value) throws Exception {
        try (Fastjson2Generator wrapper = BINDER.createGeneratorUtf8()) {
            JSONWriter w = wrapper.nativeGenerator();
            writeUsersDirect(w, value);
            return wrapper.toBytes();
        }
    }

    private static void writeUsersDirect(JSONWriter w, Users value) {
        if (value == null) {
            w.writeNull();
            return;
        }
        w.startObject();
        List<Users.User> users = value.getUsers();
        if (users == null) {
            if (INCLUDE_NULLS) {
                w.writeName5Raw(Direct.USERS_USERS_0);
                w.writeNull();
            }
        } else {
            w.writeName5Raw(Direct.USERS_USERS_0);
            w.startArray();
            if (users instanceof RandomAccess) {
                for (int i = 0, n = users.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    Users.User item = users.get(i);
                    if (item == null) w.writeNull();
                    else writeUserDirect(w, item);
                }
            } else {
                int index = 0;
                for (Users.User item : users) {
                    if (index++ != 0) w.writeComma();
                    if (item == null) w.writeNull();
                    else writeUserDirect(w, item);
                }
            }
            w.endArray();
        }
        w.endObject();
    }

    private static void writeUserDirect(JSONWriter w, Users.User value) {
        w.startObject();
        String propertyID = value.getId();
        if (propertyID != null || INCLUDE_NULLS) {
            w.writeName2Raw(Direct.USER_ID_0);
            if (propertyID == null) w.writeNull();
            else w.writeString(propertyID);
        }
        w.writeName5Raw(Direct.USER_INDEX_0);
        w.writeInt32(value.getIndex());
        String propertyGUID = value.getGuid();
        if (propertyGUID != null || INCLUDE_NULLS) {
            w.writeName4Raw(Direct.USER_GUID_0);
            if (propertyGUID == null) w.writeNull();
            else w.writeString(propertyGUID);
        }
        w.writeName8Raw(Direct.USER_IS_ACTIVE_0);
        w.writeBool(value.getIsActive());
        String propertyBALANCE = value.getBalance();
        if (propertyBALANCE != null || INCLUDE_NULLS) {
            w.writeName7Raw(Direct.USER_BALANCE_0);
            if (propertyBALANCE == null) w.writeNull();
            else w.writeString(propertyBALANCE);
        }
        String propertyPICTURE = value.getPicture();
        if (propertyPICTURE != null || INCLUDE_NULLS) {
            w.writeName7Raw(Direct.USER_PICTURE_0);
            if (propertyPICTURE == null) w.writeNull();
            else w.writeString(propertyPICTURE);
        }
        w.writeName3Raw(Direct.USER_AGE_0);
        w.writeInt32(value.getAge());
        String propertyEYE_COLOR = value.getEyeColor();
        if (propertyEYE_COLOR != null || INCLUDE_NULLS) {
            w.writeName8Raw(Direct.USER_EYE_COLOR_0);
            if (propertyEYE_COLOR == null) w.writeNull();
            else w.writeString(propertyEYE_COLOR);
        }
        String propertyNAME = value.getName();
        if (propertyNAME != null || INCLUDE_NULLS) {
            w.writeName4Raw(Direct.USER_NAME_0);
            if (propertyNAME == null) w.writeNull();
            else w.writeString(propertyNAME);
        }
        String propertyGENDER = value.getGender();
        if (propertyGENDER != null || INCLUDE_NULLS) {
            w.writeName6Raw(Direct.USER_GENDER_0);
            if (propertyGENDER == null) w.writeNull();
            else w.writeString(propertyGENDER);
        }
        String propertyCOMPANY = value.getCompany();
        if (propertyCOMPANY != null || INCLUDE_NULLS) {
            w.writeName7Raw(Direct.USER_COMPANY_0);
            if (propertyCOMPANY == null) w.writeNull();
            else w.writeString(propertyCOMPANY);
        }
        String propertyEMAIL = value.getEmail();
        if (propertyEMAIL != null || INCLUDE_NULLS) {
            w.writeName5Raw(Direct.USER_EMAIL_0);
            if (propertyEMAIL == null) w.writeNull();
            else w.writeString(propertyEMAIL);
        }
        String propertyPHONE = value.getPhone();
        if (propertyPHONE != null || INCLUDE_NULLS) {
            w.writeName5Raw(Direct.USER_PHONE_0);
            if (propertyPHONE == null) w.writeNull();
            else w.writeString(propertyPHONE);
        }
        String propertyADDRESS = value.getAddress();
        if (propertyADDRESS != null || INCLUDE_NULLS) {
            w.writeName7Raw(Direct.USER_ADDRESS_0);
            if (propertyADDRESS == null) w.writeNull();
            else w.writeString(propertyADDRESS);
        }
        String propertyABOUT = value.getAbout();
        if (propertyABOUT != null || INCLUDE_NULLS) {
            w.writeName5Raw(Direct.USER_ABOUT_0);
            if (propertyABOUT == null) w.writeNull();
            else w.writeString(propertyABOUT);
        }
        String propertyREGISTERED = value.getRegistered();
        if (propertyREGISTERED != null || INCLUDE_NULLS) {
            w.writeName10Raw(Direct.USER_REGISTERED_0, Direct.USER_REGISTERED_1);
            if (propertyREGISTERED == null) w.writeNull();
            else w.writeString(propertyREGISTERED);
        }
        w.writeName8Raw(Direct.USER_LATITUDE_0);
        w.writeDouble(value.getLatitude());
        w.writeName9Raw(Direct.USER_LONGITUDE_0, (int) Direct.USER_LONGITUDE_1);
        w.writeDouble(value.getLongitude());
        List<String> tags = value.getTags();
        if (tags == null) {
            if (INCLUDE_NULLS) {
                w.writeName4Raw(Direct.USER_TAGS_0);
                w.writeNull();
            }
        } else {
            w.writeName4Raw(Direct.USER_TAGS_0);
            w.startArray();
            if (tags instanceof RandomAccess) {
                for (int i = 0, n = tags.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    String element = tags.get(i);
                    if (element == null) w.writeNull();
                    else w.writeString(element);
                }
            } else {
                int index = 0;
                for (String element : tags) {
                    if (index++ != 0) w.writeComma();
                    if (element == null) w.writeNull();
                    else w.writeString(element);
                }
            }
            w.endArray();
        }
        List<Users.Friend> friends = value.getFriends();
        if (friends == null) {
            if (INCLUDE_NULLS) {
                w.writeName7Raw(Direct.USER_FRIENDS_0);
                w.writeNull();
            }
        } else {
            w.writeName7Raw(Direct.USER_FRIENDS_0);
            w.startArray();
            if (friends instanceof RandomAccess) {
                for (int i = 0, n = friends.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    Users.Friend element = friends.get(i);
                    if (element == null) w.writeNull();
                    else writeFriendDirect(w, element);
                }
            } else {
                int index = 0;
                for (Users.Friend element : friends) {
                    if (index++ != 0) w.writeComma();
                    if (element == null) w.writeNull();
                    else writeFriendDirect(w, element);
                }
            }
            w.endArray();
        }
        String propertyGREETING = value.getGreeting();
        if (propertyGREETING != null || INCLUDE_NULLS) {
            w.writeName8Raw(Direct.USER_GREETING_0);
            if (propertyGREETING == null) w.writeNull();
            else w.writeString(propertyGREETING);
        }
        String propertyFAVORITE_FRUIT = value.getFavoriteFruit();
        if (propertyFAVORITE_FRUIT != null || INCLUDE_NULLS) {
            w.writeName13Raw(Direct.USER_FAVORITE_FRUIT_0, Direct.USER_FAVORITE_FRUIT_1);
            if (propertyFAVORITE_FRUIT == null) w.writeNull();
            else w.writeString(propertyFAVORITE_FRUIT);
        }
        w.endObject();
    }

    private static void writeFriendDirect(JSONWriter w, Users.Friend value) {
        w.startObject();
        String id = value.getId();
        if (id != null || INCLUDE_NULLS) {
            w.writeName2Raw(Direct.FRIEND_ID_0);
            if (id == null) w.writeNull();
            else w.writeString(id);
        }
        String name = value.getName();
        if (name != null || INCLUDE_NULLS) {
            w.writeName4Raw(Direct.FRIEND_NAME_0);
            if (name == null) w.writeNull();
            else w.writeString(name);
        }
        w.endObject();
    }


    /** Actual javac-inlinable name constants, packed for little-endian x86_64. */
    private static final class Literal {
        static final long USERS_USERS_0 = 0x3a22737265737522L;
        static final long USER_ID_0 = 0x0000003a22646922L;
        static final long USER_INDEX_0 = 0x3a227865646e6922L;
        static final long USER_GUID_0 = 0x003a226469756722L;
        static final long USER_IS_ACTIVE_0 = 0x6576697463417369L;
        static final long USER_BALANCE_0 = 0x65636e616c616222L;
        static final long USER_PICTURE_0 = 0x6572757463697022L;
        static final long USER_AGE_0 = 0x00003a2265676122L;
        static final long USER_EYE_COLOR_0 = 0x726f6c6f43657965L;
        static final long USER_NAME_0 = 0x003a22656d616e22L;
        static final long USER_GENDER_0 = 0x227265646e656722L;
        static final long USER_COMPANY_0 = 0x796e61706d6f6322L;
        static final long USER_EMAIL_0 = 0x3a226c69616d6522L;
        static final long USER_PHONE_0 = 0x3a22656e6f687022L;
        static final long USER_ADDRESS_0 = 0x7373657264646122L;
        static final long USER_ABOUT_0 = 0x3a2274756f626122L;
        static final long USER_REGISTERED_0 = 0x6574736967657222L;
        static final long USER_REGISTERED_1 = 0x0000003a22646572L;
        static final long USER_LATITUDE_0 = 0x656475746974616cL;
        static final long USER_LONGITUDE_0 = 0x757469676e6f6c22L;
        static final long USER_LONGITUDE_1 = 0x3a226564L;
        static final long USER_TAGS_0 = 0x003a227367617422L;
        static final long USER_FRIENDS_0 = 0x73646e6569726622L;
        static final long USER_GREETING_0 = 0x676e697465657267L;
        static final long USER_FAVORITE_FRUIT_0 = 0x7469726f76616622L;
        static final long USER_FAVORITE_FRUIT_1 = 0x3a22746975724665L;
        static final long FRIEND_ID_0 = 0x0000003a22646922L;
        static final long FRIEND_NAME_0 = 0x003a22656d616e22L;
    }

    private static byte[] writeLiteral(Users value) throws Exception {
        try (Fastjson2Generator wrapper = BINDER.createGeneratorUtf8()) {
            JSONWriter w = wrapper.nativeGenerator();
            writeUsersLiteral(w, value);
            return wrapper.toBytes();
        }
    }

    private static void writeUsersLiteral(JSONWriter w, Users value) {
        if (value == null) {
            w.writeNull();
            return;
        }
        w.startObject();
        List<Users.User> users = value.getUsers();
        if (users == null) {
            if (INCLUDE_NULLS) {
                w.writeName5Raw(Literal.USERS_USERS_0);
                w.writeNull();
            }
        } else {
            w.writeName5Raw(Literal.USERS_USERS_0);
            w.startArray();
            if (users instanceof RandomAccess) {
                for (int i = 0, n = users.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    Users.User item = users.get(i);
                    if (item == null) w.writeNull();
                    else writeUserLiteral(w, item);
                }
            } else {
                int index = 0;
                for (Users.User item : users) {
                    if (index++ != 0) w.writeComma();
                    if (item == null) w.writeNull();
                    else writeUserLiteral(w, item);
                }
            }
            w.endArray();
        }
        w.endObject();
    }

    private static void writeUserLiteral(JSONWriter w, Users.User value) {
        w.startObject();
        String propertyID = value.getId();
        if (propertyID != null || INCLUDE_NULLS) {
            w.writeName2Raw(Literal.USER_ID_0);
            if (propertyID == null) w.writeNull();
            else w.writeString(propertyID);
        }
        w.writeName5Raw(Literal.USER_INDEX_0);
        w.writeInt32(value.getIndex());
        String propertyGUID = value.getGuid();
        if (propertyGUID != null || INCLUDE_NULLS) {
            w.writeName4Raw(Literal.USER_GUID_0);
            if (propertyGUID == null) w.writeNull();
            else w.writeString(propertyGUID);
        }
        w.writeName8Raw(Literal.USER_IS_ACTIVE_0);
        w.writeBool(value.getIsActive());
        String propertyBALANCE = value.getBalance();
        if (propertyBALANCE != null || INCLUDE_NULLS) {
            w.writeName7Raw(Literal.USER_BALANCE_0);
            if (propertyBALANCE == null) w.writeNull();
            else w.writeString(propertyBALANCE);
        }
        String propertyPICTURE = value.getPicture();
        if (propertyPICTURE != null || INCLUDE_NULLS) {
            w.writeName7Raw(Literal.USER_PICTURE_0);
            if (propertyPICTURE == null) w.writeNull();
            else w.writeString(propertyPICTURE);
        }
        w.writeName3Raw(Literal.USER_AGE_0);
        w.writeInt32(value.getAge());
        String propertyEYE_COLOR = value.getEyeColor();
        if (propertyEYE_COLOR != null || INCLUDE_NULLS) {
            w.writeName8Raw(Literal.USER_EYE_COLOR_0);
            if (propertyEYE_COLOR == null) w.writeNull();
            else w.writeString(propertyEYE_COLOR);
        }
        String propertyNAME = value.getName();
        if (propertyNAME != null || INCLUDE_NULLS) {
            w.writeName4Raw(Literal.USER_NAME_0);
            if (propertyNAME == null) w.writeNull();
            else w.writeString(propertyNAME);
        }
        String propertyGENDER = value.getGender();
        if (propertyGENDER != null || INCLUDE_NULLS) {
            w.writeName6Raw(Literal.USER_GENDER_0);
            if (propertyGENDER == null) w.writeNull();
            else w.writeString(propertyGENDER);
        }
        String propertyCOMPANY = value.getCompany();
        if (propertyCOMPANY != null || INCLUDE_NULLS) {
            w.writeName7Raw(Literal.USER_COMPANY_0);
            if (propertyCOMPANY == null) w.writeNull();
            else w.writeString(propertyCOMPANY);
        }
        String propertyEMAIL = value.getEmail();
        if (propertyEMAIL != null || INCLUDE_NULLS) {
            w.writeName5Raw(Literal.USER_EMAIL_0);
            if (propertyEMAIL == null) w.writeNull();
            else w.writeString(propertyEMAIL);
        }
        String propertyPHONE = value.getPhone();
        if (propertyPHONE != null || INCLUDE_NULLS) {
            w.writeName5Raw(Literal.USER_PHONE_0);
            if (propertyPHONE == null) w.writeNull();
            else w.writeString(propertyPHONE);
        }
        String propertyADDRESS = value.getAddress();
        if (propertyADDRESS != null || INCLUDE_NULLS) {
            w.writeName7Raw(Literal.USER_ADDRESS_0);
            if (propertyADDRESS == null) w.writeNull();
            else w.writeString(propertyADDRESS);
        }
        String propertyABOUT = value.getAbout();
        if (propertyABOUT != null || INCLUDE_NULLS) {
            w.writeName5Raw(Literal.USER_ABOUT_0);
            if (propertyABOUT == null) w.writeNull();
            else w.writeString(propertyABOUT);
        }
        String propertyREGISTERED = value.getRegistered();
        if (propertyREGISTERED != null || INCLUDE_NULLS) {
            w.writeName10Raw(Literal.USER_REGISTERED_0, Literal.USER_REGISTERED_1);
            if (propertyREGISTERED == null) w.writeNull();
            else w.writeString(propertyREGISTERED);
        }
        w.writeName8Raw(Literal.USER_LATITUDE_0);
        w.writeDouble(value.getLatitude());
        w.writeName9Raw(Literal.USER_LONGITUDE_0, (int) Literal.USER_LONGITUDE_1);
        w.writeDouble(value.getLongitude());
        List<String> tags = value.getTags();
        if (tags == null) {
            if (INCLUDE_NULLS) {
                w.writeName4Raw(Literal.USER_TAGS_0);
                w.writeNull();
            }
        } else {
            w.writeName4Raw(Literal.USER_TAGS_0);
            w.startArray();
            if (tags instanceof RandomAccess) {
                for (int i = 0, n = tags.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    String element = tags.get(i);
                    if (element == null) w.writeNull();
                    else w.writeString(element);
                }
            } else {
                int index = 0;
                for (String element : tags) {
                    if (index++ != 0) w.writeComma();
                    if (element == null) w.writeNull();
                    else w.writeString(element);
                }
            }
            w.endArray();
        }
        List<Users.Friend> friends = value.getFriends();
        if (friends == null) {
            if (INCLUDE_NULLS) {
                w.writeName7Raw(Literal.USER_FRIENDS_0);
                w.writeNull();
            }
        } else {
            w.writeName7Raw(Literal.USER_FRIENDS_0);
            w.startArray();
            if (friends instanceof RandomAccess) {
                for (int i = 0, n = friends.size(); i < n; i++) {
                    if (i != 0) w.writeComma();
                    Users.Friend element = friends.get(i);
                    if (element == null) w.writeNull();
                    else writeFriendLiteral(w, element);
                }
            } else {
                int index = 0;
                for (Users.Friend element : friends) {
                    if (index++ != 0) w.writeComma();
                    if (element == null) w.writeNull();
                    else writeFriendLiteral(w, element);
                }
            }
            w.endArray();
        }
        String propertyGREETING = value.getGreeting();
        if (propertyGREETING != null || INCLUDE_NULLS) {
            w.writeName8Raw(Literal.USER_GREETING_0);
            if (propertyGREETING == null) w.writeNull();
            else w.writeString(propertyGREETING);
        }
        String propertyFAVORITE_FRUIT = value.getFavoriteFruit();
        if (propertyFAVORITE_FRUIT != null || INCLUDE_NULLS) {
            w.writeName13Raw(Literal.USER_FAVORITE_FRUIT_0, Literal.USER_FAVORITE_FRUIT_1);
            if (propertyFAVORITE_FRUIT == null) w.writeNull();
            else w.writeString(propertyFAVORITE_FRUIT);
        }
        w.endObject();
    }

    private static void writeFriendLiteral(JSONWriter w, Users.Friend value) {
        w.startObject();
        String id = value.getId();
        if (id != null || INCLUDE_NULLS) {
            w.writeName2Raw(Literal.FRIEND_ID_0);
            if (id == null) w.writeNull();
            else w.writeString(id);
        }
        String name = value.getName();
        if (name != null || INCLUDE_NULLS) {
            w.writeName4Raw(Literal.FRIEND_NAME_0);
            if (name == null) w.writeNull();
            else w.writeString(name);
        }
        w.endObject();
    }

}
