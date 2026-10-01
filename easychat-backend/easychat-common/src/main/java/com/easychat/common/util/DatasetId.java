package com.easychat.common.util;

import java.util.regex.Pattern;

/**
 * Dataset identifiers are single path segments and exact ES keyword values.
 */
public final class DatasetId {
    public static final String DEFAULT = "default";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern RESERVED = Pattern.compile("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");

    private DatasetId() {
    }

    public static String normalize(String value) {
        String dataset = value == null || value.isBlank() ? DEFAULT : value.trim();
        if (!VALID.matcher(dataset).matches() || RESERVED.matcher(dataset).matches()) {
            throw new IllegalArgumentException("dataset must be 1-64 letters, digits, underscores or hyphens, "
                    + "start with a letter or digit, and not be a reserved device name");
        }
        return dataset;
    }
}
