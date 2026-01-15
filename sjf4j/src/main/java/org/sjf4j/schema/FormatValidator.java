package org.sjf4j.schema;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public interface FormatValidator {

    boolean validate(String value);

    static FormatValidator of(String format) {
        switch (format) {
            case "email": return EMAIL;
            case "date": return DATE;
            case "date-time": return DATETIME;
            case "time": return TIME;
            case "uri": return URI;
            case "hostname": return HOSTNAME;
            case "ipv4": return IPV4;
            case "ipv6": return IPV6;
            case "uuid": return UUID;
            case "uri-reference": return URI_REFERENCE;
            case "uri-template": return URI_TEMPLATE;
            case "json-pointer": return JSON_POINTER;
            case "regex": return REGEX;
            default: throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    // INSTANCE
    EmailValidator EMAIL = new EmailValidator();
    DateValidator DATE = new DateValidator();
    DateTimeValidator DATETIME = new DateTimeValidator();
    TimeValidator TIME = new TimeValidator();
    UriValidator URI = new UriValidator();
    HostnameValidator HOSTNAME = new HostnameValidator();
    Ipv4Validator IPV4 = new Ipv4Validator();
    Ipv6Validator IPV6 = new Ipv6Validator();
    UuidValidator UUID = new UuidValidator();
    UriReferenceValidator URI_REFERENCE = new UriReferenceValidator();
    UriTemplateValidator URI_TEMPLATE = new UriTemplateValidator();
    JsonPointerValidator JSON_POINTER = new JsonPointerValidator();
    RegexValidator REGEX = new RegexValidator();

    // email
    class EmailValidator implements FormatValidator {
        // RFC 5322-ish
        private final Pattern pattern = Pattern.compile( "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
        @Override
        public boolean validate(String value) {
            return pattern.matcher(value).matches();
        }
    }

    // date
    class DateValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                LocalDate.parse(value);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
    }

    // date-time
    class DateTimeValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                OffsetDateTime.parse(value);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
    }

    // time
    class TimeValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                OffsetTime.parse(value);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
    }

    // uri
    class UriValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                new URI(value);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // hostname
    class HostnameValidator implements FormatValidator {
        private final Pattern pattern = Pattern.compile(
                "^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
        );
        @Override
        public boolean validate(String value) {
            return pattern.matcher(value).matches();
        }
    }

    // ipv4
    class Ipv4Validator implements FormatValidator {
        private final Pattern pattern = Pattern.compile(
                "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.|$)){4}$"
        );
        @Override
        public boolean validate(String value) {
            return pattern.matcher(value).matches();
        }
    }

    // ipv6
    class Ipv6Validator implements FormatValidator {
        private final Pattern pattern = Pattern.compile(
                "^(?:[\\da-fA-F]{1,4}:){7}[\\da-fA-F]{1,4}$"
        );
        @Override
        public boolean validate(String value) {
            return pattern.matcher(value).matches();
        }
    }

    // uuid
    class UuidValidator implements FormatValidator {
        private final Pattern pattern = Pattern.compile(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
        );
        @Override
        public boolean validate(String value) {
            return pattern.matcher(value).matches();
        }
    }

    // uri-reference (rough)
    class UriReferenceValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                new URI(value);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // uri-template (very simple check for braces)
    class UriTemplateValidator implements FormatValidator {
        private final Pattern pattern = Pattern.compile("\\{[^}]*}");
        @Override
        public boolean validate(String value) {
            return pattern.matcher(value).find();
        }
    }

    // json-pointer
    class JsonPointerValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                java.net.URI.create("#" + value); // rough check
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // regex (valid regex)
    class RegexValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                Pattern.compile(value);
                return true;
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
    }

}
