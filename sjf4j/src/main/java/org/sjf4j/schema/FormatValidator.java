package org.sjf4j.schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public interface FormatValidator {

    boolean validate(String value);

    static FormatValidator get(String format) {
        switch (format) {
            case "email": {
                return EMAIL;
            }
            case "date": {
                return DATE;
            }
            case "date-time": {
                return DATETIME;
            }
            case "time": {
                return TIME;
            }
            default: {
                return null;
            }
        }
    }

    // INSTANCE
    EmailValidator EMAIL = new EmailValidator();
    DateValidator DATE = new DateValidator();
    DateTimeValidator DATETIME = new DateTimeValidator();
    TimeValidator TIME = new TimeValidator();

    class EmailValidator implements FormatValidator {
        // RFC 5322-ish
        private Pattern pattern = Pattern.compile( "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$" );
        @Override
        public boolean validate(String value) {
            return pattern.matcher(value).matches();
        }
    }

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

}
