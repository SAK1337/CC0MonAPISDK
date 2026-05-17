package com.cc0mon.sdk;

import com.cc0mon.sdk.Errors.ValidationException;

import java.util.Locale;
import java.util.Set;

/**
 * Strict validators for filter inputs.
 *
 * <p>{@link #energy(String)} and {@link #rarity(String)} return the canonical
 * casing of the input value or throw {@link ValidationException} listing the
 * valid options. Matching is case-insensitive.
 */
public final class Validators {

    private Validators() {}

    /** Return the canonical-case energy, or throw with a friendly list of valid values. */
    public static String energy(String value) {
        return validateAgainst(value, Models.ENERGIES, "energy");
    }

    /** Return the canonical-case rarity, or throw with a friendly list of valid values. */
    public static String rarity(String value) {
        return validateAgainst(value, Models.RARITIES, "rarity");
    }

    private static String validateAgainst(String value, Set<String> canonical, String fieldName) {
        if (value == null) {
            throw new ValidationException(fieldName + " must not be null");
        }
        String needle = value.toLowerCase(Locale.ROOT);
        for (String c : canonical) {
            if (c.toLowerCase(Locale.ROOT).equals(needle)) return c;
        }
        throw new ValidationException(
            "unknown " + fieldName + ": '" + value + "'. valid: " + String.join(", ", canonical)
        );
    }
}
