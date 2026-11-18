package org.sjf4j.testbench.path;

import com.jayway.jsonpath.Configuration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.path.JsonPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Exploratory JSONPath regex comparison on prebuilt Map/List data.
 *
 * <p>Jayway has no built-in match()/search() filter functions. Its =~ operator
 * uses whole-string matching; SJF4J's =~ operator uses substring search.
 * These paths are chosen to return exactly the same results for this fixture.
 *
 * <p>Path compilation and fixture creation happen in setup, outside timing.
 * SJF4J's match()/search() functions compile the regex for each input value,
 * while both =~ implementations reuse regexes compiled with their paths.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
public class RegexFunctionComparisonBenchmark {

    private static final Configuration JAYWAY_CONFIG = Configuration.defaultConfiguration();

    private static final String SJF_MATCH = "$.users[?match(@.name, 'user[0-9]{6}')].name";
    private static final String SJF_SEARCH = "$.users[?search(@.name, 'user[0-9]{6}')].name";
    private static final String SJF_OPERATOR_MATCH = "$.users[?(@.name =~ /^user[0-9]{6}$/)].name";
    private static final String SJF_OPERATOR_SEARCH = "$.users[?(@.name =~ /user[0-9]{6}/)].name";
    private static final String JAYWAY_MATCH = "$.users[?(@.name =~ /user[0-9]{6}/)].name";
    private static final String JAYWAY_SEARCH = "$.users[?(@.name =~ /.*user[0-9]{6}.*/)].name";

    @State(Scope.Thread)
    public static class Data {
        @Param({"16", "128", "1024"})
        public int count;

        public Map<String, Object> root;
        public JsonPath sjfMatch;
        public JsonPath sjfSearch;
        public JsonPath sjfOperatorMatch;
        public JsonPath sjfOperatorSearch;
        public com.jayway.jsonpath.JsonPath jaywayMatch;
        public com.jayway.jsonpath.JsonPath jaywaySearch;

        @Setup
        public void setup() {
            List<Map<String, Object>> users = new ArrayList<>(count);
            List<String> matchExpected = new ArrayList<>();
            List<String> searchExpected = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = String.format(Locale.ROOT, "%06d", i);
                String name;
                switch (i % 4) {
                    case 0:
                        name = "user" + id;
                        matchExpected.add(name);
                        searchExpected.add(name);
                        break;
                    case 1:
                        name = "prefix_user" + id + "_suffix";
                        searchExpected.add(name);
                        break;
                    case 2:
                        name = "guest" + id;
                        break;
                    default:
                        name = "prefix_guest" + id + "_suffix";
                        break;
                }
                Map<String, Object> user = new HashMap<>();
                user.put("name", name);
                users.add(user);
            }
            root = new HashMap<>();
            root.put("users", users);

            sjfMatch = JsonPath.parse(SJF_MATCH);
            sjfSearch = JsonPath.parse(SJF_SEARCH);
            sjfOperatorMatch = JsonPath.parse(SJF_OPERATOR_MATCH);
            sjfOperatorSearch = JsonPath.parse(SJF_OPERATOR_SEARCH);
            jaywayMatch = com.jayway.jsonpath.JsonPath.compile(JAYWAY_MATCH);
            jaywaySearch = com.jayway.jsonpath.JsonPath.compile(JAYWAY_SEARCH);

            check("SJF4J match()", matchExpected, sjfMatch.find(root));
            check("SJF4J search()", searchExpected, sjfSearch.find(root));
            check("SJF4J =~ match", matchExpected, sjfOperatorMatch.find(root));
            check("SJF4J =~ search", searchExpected, sjfOperatorSearch.find(root));
            check("Jayway =~ match", matchExpected, jaywayMatch.read(root, JAYWAY_CONFIG));
            check("Jayway =~ search", searchExpected, jaywaySearch.read(root, JAYWAY_CONFIG));
        }

        private static void check(String label, List<String> expected, Object actual) {
            if (!expected.equals(actual)) {
                throw new IllegalStateException(label + ": expected " + expected + ", actual " + actual);
            }
        }
    }

    @Benchmark
    public Object match_sjf4j_function(Data data) {
        return data.sjfMatch.find(data.root);
    }

    @Benchmark
    public Object match_sjf4j_operator(Data data) {
        return data.sjfOperatorMatch.find(data.root);
    }

    @Benchmark
    public Object match_jayway_regex(Data data) {
        return data.jaywayMatch.read(data.root, JAYWAY_CONFIG);
    }

    @Benchmark
    public Object search_sjf4j_function(Data data) {
        return data.sjfSearch.find(data.root);
    }

    @Benchmark
    public Object search_sjf4j_operator(Data data) {
        return data.sjfOperatorSearch.find(data.root);
    }

    @Benchmark
    public Object search_jayway_regex(Data data) {
        return data.jaywaySearch.read(data.root, JAYWAY_CONFIG);
    }
}
