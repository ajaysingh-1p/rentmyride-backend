package com.rentmyride.util;

/**
 * Issue #33 fix: email and mobile number were never trimmed or case-normalized before being
 * saved or looked up. " User@Example.com" (leading space, mixed case) registered once could then
 * silently fail to log in as "user@example.com", and a mobile number with stray whitespace could
 * dodge the uniqueness check entirely. Every register/login/lookup path should normalize through
 * these two methods so the same input always maps to the same stored/looked-up value.
 */
public final class NormalizationUtil {

    private NormalizationUtil() {}

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public static String normalizeMobile(String mobile) {
        return mobile == null ? null : mobile.trim().replaceAll("\\s+", "");
    }
}
