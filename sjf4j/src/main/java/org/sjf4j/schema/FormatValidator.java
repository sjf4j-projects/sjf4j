package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;
import org.sjf4j.util.PointerUtil;

import java.net.IDN;
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
            case "idn-email": return IDN_EMAIL;
            case "date": return DATE;
            case "date-time": return DATETIME;
            case "time": return TIME;
            case "duration": return DURATION;
            case "hostname": return HOSTNAME;
            case "idn-hostname": return IDN_HOSTNAME;
            case "ipv4": return IPV4;
            case "ipv6": return IPV6;
            case "uuid": return UUID;
            case "uri": return URI;
            case "uri-reference": return URI_REFERENCE;
            case "uri-template": return URI_TEMPLATE;
            case "iri": return IRI;
            case "iri-reference": return IRI_REFERENCE;
            case "json-pointer": return JSON_POINTER;
            case "relative-json-pointer": return RELATIVE_JSON_POINTER;
            case "regex": return REGEX;
            default: throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    // INSTANCE
    EmailValidator EMAIL = new EmailValidator();
    IdnEmailValidator IDN_EMAIL = new IdnEmailValidator();
    DateValidator DATE = new DateValidator();
    DateTimeValidator DATETIME = new DateTimeValidator();
    TimeValidator TIME = new TimeValidator();
    DurationValidator DURATION = new DurationValidator();
    HostnameValidator HOSTNAME = new HostnameValidator();
    IdnHostnameValidator IDN_HOSTNAME = new IdnHostnameValidator();
    Ipv4Validator IPV4 = new Ipv4Validator();
    Ipv6Validator IPV6 = new Ipv6Validator();
    UuidValidator UUID = new UuidValidator();
    UriValidator URI = new UriValidator();
    UriReferenceValidator URI_REFERENCE = new UriReferenceValidator();
    UriTemplateValidator URI_TEMPLATE = new UriTemplateValidator();
    IriValidator IRI = new IriValidator();
    IriReferenceValidator IRI_REFERENCE = new IriReferenceValidator();
    JsonPointerValidator JSON_POINTER = new JsonPointerValidator();
    RelativeJsonPointerValidator RELATIVE_JSON_POINTER = new RelativeJsonPointerValidator();
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

    // idn-email
    class IdnEmailValidator implements FormatValidator {
        private final Pattern pattern = Pattern.compile( "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+$");

        @Override
        public boolean validate(String value) {
            if (value == null || value.isEmpty()) return false;
            int at = value.lastIndexOf('@');
            if (at <= 0 || at == value.length() - 1) return false;

            String local = value.substring(0, at);
            String domain = value.substring(at + 1);
            if (!pattern.matcher(local).matches()) return false;
            try {
                String ascii = IDN.toASCII(domain);
                return IDN_HOSTNAME.validate(ascii);
            } catch (Exception e) {
                return false;
            }
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

    // duration
    class DurationValidator implements FormatValidator {
        private static final Pattern DURATION_PATTERN =
                Pattern.compile("^P(\\d+Y)?(\\d+M)?(\\d+D)?(T(\\d+H)?(\\d+M)?(\\d+(\\.\\d+)?S)?)?$");

        @Override
        public boolean validate(String value) {
            if (value == null || value.isEmpty()) return false;
            return DURATION_PATTERN.matcher(value).matches();
        }
    }

    // hostname
    class HostnameValidator implements FormatValidator {
        private final Pattern pattern = Pattern.compile(
                "^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$");

        @Override
        public boolean validate(String value) {
            if (value == null || value.isEmpty()) return false;
            return pattern.matcher(value).matches();
        }
    }

    // idn-hostname
    class IdnHostnameValidator implements FormatValidator {
        private final Pattern labelPattern = Pattern.compile("^[a-zA-Z0-9-]+$");

        @Override
        public boolean validate(String value) {
            if (value == null || value.isEmpty()) return false;
            String ascii;
            try {
                ascii = IDN.toASCII(value);
            } catch (Exception e) {
                return false;
            }
            String[] parts = ascii.split("\\.");
            for (String label : parts) {
                if (label.isEmpty() || label.length() > 63) return false;
                if (!labelPattern.matcher(label).matches()) return false;
                if (label.startsWith("-") || label.endsWith("-")) return false;
            }
            return true;
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

    // uri
    class UriValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                URI uri = new URI(value);
                return uri.isAbsolute();
            } catch (Exception e) {
                return false;
            }
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

    // iri
    class IriValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            if (value == null || value.isEmpty()) return false;
            try {
                // java.net.URI only support ASCII, so transform from Unicode to ASCII
                // RFC 3987: Unicode，Punycode + percent-encode
                String ascii = java.net.IDN.toASCII(value);
                new URI(ascii);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // iri-reference
    class IriReferenceValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            if (value == null || value.isEmpty()) return false;
            try {
                int schemeIdx = value.indexOf(':');
                String ascii;
                if (schemeIdx > 0) ascii = convertIriToAscii(value);
                else ascii = value;

                new URI(ascii);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private String convertIriToAscii(String iri) {
            int schemeEnd = iri.indexOf(':') + 1;
            String rest = iri.substring(schemeEnd);

            // get authority (host:port)
            if (rest.startsWith("//")) {
                int pathStart = rest.indexOf('/', 2);
                String authority = pathStart >= 0 ? rest.substring(2, pathStart) : rest.substring(2);
                String pathAndQuery = pathStart >= 0 ? rest.substring(pathStart) : "";

                String hostPort;
                int atIdx = authority.indexOf('@');
                if (atIdx >= 0) {
                    // userinfo@host:port
                    String userinfo = authority.substring(0, atIdx + 1);
                    String host = authority.substring(atIdx + 1);
                    hostPort = userinfo + IDN.toASCII(host);
                } else {
                    hostPort = IDN.toASCII(authority);
                }
                return iri.substring(0, schemeEnd) + "//" + hostPort + pathAndQuery;
            }
            return iri;
        }
    }


    // json-pointer
    class JsonPointerValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                if (value == null) return false;
                PointerUtil.compile(value);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // relative-json-pointer
    class RelativeJsonPointerValidator implements FormatValidator {
        @Override
        public boolean validate(String value) {
            try {
                if (value == null) return false;
                String[] ss = value.split("/", 2);
                if (Integer.parseInt(ss[0]) < 0) return false;
                if (ss.length > 1) PointerUtil.compile(ss[1]);
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
