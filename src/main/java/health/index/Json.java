package health.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.space();
        if (parser.position != text.length()) throw new IllegalArgumentException("Unexpected JSON suffix");
        return value;
    }

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (out.length() > 1) out.append(',');
                out.append(quote(entry.getKey().toString())).append(':').append(stringify(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> items) {
            StringBuilder out = new StringBuilder("[");
            for (Object item : items) {
                if (out.length() > 1) out.append(',');
                out.append(stringify(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 32 ? String.format("\\u%04x", (int) c) : c);
            }
        }
        return out.append('\"').toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected JSON object");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        if (!(value instanceof List<?>)) throw new IllegalArgumentException("Expected JSON array");
        return (List<Object>) value;
    }

    private static final class Parser {
        private final String text;
        private int position;
        Parser(String text) { this.text = text; }

        void space() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }

        Object value() {
            space();
            if (position >= text.length()) throw new IllegalArgumentException("Expected JSON value");
            char c = text.charAt(position);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '\"') return string();
            if (c == 't') { literal("true"); return true; }
            if (c == 'f') { literal("false"); return false; }
            if (c == 'n') { literal("null"); return null; }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            position++;
            space();
            if (take('}')) return out;
            do {
                space();
                String key = string();
                space();
                expect(':');
                out.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return out;
        }

        List<Object> array() {
            List<Object> out = new ArrayList<>();
            position++;
            space();
            if (take(']')) return out;
            do { out.add(value()); space(); } while (take(','));
            expect(']');
            return out;
        }

        String string() {
            expect('\"');
            StringBuilder out = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '\"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '\"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> { out.append((char) Integer.parseInt(text.substring(position, position + 4), 16)); position += 4; }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        Number number() {
            int start = position;
            while (position < text.length() && "-+0123456789.eE".indexOf(text.charAt(position)) >= 0) position++;
            String token = text.substring(start, position);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token);
        }

        void literal(String expected) {
            if (!text.startsWith(expected, position)) throw new IllegalArgumentException("Invalid JSON literal");
            position += expected.length();
        }
        boolean take(char c) { if (position < text.length() && text.charAt(position) == c) { position++; return true; } return false; }
        void expect(char c) { if (!take(c)) throw new IllegalArgumentException("Expected '" + c + "'"); }
    }
}

