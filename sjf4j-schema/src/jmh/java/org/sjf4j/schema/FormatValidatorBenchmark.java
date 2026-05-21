package org.sjf4j.schema;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.path.PathSyntax;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 6, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class FormatValidatorBenchmark {

    private static final Pattern LEGACY_EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~+-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~+-]+)*$");
    private static final Pattern LEGACY_UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern LEGACY_TIME_PATTERN = Pattern.compile(
            "^(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?([zZ]|[+-]\\d{2}:\\d{2})$");
    private static final Pattern LEGACY_DATE_TIME_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[tT](\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:[zZ]|[+-]\\d{2}:\\d{2}))$");
    private static final Pattern LEGACY_HOSTNAME_LABEL = Pattern.compile("^[a-zA-Z0-9-]+$");
    private static final Pattern LEGACY_IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern LEGACY_DURATION_PATTERN = Pattern.compile(
            "^P(?:\\d+W|(?:\\d+Y(?:\\d+M(?:\\d+D)?|\\d+M|)|\\d+M(?:\\d+D)?|\\d+D)?(?:T(?:\\d+H(?:\\d+M(?:\\d+S)?|\\d+M|)|\\d+M(?:\\d+S)?|\\d+S))?)$",
            Pattern.CASE_INSENSITIVE);

    private final String date = "2026-05-22";
    private final String uuid = "123e4567-e89b-12d3-a456-426614174000";
    private final String time = "12:34:56.789+08:00";
    private final String dateTime = "2026-05-22T12:34:56.789+08:00";
    private final String email = "alice.smith+tag@example-service.internal";
    private final String hostname = "api-01.example-service.internal";
    private final String ipv4 = "203.0.113.42";
    private final String duration = "P1Y2M3DT4H5M6S";
    private final String jsonPointer = "/a~1b/c~0d/0/-/tail";
    private final String relativeJsonPointer = "12/a~1b/c~0d/0";

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{"FormatValidatorBenchmark"});
    }

    @Benchmark
    public boolean date_current() {
        return FormatValidator.DATE.validate(date);
    }

    @Benchmark
    public boolean date_legacy_ascii_precheck() {
        return FormatValidator._isAsciiDigitsOnly(date) && FormatValidator._isValidDate(date);
    }

    @Benchmark
    public boolean uuid_current() {
        return FormatValidator.UUID.validate(uuid);
    }

    @Benchmark
    public boolean uuid_legacy_regex() {
        return LEGACY_UUID_PATTERN.matcher(uuid).matches();
    }

    @Benchmark
    public boolean time_current() {
        return FormatValidator.TIME.validate(time);
    }

    @Benchmark
    public boolean time_legacy_regex() {
        return legacyValidateTime(time);
    }

    @Benchmark
    public boolean date_time_current() {
        return FormatValidator.DATETIME.validate(dateTime);
    }

    @Benchmark
    public boolean date_time_legacy_regex() {
        return legacyValidateDateTime(dateTime);
    }

    @Benchmark
    public boolean email_current() {
        return FormatValidator.EMAIL.validate(email);
    }

    @Benchmark
    public boolean email_legacy_regex_local() {
        return legacyValidateEmail(email);
    }

    @Benchmark
    public boolean hostname_current() {
        return FormatUtil.validateHostname(hostname, false);
    }

    @Benchmark
    public boolean hostname_legacy_split_regex() {
        return legacyValidateAsciiHostname(hostname);
    }

    @Benchmark
    public boolean ipv4_current() {
        return FormatValidator.IPV4.validate(ipv4);
    }

    @Benchmark
    public boolean ipv4_legacy_regex() {
        return LEGACY_IPV4_PATTERN.matcher(ipv4).matches();
    }

    @Benchmark
    public boolean duration_current() {
        return FormatValidator.DURATION.validate(duration);
    }

    @Benchmark
    public boolean duration_legacy_regex() {
        return legacyValidateDuration(duration);
    }

    @Benchmark
    public boolean json_pointer_current() {
        return FormatValidator.JSON_POINTER.validate(jsonPointer);
    }

    @Benchmark
    public boolean json_pointer_legacy_parse() {
        return legacyValidateJsonPointer(jsonPointer);
    }

    @Benchmark
    public boolean relative_json_pointer_current() {
        return FormatValidator.RELATIVE_JSON_POINTER.validate(relativeJsonPointer);
    }

    @Benchmark
    public boolean relative_json_pointer_legacy_parse() {
        return legacyValidateRelativeJsonPointer(relativeJsonPointer);
    }

    private static boolean legacyValidateEmail(String value) {
        if (value == null || value.isEmpty()) return false;
        int at = FormatValidator._findEmailAt(value);
        if (at <= 0 || at == value.length() - 1) return false;
        String local = value.substring(0, at);
        if (local.isEmpty()) return false;
        if (local.charAt(0) == '"') {
            if (!FormatValidator._isQuotedEmailLocal(local)) return false;
        } else if (!LEGACY_EMAIL_PATTERN.matcher(local).matches()) {
            return false;
        }
        return FormatValidator._validateEmailDomain(value.substring(at + 1), false);
    }

    private static boolean legacyValidateTime(String value) {
        if (!FormatValidator._isAsciiDigitsOnly(value)) return false;
        java.util.regex.Matcher m = LEGACY_TIME_PATTERN.matcher(value);
        if (!m.matches()) return false;
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        int second = Integer.parseInt(m.group(3));
        String offset = m.group(5);
        if (hour > 23 || minute > 59) return false;
        if (second == 60) return FormatValidator._isValidLeapSecond(hour, minute, offset);
        if (second > 59) return false;
        return FormatValidator._isValidOffset(offset);
    }

    private static boolean legacyValidateDateTime(String value) {
        if (!FormatValidator._isAsciiDigitsOnly(value)) return false;
        java.util.regex.Matcher m = LEGACY_DATE_TIME_PATTERN.matcher(value);
        if (!m.matches()) return false;
        return FormatValidator._isValidDate(m.group(1)) && legacyValidateTime(m.group(2));
    }

    private static boolean legacyValidateAsciiHostname(String value) {
        if (value == null || value.isEmpty() || value.length() > 253 || value.startsWith(".") || value.endsWith(".")) return false;
        String[] parts = value.split("\\.", -1);
        int asciiLength = 0;
        for (int i = 0; i < parts.length; i++) {
            String label = parts[i];
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return false;
            if (!LEGACY_HOSTNAME_LABEL.matcher(label).matches()) return false;
            asciiLength += label.length();
            if (i > 0) asciiLength++;
        }
        return asciiLength <= 253;
    }

    private static boolean legacyValidateDuration(String value) {
        return value != null && FormatValidator._isAsciiDigitsOnly(value) && value.length() > 1 && LEGACY_DURATION_PATTERN.matcher(value).matches();
    }

    private static boolean legacyValidateJsonPointer(String value) {
        try {
            if (value == null) return false;
            PathSyntax.parsePointer(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean legacyValidateRelativeJsonPointer(String value) {
        try {
            if (value == null) return false;
            int idx = 0;
            while (idx < value.length() && Character.isDigit(value.charAt(idx))) idx++;
            if (idx == 0) return false;
            String prefix = value.substring(0, idx);
            if (prefix.length() > 1 && prefix.charAt(0) == '0') return false;
            Integer.parseInt(prefix);
            if (idx == value.length()) return true;
            if (value.charAt(idx) == '#') return idx == value.length() - 1;
            PathSyntax.parsePointer(value.substring(idx));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
