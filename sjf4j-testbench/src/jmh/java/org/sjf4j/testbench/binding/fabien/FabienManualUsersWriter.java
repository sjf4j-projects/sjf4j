package org.sjf4j.testbench.binding.fabien;

import org.sjf4j.NodeConfig;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Generator;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Generator;
import org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName;
import org.sjf4j.testbench.model.fabien.Users;
import tools.jackson.core.io.SerializedString;
import java.io.IOException;

/** Handwritten native output for Fabien Users; two null-policy strategies, no APT generated helpers. */
final class FabienManualUsersWriter {
    private static final Jackson3Binder J_BINDER = new Jackson3Binder();
    private static final Fastjson2Binder F_BINDER = new Fastjson2Binder();
    // Deliberately non-constant: otherwise C2 can constant-fold the runtime variant
    // and make it indistinguishable from the compile-time-fixed experiment.
    private static volatile NodeConfig runtimeConfig = NodeConfig.DEFAULT;

    private static final SerializedString J_USERS = new SerializedString("users");
    private static final SerializedString J_ID = new SerializedString("id");
    private static final SerializedString J_INDEX = new SerializedString("index");
    private static final SerializedString J_GUID = new SerializedString("guid");
    private static final SerializedString J_ISACTIVE = new SerializedString("isActive");
    private static final SerializedString J_BALANCE = new SerializedString("balance");
    private static final SerializedString J_PICTURE = new SerializedString("picture");
    private static final SerializedString J_AGE = new SerializedString("age");
    private static final SerializedString J_EYECOLOR = new SerializedString("eyeColor");
    private static final SerializedString J_NAME = new SerializedString("name");
    private static final SerializedString J_GENDER = new SerializedString("gender");
    private static final SerializedString J_COMPANY = new SerializedString("company");
    private static final SerializedString J_EMAIL = new SerializedString("email");
    private static final SerializedString J_PHONE = new SerializedString("phone");
    private static final SerializedString J_ADDRESS = new SerializedString("address");
    private static final SerializedString J_ABOUT = new SerializedString("about");
    private static final SerializedString J_REGISTERED = new SerializedString("registered");
    private static final SerializedString J_LATITUDE = new SerializedString("latitude");
    private static final SerializedString J_LONGITUDE = new SerializedString("longitude");
    private static final SerializedString J_TAGS = new SerializedString("tags");
    private static final SerializedString J_FRIENDS = new SerializedString("friends");
    private static final SerializedString J_GREETING = new SerializedString("greeting");
    private static final SerializedString J_FAVORITEFRUIT = new SerializedString("favoriteFruit");

    private static final Fastjson2CompiledName F_USERS = new Fastjson2CompiledName("users");
    private static final Fastjson2CompiledName F_ID = new Fastjson2CompiledName("id");
    private static final Fastjson2CompiledName F_INDEX = new Fastjson2CompiledName("index");
    private static final Fastjson2CompiledName F_GUID = new Fastjson2CompiledName("guid");
    private static final Fastjson2CompiledName F_ISACTIVE = new Fastjson2CompiledName("isActive");
    private static final Fastjson2CompiledName F_BALANCE = new Fastjson2CompiledName("balance");
    private static final Fastjson2CompiledName F_PICTURE = new Fastjson2CompiledName("picture");
    private static final Fastjson2CompiledName F_AGE = new Fastjson2CompiledName("age");
    private static final Fastjson2CompiledName F_EYECOLOR = new Fastjson2CompiledName("eyeColor");
    private static final Fastjson2CompiledName F_NAME = new Fastjson2CompiledName("name");
    private static final Fastjson2CompiledName F_GENDER = new Fastjson2CompiledName("gender");
    private static final Fastjson2CompiledName F_COMPANY = new Fastjson2CompiledName("company");
    private static final Fastjson2CompiledName F_EMAIL = new Fastjson2CompiledName("email");
    private static final Fastjson2CompiledName F_PHONE = new Fastjson2CompiledName("phone");
    private static final Fastjson2CompiledName F_ADDRESS = new Fastjson2CompiledName("address");
    private static final Fastjson2CompiledName F_ABOUT = new Fastjson2CompiledName("about");
    private static final Fastjson2CompiledName F_REGISTERED = new Fastjson2CompiledName("registered");
    private static final Fastjson2CompiledName F_LATITUDE = new Fastjson2CompiledName("latitude");
    private static final Fastjson2CompiledName F_LONGITUDE = new Fastjson2CompiledName("longitude");
    private static final Fastjson2CompiledName F_TAGS = new Fastjson2CompiledName("tags");
    private static final Fastjson2CompiledName F_FRIENDS = new Fastjson2CompiledName("friends");
    private static final Fastjson2CompiledName F_GREETING = new Fastjson2CompiledName("greeting");
    private static final Fastjson2CompiledName F_FAVORITEFRUIT = new Fastjson2CompiledName("favoriteFruit");

