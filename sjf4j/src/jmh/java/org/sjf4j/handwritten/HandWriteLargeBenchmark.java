package org.sjf4j.handwritten;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.facade.gson.GsonModule;
import org.sjf4j.node.ReflectUtil;

/**
 * Large typed response-to-UTF-8 serialization comparison.
 *
 * <p>{@code userCount} is the number of fully populated users in a repeated typed response. The
 * fixture is created outside benchmark invocations. It intentionally excludes facade paths so
 * each native/handwritten pair isolates typed serialization.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class HandWriteLargeBenchmark {
  private static final ObjectMapper JACKSON2 = new ObjectMapper();
  private static final ObjectWriter JACKSON2_WRITER = JACKSON2.writerFor(Users.class);
  private static final Gson GSON = createNativeGson();
  private static final JSONWriter.Context FASTJSON2_CONTEXT =
      JSONFactory.createWriteContext(JSONWriter.Feature.WriteNulls);

  @Param({"1", "10", "100"})
  public int userCount;

  private Users users;

  public static void main(String[] args) throws Exception {
    String[] benchmarkArgs = new String[args.length + 1];
    benchmarkArgs[0] = HandWriteLargeBenchmark.class.getName();
    System.arraycopy(args, 0, benchmarkArgs, 1, args.length);
    Main.main(benchmarkArgs);
  }

  private static Gson createNativeGson() {
    GsonBuilder builder = new GsonBuilder();
    builder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
    builder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
    builder.serializeNulls();
    builder.setFieldNamingStrategy(
        field -> {
          String name = ReflectUtil.getExplicitName(field);
          return name != null ? name : field.getName();
        });
    return builder.create();
  }

  private static Users createUsers(int count) {
    Users users = new Users();
    ArrayList<User> list = new ArrayList<User>(count);
    for (int userIndex = 0; userIndex < count; userIndex++) {
      User user = new User();
      user.setId(839201L + userIndex);
      user.setUsername("alice.builder." + userIndex);
      user.setEmail("alice" + userIndex + "@example.test");
      user.setDisplayName("Alice \"Builder " + userIndex + "\"\\line\n");
      user.setPasswordHash("$2a$12$abc\\def" + userIndex);
      user.setBio("Builds\tfast JSON\rwith control\bchars " + userIndex);
      user.setWebsite("https://example.test/a?b=" + (userIndex + 1));
      user.setDepartment("Platform Engineering " + (userIndex % 3));
      user.setCreatedAt(1700000000123L + userIndex);
      user.setUpdatedAt(1701234567890L + userIndex);
      user.setLoginCount(421 + userIndex);
      user.setReputation(9876543210L + userIndex);
      user.setActive((userIndex & 1) == 0);
      user.setVerified((userIndex & 1) == 0);
      user.setAdmin((userIndex % 10) == 0);
      user.setSuspended((userIndex % 17) == 0);
      user.setScore(98.75d + userIndex);
      user.setLatitude(37.7749d + userIndex * 0.001d);
      user.setLongitude(-122.4194d - userIndex * 0.001d);
      user.setAge(34 + userIndex % 20);
      user.setAddress(
          new Address(
              (userIndex + 1) + " Main St",
              "San Francisco",
              "CA",
              "941" + (5 + userIndex % 10),
              "US"));
      ArrayList<String> tags = new ArrayList<String>(22);
      for (int tagIndex = 0; tagIndex < 22; tagIndex++) {
        tags.add("tag-" + userIndex + "-" + tagIndex + "-value\\\"\n");
      }
      user.setTags(tags);
      ArrayList<Friend> friends = new ArrayList<Friend>(20);
      for (int friendIndex = 0; friendIndex < 20; friendIndex++) {
        friends.add(
            new Friend(
                1000L + userIndex * 100L + friendIndex,
                "friend-" + userIndex + "-" + friendIndex,
                1600000000000L + userIndex * 100L + friendIndex,
                (friendIndex & 1) == 0));
      }
      user.setFriends(friends);
      list.add(user);
    }
    users.setUsers(list);
    users.setTotal(count);
    users.setPage(1);
    users.setGeneratedAt(1701234567999L);
    return users;
  }

  @Setup(Level.Trial)
  public void setupUsersAndValidateHandwrittenWriters() throws IOException {
    users = createUsers(userCount);
    validate("Jackson2", JACKSON2_WRITER.writeValueAsBytes(users), writeJackson2Handwritten(users));
    validate("Gson", writeGsonNative(users), writeGsonHandwritten(users));
    validate(
        "Fastjson2",
        JSON.toJSONBytes(users, StandardCharsets.UTF_8, FASTJSON2_CONTEXT),
        writeFastjson2Handwritten(users));
    validate(
        "Jackson2 top-level null",
        JACKSON2_WRITER.writeValueAsBytes(null),
        writeJackson2Handwritten(null));
    validate("Gson top-level null", writeGsonNative(null), writeGsonHandwritten(null));
    validate(
        "Fastjson2 top-level null",
        JSON.toJSONBytes(null, StandardCharsets.UTF_8, FASTJSON2_CONTEXT),
        writeFastjson2Handwritten(null));
    Users edge = new Users();
    User nullUser = new User();
    nullUser.setDisplayName(null);
    nullUser.setTags(null);
    ArrayList<Friend> nullFriends = new ArrayList<Friend>();
    nullFriends.add(null);
    nullUser.setFriends(nullFriends);
    ArrayList<User> edgeUsers = new ArrayList<User>();
    edgeUsers.add(nullUser);
    edge.setUsers(edgeUsers);
    validate(
        "Jackson2 null values",
        JACKSON2_WRITER.writeValueAsBytes(edge),
        writeJackson2Handwritten(edge));
    validate("Gson null values", writeGsonNative(edge), writeGsonHandwritten(edge));
    validate(
        "Fastjson2 null values",
        JSON.toJSONBytes(edge, StandardCharsets.UTF_8, FASTJSON2_CONTEXT),
        writeFastjson2Handwritten(edge));
  }

  private static void validate(String backend, byte[] nativeJson, byte[] handwrittenJson)
      throws IOException {
    if (nativeJson.length != handwrittenJson.length) {
      throw new IllegalStateException(
          backend
              + " output length differs: native="
              + nativeJson.length
              + ", handwritten="
              + handwrittenJson.length);
    }
    JsonNode nativeTree = JACKSON2.readTree(nativeJson);
    JsonNode handwrittenTree = JACKSON2.readTree(handwrittenJson);
    if (!nativeTree.equals(handwrittenTree)) {
      throw new IllegalStateException(backend + " handwritten output differs");
    }
  }

  @Benchmark
  public byte[] json_jackson2_large_native() throws IOException {
    return JACKSON2_WRITER.writeValueAsBytes(users);
  }

  @Benchmark
  public byte[] json_jackson2_large_handwritten_serialized_names() throws IOException {
    return writeJackson2Handwritten(users);
  }

  @Benchmark
  public byte[] json_gson_large_native() throws IOException {
    return writeGsonNative(users);
  }

  @Benchmark
  public byte[] json_gson_large_handwritten() throws IOException {
    return writeGsonHandwritten(users);
  }

  @Benchmark
  public byte[] json_fastjson2_large_native() {
    return JSON.toJSONBytes(users, StandardCharsets.UTF_8, FASTJSON2_CONTEXT);
  }

  @Benchmark
  public byte[] json_fastjson2_large_handwritten_raw_names() {
    return writeFastjson2Handwritten(users);
  }

  private static byte[] writeJackson2Handwritten(Users users) throws IOException {
    BufferRecycler recycler = JACKSON2.getFactory()._getBufferRecycler();
    try (ByteArrayBuilder output = new ByteArrayBuilder(recycler)) {
      JsonGenerator generator = JACKSON2.getFactory().createGenerator(output, JsonEncoding.UTF8);
      Jackson2HandLargeWriter.writeUsers(generator, users);
      generator.close();
      return output.getClearAndRelease();
    } finally {
      recycler.releaseToPool();
    }
  }

  private static byte[] writeGsonNative(Users users) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    OutputStreamWriter chars = new OutputStreamWriter(output, StandardCharsets.UTF_8);
    JsonWriter writer = GSON.newJsonWriter(chars);
    GSON.toJson(users, Users.class, writer);
    writer.flush();
    return output.toByteArray();
  }

  private static byte[] writeGsonHandwritten(Users users) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    OutputStreamWriter chars = new OutputStreamWriter(output, StandardCharsets.UTF_8);
    JsonWriter writer = GSON.newJsonWriter(chars);
    GsonHandLargeWriter.writeUsers(writer, users);
    writer.flush();
    return output.toByteArray();
  }

  private static byte[] writeFastjson2Handwritten(Users users) {
    try (JSONWriter writer = JSONWriter.ofUTF8(FASTJSON2_CONTEXT)) {
      Fastjson2HandLargeWriter.writeUsers(writer, users);
      return writer.getBytes();
    }
  }

}
