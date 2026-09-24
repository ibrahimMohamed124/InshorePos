package com.inshore.payment.provider.fake;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal reader/writer for the FLAT JSON object ({"key":"value", ...}) the fake gateway uses for its
 * webhook bodies. Deliberately dependency-free: a real adapter parses its provider's payload with
 * Jackson; the fake only needs strings and numbers on one level.
 */
final class FakeJson {

    private final String s;
    private int i;

    private FakeJson(String s) {
        this.s = s;
    }

    static String write(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            quote(sb, e.getKey());
            sb.append(':');
            quote(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    static Map<String, String> parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("empty body");
        }
        return new FakeJson(json).readObject();
    }

    private Map<String, String> readObject() {
        Map<String, String> out = new LinkedHashMap<>();
        skipWs();
        expect('{');
        skipWs();
        if (peek() == '}') {
            i++;
            return finish(out);
        }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            expect(':');
            skipWs();
            String value = peek() == '"' ? readString() : readBare();
            out.put(key, value);
            skipWs();
            char c = next();
            if (c == '}') {
                return finish(out);
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected ',' or '}' at " + (i - 1));
            }
        }
    }

    private Map<String, String> finish(Map<String, String> out) {
        skipWs();
        if (i < s.length()) {
            throw new IllegalArgumentException("unexpected trailing content");
        }
        return out;
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char esc = next();
            switch (esc) {
                case '"', '\\', '/' -> sb.append(esc);
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) {
                        throw new IllegalArgumentException("bad unicode escape");
                    }
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> throw new IllegalArgumentException("bad escape \\" + esc);
            }
        }
    }

    private String readBare() {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                break;
            }
            if (c == '{' || c == '[') {
                throw new IllegalArgumentException("nested values are not supported");
            }
            i++;
        }
        String token = s.substring(start, i);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("missing value at " + start);
        }
        return "null".equals(token) ? null : token;
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        if (i >= s.length()) {
            throw new IllegalArgumentException("unexpected end of input");
        }
        return s.charAt(i);
    }

    private char next() {
        char c = peek();
        i++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw new IllegalArgumentException("expected '" + c + "' at " + (i - 1));
        }
    }

    private static void quote(StringBuilder sb, String v) {
        sb.append('"');
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
