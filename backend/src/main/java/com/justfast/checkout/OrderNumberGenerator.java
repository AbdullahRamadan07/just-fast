package com.justfast.checkout;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Human-friendly order numbers such as {@code JF-20261114-7KQ2XM}. Uniqueness is enforced by the database. */
@Component
public class OrderNumberGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final SecureRandom random = new SecureRandom();

    public String next(Instant now) {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return "JF-" + DATE.format(now) + "-" + suffix;
    }
}
