package com.finai.analysis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Periods {

    private static final Pattern YEAR = Pattern.compile("(\\d{4})(.*)");

    private Periods() {
    }

    public static String prior(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        Matcher matcher = YEAR.matcher(period.trim());
        if (!matcher.matches()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        return (year - 1) + matcher.group(2);
    }
}
