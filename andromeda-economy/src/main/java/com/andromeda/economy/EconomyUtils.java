package com.andromeda.economy;

import java.text.NumberFormat;
import java.util.Locale;

public final class EconomyUtils {
    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.US);

    static {
        FMT.setMinimumFractionDigits(2);
        FMT.setMaximumFractionDigits(2);
    }

    private EconomyUtils() {}

    public static String format(double amount) {
        return FMT.format(amount);
    }

    /** Compact notation: 1500 → "1.5K", 2000000 → "2M", etc. */
    public static String compact(double amount) {
        if (amount < 0) return "-" + compact(-amount);
        if (amount >= 1_000_000_000_000.0) return shortFmt(amount / 1_000_000_000_000.0) + "T";
        if (amount >= 1_000_000_000.0)     return shortFmt(amount / 1_000_000_000.0)     + "B";
        if (amount >= 1_000_000.0)         return shortFmt(amount / 1_000_000.0)         + "M";
        if (amount >= 1_000.0)             return shortFmt(amount / 1_000.0)             + "K";
        return String.format("%.0f", amount);
    }

    private static String shortFmt(double v) {
        if (v >= 100 || v == Math.floor(v)) return String.format("%.0f", v);
        return String.format("%.1f", v);
    }

    /**
     * Parses a player-typed amount string that may end with k/m/b/t suffix.
     * e.g. "10m" → 10_000_000.0, "1.5k" → 1_500.0, "500" → 500.0
     * Throws NumberFormatException if the string is not a valid number.
     */
    public static double parseAmount(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        double mul = 1;
        if      (s.endsWith("t")) { mul = 1e12; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("b")) { mul = 1e9;  s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("m")) { mul = 1e6;  s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("k")) { mul = 1e3;  s = s.substring(0, s.length() - 1); }
        return Double.parseDouble(s) * mul;
    }

    public static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI";
            case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX";
            case 10 -> "X"; default -> String.valueOf(n);
        };
    }

    /** "minecraft:blue_concrete_powder" -> "Blue Concrete Powder" */
    public static String toDisplayName(String itemId) {
        String path = itemId.contains(":") ? itemId.split(":")[1] : itemId;
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
