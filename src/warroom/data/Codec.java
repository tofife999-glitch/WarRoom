package warroom.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns records into single lines of text for the save. There are three separator levels: '|' between a
 * record's fields, ';' between the items of a list field (such as a war's story), ',' between an item's
 * fields; numbers inside one field are separated by spaces. Text is escaped so that it never contains a
 * raw separator, whatever the level.
 */
final class Codec {
    static final char FIELD = '|';
    static final char ITEM = ';';
    static final char PART = ',';

    private Codec() {
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case FIELD: sb.append("\\p"); break;
                case ITEM: sb.append("\\s"); break;
                case PART: sb.append("\\c"); break;
                case '\n': sb.append("\\n"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Inverse of {@link #esc}; the empty string stands for null. */
    static String unesc(String s) {
        if (s == null || s.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'p': sb.append(FIELD); break;
                case 's': sb.append(ITEM); break;
                case 'c': sb.append(PART); break;
                case 'n': sb.append('\n'); break;
                default: sb.append(n);
            }
        }
        return sb.toString();
    }

    /** Splits on {@code sep} wherever it is not escaped. Escapes are kept, for the next level or unesc. */
    static List<String> split(String s, char sep) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                cur.append(c).append(s.charAt(++i));
            } else if (c == sep) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** Joins already escaped parts. */
    static String join(List<String> parts, char sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    static String ints(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    /** Space-separated integers; also reads 0.2's comma-separated lists. */
    static int[] ints(String s) {
        if (s == null || s.trim().isEmpty()) return new int[0];
        String[] parts = s.trim().split("[ ,]+");
        int[] v = new int[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Integer.parseInt(parts[i]);
        return v;
    }

    static int intAt(List<String> f, int i, int def) {
        try {
            return i < f.size() && !f.get(i).isEmpty() ? Integer.parseInt(f.get(i)) : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    static long longAt(List<String> f, int i, long def) {
        try {
            return i < f.size() && !f.get(i).isEmpty() ? Long.parseLong(f.get(i)) : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    static String textAt(List<String> f, int i) {
        return i < f.size() ? unesc(f.get(i)) : null;
    }

    /** Unescape for War Room 0.2 saves, which used "\/" for '|' and ',' between numbers. */
    static String unescV2(String s) {
        if (s == null || s.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == '/' ? '|' : n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
