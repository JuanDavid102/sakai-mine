package edu.uc.ltigradebook.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DateUtils {
    private final static String DATE_FORMAT = "yyyy-MM-dd HH:mm";

    public static Instant convertDateToInstant(String inputDate) {
        try {
            Instant instant = LocalDateTime.parse(inputDate, DateTimeFormatter.ofPattern(DATE_FORMAT).withLocale(Locale.FRENCH)).atZone(ZoneId.systemDefault()).toInstant();
            return instant;
        } catch(Exception e) {
            log.error("Error converting date {}", inputDate, e);
            return null;
        }
    }

    public static String convertInstantToString(Instant inputInstant) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT).withLocale( Locale.FRENCH ).withZone(ZoneId.systemDefault());
            return formatter.format(inputInstant);
        } catch(Exception e) {
            log.error("Error formatting instant {}", inputInstant, e);
            return null;
        }
    }

}