    static byte[] jacksonFixed(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (Jackson3Generator gen = J_BINDER.createGenerator(output)) {
                gen.start();
                jusersFixed(gen.nativeGenerator(), value);
                gen.end();
            }
            return output.toByteArray();
        } finally { output.recycle(); }
    }
    static byte[] jacksonRuntime(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (Jackson3Generator gen = J_BINDER.createGenerator(output)) {
                gen.start();
                jusersRuntime(gen.nativeGenerator(), value, runtimeConfig);
                gen.end();
            }
            return output.toByteArray();
        } finally { output.recycle(); }
    }
    static byte[] fastjsonFixed(Users value) throws IOException {
        try (Fastjson2Generator gen = F_BINDER.createGeneratorUtf8()) {
            gen.start();
            fusersFixed(gen.nativeGenerator(), value);
            gen.end();
            return gen.toBytes();
        }
    }
    static byte[] fastjsonRuntime(Users value) throws IOException {
        try (Fastjson2Generator gen = F_BINDER.createGeneratorUtf8()) {
            gen.start();
            fusersRuntime(gen.nativeGenerator(), value, runtimeConfig);
            gen.end();
            return gen.toBytes();
        }
    }

    private static void jusersFixed(tools.jackson.core.JsonGenerator w, Users value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.writeStartObject();
        w.writeName(J_USERS);
        juserListFixed(w, value.getUsers());
        w.writeEndObject();
    }
    private static void juserFixed(tools.jackson.core.JsonGenerator w, Users.User value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.writeStartObject();
        w.writeName(J_ID);
        w.writeString(value.getId());
        w.writeName(J_INDEX);
        w.writeNumber(value.getIndex());
        w.writeName(J_GUID);
        w.writeString(value.getGuid());
        w.writeName(J_ISACTIVE);
        w.writeBoolean(value.getIsActive());
        w.writeName(J_BALANCE);
        w.writeString(value.getBalance());
        w.writeName(J_PICTURE);
        w.writeString(value.getPicture());
        w.writeName(J_AGE);
        w.writeNumber(value.getAge());
        w.writeName(J_EYECOLOR);
        w.writeString(value.getEyeColor());
        w.writeName(J_NAME);
        w.writeString(value.getName());
        w.writeName(J_GENDER);
        w.writeString(value.getGender());
        w.writeName(J_COMPANY);
        w.writeString(value.getCompany());
        w.writeName(J_EMAIL);
        w.writeString(value.getEmail());
        w.writeName(J_PHONE);
        w.writeString(value.getPhone());
        w.writeName(J_ADDRESS);
        w.writeString(value.getAddress());
        w.writeName(J_ABOUT);
        w.writeString(value.getAbout());
        w.writeName(J_REGISTERED);
        w.writeString(value.getRegistered());
        w.writeName(J_LATITUDE);
        w.writeNumber(value.getLatitude());
        w.writeName(J_LONGITUDE);
        w.writeNumber(value.getLongitude());
        w.writeName(J_TAGS);
        jstringsFixed(w, value.getTags());
        w.writeName(J_FRIENDS);
        jfriendsFixed(w, value.getFriends());
        w.writeName(J_GREETING);
        w.writeString(value.getGreeting());
        w.writeName(J_FAVORITEFRUIT);
        w.writeString(value.getFavoriteFruit());
        w.writeEndObject();
    }
    private static void jfriendFixed(tools.jackson.core.JsonGenerator w, Users.Friend value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.writeStartObject();
        w.writeName(J_ID);
        w.writeString(value.getId());
        w.writeName(J_NAME);
        w.writeString(value.getName());
        w.writeEndObject();
    }
    private static void jstringsFixed(tools.jackson.core.JsonGenerator w, java.util.List<String> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.writeStartArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            String item = values.get(i);
w.writeString(item);
        }
        w.writeEndArray();
    }
    private static void jfriendsFixed(tools.jackson.core.JsonGenerator w, java.util.List<Users.Friend> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.writeStartArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            Users.Friend item = values.get(i);
            jfriendFixed(w, item);
        }
        w.writeEndArray();
    }
    private static void juserListFixed(tools.jackson.core.JsonGenerator w, java.util.List<Users.User> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.writeStartArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            Users.User item = values.get(i);
            juserFixed(w, item);
        }
        w.writeEndArray();
    }
    private static void jusersRuntime(tools.jackson.core.JsonGenerator w, Users value, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.writeStartObject();
        w.writeName(J_USERS);
        juserListRuntime(w, value.getUsers(), config);
        w.writeEndObject();
    }
    private static void juserRuntime(tools.jackson.core.JsonGenerator w, Users.User value, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.writeStartObject();
        String v_id = value.getId();
        if (v_id != null || config.includeNulls) {
            w.writeName(J_ID);
            w.writeString(v_id);
        }
        w.writeName(J_INDEX);
        w.writeNumber(value.getIndex());
        String v_guid = value.getGuid();
        if (v_guid != null || config.includeNulls) {
            w.writeName(J_GUID);
            w.writeString(v_guid);
        }
        w.writeName(J_ISACTIVE);
        w.writeBoolean(value.getIsActive());
        String v_balance = value.getBalance();
        if (v_balance != null || config.includeNulls) {
            w.writeName(J_BALANCE);
            w.writeString(v_balance);
        }
        String v_picture = value.getPicture();
        if (v_picture != null || config.includeNulls) {
            w.writeName(J_PICTURE);
            w.writeString(v_picture);
        }
        w.writeName(J_AGE);
        w.writeNumber(value.getAge());
        String v_eyeColor = value.getEyeColor();
        if (v_eyeColor != null || config.includeNulls) {
            w.writeName(J_EYECOLOR);
            w.writeString(v_eyeColor);
        }
        String v_name = value.getName();
        if (v_name != null || config.includeNulls) {
            w.writeName(J_NAME);
            w.writeString(v_name);
        }
        String v_gender = value.getGender();
        if (v_gender != null || config.includeNulls) {
            w.writeName(J_GENDER);
            w.writeString(v_gender);
        }
        String v_company = value.getCompany();
        if (v_company != null || config.includeNulls) {
            w.writeName(J_COMPANY);
            w.writeString(v_company);
        }
        String v_email = value.getEmail();
        if (v_email != null || config.includeNulls) {
            w.writeName(J_EMAIL);
            w.writeString(v_email);
        }
        String v_phone = value.getPhone();
        if (v_phone != null || config.includeNulls) {
            w.writeName(J_PHONE);
            w.writeString(v_phone);
        }
        String v_address = value.getAddress();
        if (v_address != null || config.includeNulls) {
            w.writeName(J_ADDRESS);
            w.writeString(v_address);
        }
        String v_about = value.getAbout();
        if (v_about != null || config.includeNulls) {
            w.writeName(J_ABOUT);
            w.writeString(v_about);
        }
        String v_registered = value.getRegistered();
        if (v_registered != null || config.includeNulls) {
            w.writeName(J_REGISTERED);
            w.writeString(v_registered);
        }
        w.writeName(J_LATITUDE);
        w.writeNumber(value.getLatitude());
        w.writeName(J_LONGITUDE);
        w.writeNumber(value.getLongitude());
        w.writeName(J_TAGS);
        jstringsRuntime(w, value.getTags(), config);
        w.writeName(J_FRIENDS);
        jfriendsRuntime(w, value.getFriends(), config);
        String v_greeting = value.getGreeting();
        if (v_greeting != null || config.includeNulls) {
            w.writeName(J_GREETING);
            w.writeString(v_greeting);
        }
        String v_favoriteFruit = value.getFavoriteFruit();
        if (v_favoriteFruit != null || config.includeNulls) {
            w.writeName(J_FAVORITEFRUIT);
            w.writeString(v_favoriteFruit);
        }
        w.writeEndObject();
    }
    private static void jfriendRuntime(tools.jackson.core.JsonGenerator w, Users.Friend value, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.writeStartObject();
        String v_id = value.getId();
        if (v_id != null || config.includeNulls) {
            w.writeName(J_ID);
            w.writeString(v_id);
        }
        String v_name = value.getName();
        if (v_name != null || config.includeNulls) {
            w.writeName(J_NAME);
            w.writeString(v_name);
        }
        w.writeEndObject();
    }
    private static void jstringsRuntime(tools.jackson.core.JsonGenerator w, java.util.List<String> values, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.writeStartArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            String item = values.get(i);
w.writeString(item);
        }
        w.writeEndArray();
    }
    private static void jfriendsRuntime(tools.jackson.core.JsonGenerator w, java.util.List<Users.Friend> values, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.writeStartArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            Users.Friend item = values.get(i);
            jfriendRuntime(w, item, config);
        }
        w.writeEndArray();
    }
    private static void juserListRuntime(tools.jackson.core.JsonGenerator w, java.util.List<Users.User> values, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.writeStartArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            Users.User item = values.get(i);
            juserRuntime(w, item, config);
        }
        w.writeEndArray();
    }
    private static void fusersFixed(com.alibaba.fastjson2.JSONWriter w, Users value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName5Raw(F_USERS.rawName0);
        fuserListFixed(w, value.getUsers());
        w.endObject();
    }
    private static void fuserFixed(com.alibaba.fastjson2.JSONWriter w, Users.User value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName2Raw(F_ID.rawName0);
        w.writeString(value.getId());
        w.writeName5Raw(F_INDEX.rawName0);
        w.writeInt32(value.getIndex());
        w.writeName4Raw(F_GUID.rawName0);
        w.writeString(value.getGuid());
        w.writeName8Raw(F_ISACTIVE.rawName0);
        w.writeBool(value.getIsActive());
        w.writeName7Raw(F_BALANCE.rawName0);
        w.writeString(value.getBalance());
        w.writeName7Raw(F_PICTURE.rawName0);
        w.writeString(value.getPicture());
        w.writeName3Raw(F_AGE.rawName0);
        w.writeInt32(value.getAge());
        w.writeName8Raw(F_EYECOLOR.rawName0);
        w.writeString(value.getEyeColor());
        w.writeName4Raw(F_NAME.rawName0);
        w.writeString(value.getName());
        w.writeName6Raw(F_GENDER.rawName0);
        w.writeString(value.getGender());
        w.writeName7Raw(F_COMPANY.rawName0);
        w.writeString(value.getCompany());
        w.writeName5Raw(F_EMAIL.rawName0);
        w.writeString(value.getEmail());
        w.writeName5Raw(F_PHONE.rawName0);
        w.writeString(value.getPhone());
        w.writeName7Raw(F_ADDRESS.rawName0);
        w.writeString(value.getAddress());
        w.writeName5Raw(F_ABOUT.rawName0);
        w.writeString(value.getAbout());
        w.writeName10Raw(F_REGISTERED.rawName0, F_REGISTERED.rawName1);
        w.writeString(value.getRegistered());
        w.writeName8Raw(F_LATITUDE.rawName0);
        w.writeDouble(value.getLatitude());
        w.writeName9Raw(F_LONGITUDE.rawName0, (int) F_LONGITUDE.rawName1);
        w.writeDouble(value.getLongitude());
        w.writeName4Raw(F_TAGS.rawName0);
        fstringsFixed(w, value.getTags());
        w.writeName7Raw(F_FRIENDS.rawName0);
        ffriendsFixed(w, value.getFriends());
        w.writeName8Raw(F_GREETING.rawName0);
        w.writeString(value.getGreeting());
        w.writeName13Raw(F_FAVORITEFRUIT.rawName0, F_FAVORITEFRUIT.rawName1);
        w.writeString(value.getFavoriteFruit());
        w.endObject();
    }
    private static void ffriendFixed(com.alibaba.fastjson2.JSONWriter w, Users.Friend value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName2Raw(F_ID.rawName0);
        w.writeString(value.getId());
        w.writeName4Raw(F_NAME.rawName0);
        w.writeString(value.getName());
        w.endObject();
    }
    private static void fstringsFixed(com.alibaba.fastjson2.JSONWriter w, java.util.List<String> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            String item = values.get(i);
            if (item == null) w.writeNull(); else w.writeString(item);
        }
        w.endArray();
    }
    private static void ffriendsFixed(com.alibaba.fastjson2.JSONWriter w, java.util.List<Users.Friend> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            Users.Friend item = values.get(i);
            ffriendFixed(w, item);
        }
        w.endArray();
    }
    private static void fuserListFixed(com.alibaba.fastjson2.JSONWriter w, java.util.List<Users.User> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            Users.User item = values.get(i);
            fuserFixed(w, item);
        }
        w.endArray();
    }
    private static void fusersRuntime(com.alibaba.fastjson2.JSONWriter w, Users value, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName5Raw(F_USERS.rawName0);
        fuserListRuntime(w, value.getUsers(), config);
        w.endObject();
    }
    private static void fuserRuntime(com.alibaba.fastjson2.JSONWriter w, Users.User value, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        String v_id = value.getId();
        if (v_id != null || config.includeNulls) {
            w.writeName2Raw(F_ID.rawName0);
            w.writeString(v_id);
        }
        w.writeName5Raw(F_INDEX.rawName0);
        w.writeInt32(value.getIndex());
        String v_guid = value.getGuid();
        if (v_guid != null || config.includeNulls) {
            w.writeName4Raw(F_GUID.rawName0);
            w.writeString(v_guid);
        }
        w.writeName8Raw(F_ISACTIVE.rawName0);
        w.writeBool(value.getIsActive());
        String v_balance = value.getBalance();
        if (v_balance != null || config.includeNulls) {
            w.writeName7Raw(F_BALANCE.rawName0);
            w.writeString(v_balance);
        }
        String v_picture = value.getPicture();
        if (v_picture != null || config.includeNulls) {
            w.writeName7Raw(F_PICTURE.rawName0);
            w.writeString(v_picture);
        }
        w.writeName3Raw(F_AGE.rawName0);
        w.writeInt32(value.getAge());
        String v_eyeColor = value.getEyeColor();
        if (v_eyeColor != null || config.includeNulls) {
            w.writeName8Raw(F_EYECOLOR.rawName0);
            w.writeString(v_eyeColor);
        }
        String v_name = value.getName();
        if (v_name != null || config.includeNulls) {
            w.writeName4Raw(F_NAME.rawName0);
            w.writeString(v_name);
        }
        String v_gender = value.getGender();
        if (v_gender != null || config.includeNulls) {
            w.writeName6Raw(F_GENDER.rawName0);
            w.writeString(v_gender);
        }
        String v_company = value.getCompany();
        if (v_company != null || config.includeNulls) {
            w.writeName7Raw(F_COMPANY.rawName0);
            w.writeString(v_company);
        }
        String v_email = value.getEmail();
        if (v_email != null || config.includeNulls) {
            w.writeName5Raw(F_EMAIL.rawName0);
            w.writeString(v_email);
        }
        String v_phone = value.getPhone();
        if (v_phone != null || config.includeNulls) {
            w.writeName5Raw(F_PHONE.rawName0);
            w.writeString(v_phone);
        }
        String v_address = value.getAddress();
        if (v_address != null || config.includeNulls) {
            w.writeName7Raw(F_ADDRESS.rawName0);
            w.writeString(v_address);
        }
        String v_about = value.getAbout();
        if (v_about != null || config.includeNulls) {
            w.writeName5Raw(F_ABOUT.rawName0);
            w.writeString(v_about);
        }
        String v_registered = value.getRegistered();
        if (v_registered != null || config.includeNulls) {
            w.writeName10Raw(F_REGISTERED.rawName0, F_REGISTERED.rawName1);
            w.writeString(v_registered);
        }
        w.writeName8Raw(F_LATITUDE.rawName0);
        w.writeDouble(value.getLatitude());
        w.writeName9Raw(F_LONGITUDE.rawName0, (int) F_LONGITUDE.rawName1);
        w.writeDouble(value.getLongitude());
        w.writeName4Raw(F_TAGS.rawName0);
        fstringsRuntime(w, value.getTags(), config);
        w.writeName7Raw(F_FRIENDS.rawName0);
        ffriendsRuntime(w, value.getFriends(), config);
        String v_greeting = value.getGreeting();
        if (v_greeting != null || config.includeNulls) {
            w.writeName8Raw(F_GREETING.rawName0);
            w.writeString(v_greeting);
        }
        String v_favoriteFruit = value.getFavoriteFruit();
        if (v_favoriteFruit != null || config.includeNulls) {
            w.writeName13Raw(F_FAVORITEFRUIT.rawName0, F_FAVORITEFRUIT.rawName1);
            w.writeString(v_favoriteFruit);
        }
        w.endObject();
    }
    private static void ffriendRuntime(com.alibaba.fastjson2.JSONWriter w, Users.Friend value, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        String v_id = value.getId();
        if (v_id != null || config.includeNulls) {
            w.writeName2Raw(F_ID.rawName0);
            w.writeString(v_id);
        }
        String v_name = value.getName();
        if (v_name != null || config.includeNulls) {
            w.writeName4Raw(F_NAME.rawName0);
            w.writeString(v_name);
        }
        w.endObject();
    }
    private static void fstringsRuntime(com.alibaba.fastjson2.JSONWriter w, java.util.List<String> values, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            String item = values.get(i);
            if (item == null) w.writeNull(); else w.writeString(item);
        }
        w.endArray();
    }
    private static void ffriendsRuntime(com.alibaba.fastjson2.JSONWriter w, java.util.List<Users.Friend> values, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            Users.Friend item = values.get(i);
            ffriendRuntime(w, item, config);
        }
        w.endArray();
    }
    private static void fuserListRuntime(com.alibaba.fastjson2.JSONWriter w, java.util.List<Users.User> values, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            Users.User item = values.get(i);
            fuserRuntime(w, item, config);
        }
        w.endArray();
    }

    /** Isolates Fastjson2 JSONWriterUTF8.writeString(List<String>) only for tags. */
    static byte[] fastjsonFixedNativeList(Users value) throws IOException {
        try (Fastjson2Generator gen = F_BINDER.createGeneratorUtf8()) {
            gen.start();
            fusersFixedNativeList(gen.nativeGenerator(), value);
            gen.end();
            return gen.toBytes();
        }
    }

    private static void fusersFixedNativeList(com.alibaba.fastjson2.JSONWriter w, Users value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName5Raw(F_USERS.rawName0);
        fuserListFixedNativeList(w, value.getUsers());
        w.endObject();
    }


    private static void fuserListFixedNativeList(com.alibaba.fastjson2.JSONWriter w, java.util.List<Users.User> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            Users.User item = values.get(i);
            fuserFixedNativeList(w, item);
        }
        w.endArray();
    }


    private static void fuserFixedNativeList(com.alibaba.fastjson2.JSONWriter w, Users.User value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName2Raw(F_ID.rawName0);
        w.writeString(value.getId());
        w.writeName5Raw(F_INDEX.rawName0);
        w.writeInt32(value.getIndex());
        w.writeName4Raw(F_GUID.rawName0);
        w.writeString(value.getGuid());
        w.writeName8Raw(F_ISACTIVE.rawName0);
        w.writeBool(value.getIsActive());
        w.writeName7Raw(F_BALANCE.rawName0);
        w.writeString(value.getBalance());
        w.writeName7Raw(F_PICTURE.rawName0);
        w.writeString(value.getPicture());
        w.writeName3Raw(F_AGE.rawName0);
        w.writeInt32(value.getAge());
        w.writeName8Raw(F_EYECOLOR.rawName0);
        w.writeString(value.getEyeColor());
        w.writeName4Raw(F_NAME.rawName0);
        w.writeString(value.getName());
        w.writeName6Raw(F_GENDER.rawName0);
        w.writeString(value.getGender());
        w.writeName7Raw(F_COMPANY.rawName0);
        w.writeString(value.getCompany());
        w.writeName5Raw(F_EMAIL.rawName0);
        w.writeString(value.getEmail());
        w.writeName5Raw(F_PHONE.rawName0);
        w.writeString(value.getPhone());
        w.writeName7Raw(F_ADDRESS.rawName0);
        w.writeString(value.getAddress());
        w.writeName5Raw(F_ABOUT.rawName0);
        w.writeString(value.getAbout());
        w.writeName10Raw(F_REGISTERED.rawName0, F_REGISTERED.rawName1);
        w.writeString(value.getRegistered());
        w.writeName8Raw(F_LATITUDE.rawName0);
        w.writeDouble(value.getLatitude());
        w.writeName9Raw(F_LONGITUDE.rawName0, (int) F_LONGITUDE.rawName1);
        w.writeDouble(value.getLongitude());
        w.writeName4Raw(F_TAGS.rawName0);
        java.util.List<String> tags = value.getTags();
        if (tags == null) w.writeNull();
        else w.writeString(tags);
        w.writeName7Raw(F_FRIENDS.rawName0);
        ffriendsFixed(w, value.getFriends());
        w.writeName8Raw(F_GREETING.rawName0);
        w.writeString(value.getGreeting());
        w.writeName13Raw(F_FAVORITEFRUIT.rawName0, F_FAVORITEFRUIT.rawName1);
        w.writeString(value.getFavoriteFruit());
        w.endObject();
    }


    // Same native writer and JSON semantics; only the User POJO helper
    // boundary is removed from the containing Users list loop.
    static byte[] jacksonInlineRuntime(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (Jackson3Generator gen = J_BINDER.createGenerator(output)) {
                gen.start();
                jusersInlineRuntime(gen.nativeGenerator(), value, runtimeConfig);
                gen.end();
            }
            return output.toByteArray();
        } finally { output.recycle(); }
    }


    private static void jusersInlineRuntime(tools.jackson.core.JsonGenerator w, Users value, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.writeStartObject();
        w.writeName(J_USERS);
        juserListInlineRuntime(w, value.getUsers(), config);
        w.writeEndObject();
    }


    private static void juserListInlineRuntime(tools.jackson.core.JsonGenerator w, java.util.List<Users.User> values, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.writeStartArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            Users.User item = values.get(i);
            if (item == null) {
                w.writeNull();
            } else {
            w.writeStartObject();
            String v_id = item.getId();
            if (v_id != null || config.includeNulls) {
                w.writeName(J_ID);
                w.writeString(v_id);
            }
            w.writeName(J_INDEX);
            w.writeNumber(item.getIndex());
            String v_guid = item.getGuid();
            if (v_guid != null || config.includeNulls) {
                w.writeName(J_GUID);
                w.writeString(v_guid);
            }
            w.writeName(J_ISACTIVE);
            w.writeBoolean(item.getIsActive());
            String v_balance = item.getBalance();
            if (v_balance != null || config.includeNulls) {
                w.writeName(J_BALANCE);
                w.writeString(v_balance);
            }
            String v_picture = item.getPicture();
            if (v_picture != null || config.includeNulls) {
                w.writeName(J_PICTURE);
                w.writeString(v_picture);
            }
            w.writeName(J_AGE);
            w.writeNumber(item.getAge());
            String v_eyeColor = item.getEyeColor();
            if (v_eyeColor != null || config.includeNulls) {
                w.writeName(J_EYECOLOR);
                w.writeString(v_eyeColor);
            }
            String v_name = item.getName();
            if (v_name != null || config.includeNulls) {
                w.writeName(J_NAME);
                w.writeString(v_name);
            }
            String v_gender = item.getGender();
            if (v_gender != null || config.includeNulls) {
                w.writeName(J_GENDER);
                w.writeString(v_gender);
            }
            String v_company = item.getCompany();
            if (v_company != null || config.includeNulls) {
                w.writeName(J_COMPANY);
                w.writeString(v_company);
            }
            String v_email = item.getEmail();
            if (v_email != null || config.includeNulls) {
                w.writeName(J_EMAIL);
                w.writeString(v_email);
            }
            String v_phone = item.getPhone();
            if (v_phone != null || config.includeNulls) {
                w.writeName(J_PHONE);
                w.writeString(v_phone);
            }
            String v_address = item.getAddress();
            if (v_address != null || config.includeNulls) {
                w.writeName(J_ADDRESS);
                w.writeString(v_address);
            }
            String v_about = item.getAbout();
            if (v_about != null || config.includeNulls) {
                w.writeName(J_ABOUT);
                w.writeString(v_about);
            }
            String v_registered = item.getRegistered();
            if (v_registered != null || config.includeNulls) {
                w.writeName(J_REGISTERED);
                w.writeString(v_registered);
            }
            w.writeName(J_LATITUDE);
            w.writeNumber(item.getLatitude());
            w.writeName(J_LONGITUDE);
            w.writeNumber(item.getLongitude());
            w.writeName(J_TAGS);
            jstringsRuntime(w, item.getTags(), config);
            w.writeName(J_FRIENDS);
            jfriendsRuntime(w, item.getFriends(), config);
            String v_greeting = item.getGreeting();
            if (v_greeting != null || config.includeNulls) {
                w.writeName(J_GREETING);
                w.writeString(v_greeting);
            }
            String v_favoriteFruit = item.getFavoriteFruit();
            if (v_favoriteFruit != null || config.includeNulls) {
                w.writeName(J_FAVORITEFRUIT);
                w.writeString(v_favoriteFruit);
            }
            w.writeEndObject();
            }
        }
        w.writeEndArray();
    }


    static byte[] jacksonInlineFixed(Users value) throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            try (Jackson3Generator gen = J_BINDER.createGenerator(output)) {
                gen.start();
                jusersInlineFixed(gen.nativeGenerator(), value);
                gen.end();
            }
            return output.toByteArray();
        } finally { output.recycle(); }
    }


    private static void jusersInlineFixed(tools.jackson.core.JsonGenerator w, Users value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.writeStartObject();
        w.writeName(J_USERS);
        juserListInlineFixed(w, value.getUsers());
        w.writeEndObject();
    }


    private static void juserListInlineFixed(tools.jackson.core.JsonGenerator w, java.util.List<Users.User> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.writeStartArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            Users.User item = values.get(i);
            if (item == null) {
                w.writeNull();
            } else {
            w.writeStartObject();
            w.writeName(J_ID);
            w.writeString(item.getId());
            w.writeName(J_INDEX);
            w.writeNumber(item.getIndex());
            w.writeName(J_GUID);
            w.writeString(item.getGuid());
            w.writeName(J_ISACTIVE);
            w.writeBoolean(item.getIsActive());
            w.writeName(J_BALANCE);
            w.writeString(item.getBalance());
            w.writeName(J_PICTURE);
            w.writeString(item.getPicture());
            w.writeName(J_AGE);
            w.writeNumber(item.getAge());
            w.writeName(J_EYECOLOR);
            w.writeString(item.getEyeColor());
            w.writeName(J_NAME);
            w.writeString(item.getName());
            w.writeName(J_GENDER);
            w.writeString(item.getGender());
            w.writeName(J_COMPANY);
            w.writeString(item.getCompany());
            w.writeName(J_EMAIL);
            w.writeString(item.getEmail());
            w.writeName(J_PHONE);
            w.writeString(item.getPhone());
            w.writeName(J_ADDRESS);
            w.writeString(item.getAddress());
            w.writeName(J_ABOUT);
            w.writeString(item.getAbout());
            w.writeName(J_REGISTERED);
            w.writeString(item.getRegistered());
            w.writeName(J_LATITUDE);
            w.writeNumber(item.getLatitude());
            w.writeName(J_LONGITUDE);
            w.writeNumber(item.getLongitude());
            w.writeName(J_TAGS);
            jstringsFixed(w, item.getTags());
            w.writeName(J_FRIENDS);
            jfriendsFixed(w, item.getFriends());
            w.writeName(J_GREETING);
            w.writeString(item.getGreeting());
            w.writeName(J_FAVORITEFRUIT);
            w.writeString(item.getFavoriteFruit());
            w.writeEndObject();
            }
        }
        w.writeEndArray();
    }


    static byte[] fastjsonInlineRuntime(Users value) throws IOException {
        try (Fastjson2Generator gen = F_BINDER.createGeneratorUtf8()) {
            gen.start();
            fusersInlineRuntime(gen.nativeGenerator(), value, runtimeConfig);
            gen.end();
            return gen.toBytes();
        }
    }


    private static void fusersInlineRuntime(com.alibaba.fastjson2.JSONWriter w, Users value, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName5Raw(F_USERS.rawName0);
        fuserListInlineRuntime(w, value.getUsers(), config);
        w.endObject();
    }


    private static void fuserListInlineRuntime(com.alibaba.fastjson2.JSONWriter w, java.util.List<Users.User> values, org.sjf4j.NodeConfig config) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            Users.User item = values.get(i);
            if (item == null) {
                w.writeNull();
            } else {
            w.startObject();
            String v_id = item.getId();
            if (v_id != null || config.includeNulls) {
                w.writeName2Raw(F_ID.rawName0);
                w.writeString(v_id);
            }
            w.writeName5Raw(F_INDEX.rawName0);
            w.writeInt32(item.getIndex());
            String v_guid = item.getGuid();
            if (v_guid != null || config.includeNulls) {
                w.writeName4Raw(F_GUID.rawName0);
                w.writeString(v_guid);
            }
            w.writeName8Raw(F_ISACTIVE.rawName0);
            w.writeBool(item.getIsActive());
            String v_balance = item.getBalance();
            if (v_balance != null || config.includeNulls) {
                w.writeName7Raw(F_BALANCE.rawName0);
                w.writeString(v_balance);
            }
            String v_picture = item.getPicture();
            if (v_picture != null || config.includeNulls) {
                w.writeName7Raw(F_PICTURE.rawName0);
                w.writeString(v_picture);
            }
            w.writeName3Raw(F_AGE.rawName0);
            w.writeInt32(item.getAge());
            String v_eyeColor = item.getEyeColor();
            if (v_eyeColor != null || config.includeNulls) {
                w.writeName8Raw(F_EYECOLOR.rawName0);
                w.writeString(v_eyeColor);
            }
            String v_name = item.getName();
            if (v_name != null || config.includeNulls) {
                w.writeName4Raw(F_NAME.rawName0);
                w.writeString(v_name);
            }
            String v_gender = item.getGender();
            if (v_gender != null || config.includeNulls) {
                w.writeName6Raw(F_GENDER.rawName0);
                w.writeString(v_gender);
            }
            String v_company = item.getCompany();
            if (v_company != null || config.includeNulls) {
                w.writeName7Raw(F_COMPANY.rawName0);
                w.writeString(v_company);
            }
            String v_email = item.getEmail();
            if (v_email != null || config.includeNulls) {
                w.writeName5Raw(F_EMAIL.rawName0);
                w.writeString(v_email);
            }
            String v_phone = item.getPhone();
            if (v_phone != null || config.includeNulls) {
                w.writeName5Raw(F_PHONE.rawName0);
                w.writeString(v_phone);
            }
            String v_address = item.getAddress();
            if (v_address != null || config.includeNulls) {
                w.writeName7Raw(F_ADDRESS.rawName0);
                w.writeString(v_address);
            }
            String v_about = item.getAbout();
            if (v_about != null || config.includeNulls) {
                w.writeName5Raw(F_ABOUT.rawName0);
                w.writeString(v_about);
            }
            String v_registered = item.getRegistered();
            if (v_registered != null || config.includeNulls) {
                w.writeName10Raw(F_REGISTERED.rawName0, F_REGISTERED.rawName1);
                w.writeString(v_registered);
            }
            w.writeName8Raw(F_LATITUDE.rawName0);
            w.writeDouble(item.getLatitude());
            w.writeName9Raw(F_LONGITUDE.rawName0, (int) F_LONGITUDE.rawName1);
            w.writeDouble(item.getLongitude());
            w.writeName4Raw(F_TAGS.rawName0);
            fstringsRuntime(w, item.getTags(), config);
            w.writeName7Raw(F_FRIENDS.rawName0);
            ffriendsRuntime(w, item.getFriends(), config);
            String v_greeting = item.getGreeting();
            if (v_greeting != null || config.includeNulls) {
                w.writeName8Raw(F_GREETING.rawName0);
                w.writeString(v_greeting);
            }
            String v_favoriteFruit = item.getFavoriteFruit();
            if (v_favoriteFruit != null || config.includeNulls) {
                w.writeName13Raw(F_FAVORITEFRUIT.rawName0, F_FAVORITEFRUIT.rawName1);
                w.writeString(v_favoriteFruit);
            }
            w.endObject();
            }
        }
        w.endArray();
    }


    static byte[] fastjsonInlineFixed(Users value) throws IOException {
        try (Fastjson2Generator gen = F_BINDER.createGeneratorUtf8()) {
            gen.start();
            fusersInlineFixed(gen.nativeGenerator(), value);
            gen.end();
            return gen.toBytes();
        }
    }


    private static void fusersInlineFixed(com.alibaba.fastjson2.JSONWriter w, Users value) throws java.io.IOException {
        if (value == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName5Raw(F_USERS.rawName0);
        fuserListInlineFixed(w, value.getUsers());
        w.endObject();
    }


    private static void fuserListInlineFixed(com.alibaba.fastjson2.JSONWriter w, java.util.List<Users.User> values) throws java.io.IOException {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        for (int i = 0, n = values.size(); i < n; i++) {
            if (i != 0) w.writeComma();
            Users.User item = values.get(i);
            if (item == null) {
                w.writeNull();
            } else {
            w.startObject();
            w.writeName2Raw(F_ID.rawName0);
            w.writeString(item.getId());
            w.writeName5Raw(F_INDEX.rawName0);
            w.writeInt32(item.getIndex());
            w.writeName4Raw(F_GUID.rawName0);
            w.writeString(item.getGuid());
            w.writeName8Raw(F_ISACTIVE.rawName0);
            w.writeBool(item.getIsActive());
            w.writeName7Raw(F_BALANCE.rawName0);
            w.writeString(item.getBalance());
            w.writeName7Raw(F_PICTURE.rawName0);
            w.writeString(item.getPicture());
            w.writeName3Raw(F_AGE.rawName0);
            w.writeInt32(item.getAge());
            w.writeName8Raw(F_EYECOLOR.rawName0);
            w.writeString(item.getEyeColor());
            w.writeName4Raw(F_NAME.rawName0);
            w.writeString(item.getName());
            w.writeName6Raw(F_GENDER.rawName0);
            w.writeString(item.getGender());
            w.writeName7Raw(F_COMPANY.rawName0);
            w.writeString(item.getCompany());
            w.writeName5Raw(F_EMAIL.rawName0);
            w.writeString(item.getEmail());
            w.writeName5Raw(F_PHONE.rawName0);
            w.writeString(item.getPhone());
            w.writeName7Raw(F_ADDRESS.rawName0);
            w.writeString(item.getAddress());
            w.writeName5Raw(F_ABOUT.rawName0);
            w.writeString(item.getAbout());
            w.writeName10Raw(F_REGISTERED.rawName0, F_REGISTERED.rawName1);
            w.writeString(item.getRegistered());
            w.writeName8Raw(F_LATITUDE.rawName0);
            w.writeDouble(item.getLatitude());
            w.writeName9Raw(F_LONGITUDE.rawName0, (int) F_LONGITUDE.rawName1);
            w.writeDouble(item.getLongitude());
            w.writeName4Raw(F_TAGS.rawName0);
            fstringsFixed(w, item.getTags());
            w.writeName7Raw(F_FRIENDS.rawName0);
            ffriendsFixed(w, item.getFriends());
            w.writeName8Raw(F_GREETING.rawName0);
            w.writeString(item.getGreeting());
            w.writeName13Raw(F_FAVORITEFRUIT.rawName0, F_FAVORITEFRUIT.rawName1);
            w.writeString(item.getFavoriteFruit());
            w.endObject();
            }
        }
        w.endArray();
    }

}
