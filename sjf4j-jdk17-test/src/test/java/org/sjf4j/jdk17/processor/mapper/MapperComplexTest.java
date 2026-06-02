package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.compiled.CompiledNodes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MapperComplexTest {

    @Test
    public void mapsMixedRecordBeanFieldAndConstructorModels() {
        ComplexMapper mapper = CompiledNodes.of(ComplexMapper.class);
        Account account = new Account(
                new AccountId("A-001"),
                new Profile("Ada", "Lovelace", true),
                new Stats(7, 99L),
                List.of("math", "code"),
                Map.of("score", 7, "rank", 1),
                new String[]{"ada", "lovelace"},
                "payload-value",
                "internal-note");

        AccountDto dto = mapper.toDto(account);

        assertEquals("A-001", dto.id);
        assertEquals("Ada", dto.getFirstName());
        assertEquals("Lovelace", dto.getLastName());
        assertEquals("Ada Lovelace", dto.getDisplayName());
        assertEquals("Ada#7", dto.getLabel());
        assertEquals("2:7:lovelace:payload-value", dto.getContainerSummary());
        assertEquals(7, dto.score);
        assertEquals(List.of("math", "code"), dto.tags);
        assertEquals(Map.of("score", 7, "rank", 1), dto.metrics);
        assertEquals("lovelace", dto.aliases[1]);
        assertEquals("payload-value", dto.payload);
        assertEquals(0L, dto.version());
        assertEquals(0L, dto.auditVersion);
        assertNull(mapper.toDto(null));
    }

    @Test
    public void mapsToRecordSummaryWithConstructorArguments() {
        ComplexMapper mapper = CompiledNodes.of(ComplexMapper.class);
        Account account = new Account(
                new AccountId("A-002"),
                new Profile("Grace", "Hopper", false),
                new Stats(11, 101L),
                List.of("compiler"),
                Map.of("score", 11),
                new String[]{"grace", "hopper"},
                new Object(),
                "hidden");

        AccountSummary summary = mapper.toSummary(account);

        assertEquals("A-002", summary.id());
        assertEquals("Grace Hopper", summary.displayName());
        assertEquals(11, summary.score());
        assertEquals(false, summary.active());
    }

    @Test
    public void mapsToUniqueConstructorTarget() {
        ComplexMapper mapper = CompiledNodes.of(ComplexMapper.class);
        Account account = new Account(
                new AccountId("A-003"),
                new Profile("Katherine", "Johnson", true),
                new Stats(13, 102L),
                List.of("orbit"),
                Map.of("score", 13),
                new String[]{"katherine", "johnson"},
                "payload",
                "hidden");

        AccountView view = mapper.toView(account);

        assertEquals("A-003", view.id());
        assertEquals("Katherine Johnson", view.displayName());
        assertEquals(13, view.score());
    }

    public record AccountId(String value) {}

    public record Stats(int score, long version) {}

    public static final class Profile {
        public String firstName;
        private final String lastName;
        private final boolean active;

        public Profile(String firstName, String lastName, boolean active) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.active = active;
        }

        public String getLastName() { return lastName; }
        public boolean isActive() { return active; }
    }

    public static final class Account {
        private final AccountId id;
        private final Profile profile;
        private final Stats stats;
        private final List<String> tags;
        private final Map<String, Integer> metrics;
        private final String[] aliases;
        private final Object payload;
        public String note;

        public Account(AccountId id, Profile profile, Stats stats,
                       List<String> tags, Map<String, Integer> metrics, String[] aliases,
                       Object payload, String note) {
            this.id = id;
            this.profile = profile;
            this.stats = stats;
            this.tags = tags;
            this.metrics = metrics;
            this.aliases = aliases;
            this.payload = payload;
            this.note = note;
        }

        public String getId() { return id.value(); }
        public String getFirstName() { return profile.firstName; }
        public String getLastName() { return profile.getLastName(); }
        public boolean isActive() { return profile.isActive(); }
        public int getScore() { return stats.score(); }
        public long version() { return stats.version(); }
        public List<String> getTags() { return tags; }
        public Map<String, Integer> getMetrics() { return metrics; }
        public String[] getAliases() { return aliases; }
        public Object getPayload() { return payload; }
    }

    public static final class AccountDto {
        public String id;
        private String firstName;
        private String lastName;
        private String displayName;
        private String label;
        private String containerSummary;
        public List<String> tags;
        public Map<String, Integer> metrics;
        public String[] aliases;
        public Object payload;
        public int score;
        public long auditVersion;

        public AccountDto() {}

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getContainerSummary() { return containerSummary; }
        public void setContainerSummary(String containerSummary) { this.containerSummary = containerSummary; }

        public long version() { return auditVersion; }
    }

    public record AccountSummary(String id, String displayName, int score, boolean active) {}

    public static final class AccountView {
        private final String id;
        private final String displayName;
        private final int score;

        public AccountView(String id, String displayName, int score) {
            this.id = id;
            this.displayName = displayName;
            this.score = score;
        }

        public String id() { return id; }
        public String displayName() { return displayName; }
        public int score() { return score; }
    }

    @CompiledMapper
    public interface ComplexMapper {
        @Mapping(target = "displayName", sources = {"firstName", "lastName"}, compute = "(first, last) -> first + \" \" + last")
        @Mapping(target = "label", sources = {"firstName", "score"}, compute = "(first, score) -> first + \"#\" + score")
        @Mapping(target = "containerSummary", sources = {"tags", "metrics", "aliases", "payload"}, compute = "(tags, metrics, aliases, payload) -> tags.size() + \":\" + metrics.get(\"score\") + \":\" + aliases[1] + \":\" + payload")
        @Mapping(target = "auditVersion", ignore = true)
        AccountDto toDto(Account account);

        @Mapping(target = "displayName", compute = "this::displayName")
        AccountSummary toSummary(Account account);

        @Mapping(target = "displayName", compute = "this::displayName")
        AccountView toView(Account account);

        default String displayName(String firstName, String lastName) {
            return firstName + " " + lastName;
        }
    }
}
