package codeanalysis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict RFC 8259 JSON parser for tests, written independently of Siyo's own
 * so a serializer bug cannot be hidden by a parser that shares it.
 *
 * <p>It rejects what the RFC rejects — a raw control character in a string, a
 * leading zero, a bare word, trailing data — and returns typed values: a
 * {@link String}, a {@link BigDecimal}, a {@link Boolean}, null, a
 * {@link List} or a {@link Map}, so a test can tell the string "123" from the
 * number 123.
 */
final class StrictJson {
    private final String text;
    private int pos;

    private StrictJson(String text) {
        this.text = text;
    }

    /** Parses one JSON text, or throws IllegalArgumentException saying where it is invalid. */
    static Object parse(String text) {
        StrictJson parser = new StrictJson(text);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != text.length()) throw parser.error("trailing data");
        return value;
    }

    private Object value() {
        if (pos >= text.length()) throw error("unexpected end");
        char c = text.charAt(pos);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        if (text.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        if (text.startsWith("null", pos)) { pos += 4; return null; }
        throw error("unexpected character");
    }

    private Map<String, Object> object() {
        Map<String, Object> result = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (peek() == '}') { pos++; return result; }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("expected a string key");
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, value());
            skipWhitespace();
            if (peek() == ',') { pos++; continue; }
            expect('}');
            return result;
        }
    }

    private List<Object> array() {
        List<Object> result = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (peek() == ']') { pos++; return result; }
        while (true) {
            skipWhitespace();
            result.add(value());
            skipWhitespace();
            if (peek() == ',') { pos++; continue; }
            expect(']');
            return result;
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) throw error("unterminated string");
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c < 0x20) throw error("raw control character U+" + String.format("%04X", (int) c));
            if (c != '\\') { sb.append(c); continue; }
            if (pos >= text.length()) throw error("unterminated escape");
            char e = text.charAt(pos++);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw error("short \\u escape");
                    String hex = text.substring(pos, pos + 4);
                    if (!hex.matches("[0-9a-fA-F]{4}")) throw error("bad \\u escape");
                    sb.append((char) Integer.parseInt(hex, 16));
                    pos += 4;
                }
                default -> throw error("bad escape \\" + e);
            }
        }
    }

    private BigDecimal number() {
        int start = pos;
        if (peek() == '-') pos++;
        if (peek() == '0') {
            pos++;
        } else if (peek() >= '1' && peek() <= '9') {
            while (Character.isDigit(peek())) pos++;
        } else {
            throw error("bad number");
        }
        if (peek() == '.') {
            pos++;
            if (!Character.isDigit(peek())) throw error("bad fraction");
            while (Character.isDigit(peek())) pos++;
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            if (!Character.isDigit(peek())) throw error("bad exponent");
            while (Character.isDigit(peek())) pos++;
        }
        return new BigDecimal(text.substring(start, pos));
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
            pos++;
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void expect(char c) {
        if (peek() != c) throw error("expected '" + c + "'");
        pos++;
    }

    private IllegalArgumentException error(String why) {
        return new IllegalArgumentException("invalid JSON at " + pos + ": " + why + " in " + text);
    }
}
