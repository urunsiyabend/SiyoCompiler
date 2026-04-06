package codeanalysis.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal TOML parser for siyo.toml — supports flat tables and string/value pairs only.
 * No arrays, inline tables, or nested structures.
 */
public class TomlParser {
    private final Map<String, Map<String, String>> _tables = new LinkedHashMap<>();

    public static TomlParser parse(Path path) throws IOException {
        String content = new String(Files.readAllBytes(path));
        return parse(content);
    }

    public static TomlParser parse(String content) {
        TomlParser parser = new TomlParser();
        String currentTable = "";

        for (String rawLine : content.split("\n")) {
            String line = rawLine.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) continue;

            // Table header: [project]
            if (line.startsWith("[") && line.endsWith("]")) {
                currentTable = line.substring(1, line.length() - 1).trim();
                parser._tables.putIfAbsent(currentTable, new LinkedHashMap<>());
                continue;
            }

            // Key = value pair
            int eqIndex = line.indexOf('=');
            if (eqIndex < 0) continue;

            String key = line.substring(0, eqIndex).trim();
            String value = line.substring(eqIndex + 1).trim();

            // Strip quotes from key (for dependency keys like "org.xerial:sqlite-jdbc")
            key = stripQuotes(key);
            // Strip quotes from value
            value = stripQuotes(value);

            parser._tables.putIfAbsent(currentTable, new LinkedHashMap<>());
            parser._tables.get(currentTable).put(key, value);
        }

        return parser;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2) {
            if ((s.startsWith("\"") && s.endsWith("\"")) ||
                (s.startsWith("'") && s.endsWith("'"))) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    public String getString(String table, String key) {
        Map<String, String> t = _tables.get(table);
        return t != null ? t.get(key) : null;
    }

    public String getString(String table, String key, String defaultValue) {
        String v = getString(table, key);
        return v != null ? v : defaultValue;
    }

    public Map<String, String> getTable(String table) {
        return _tables.getOrDefault(table, new LinkedHashMap<>());
    }

    public boolean hasTable(String table) {
        return _tables.containsKey(table);
    }
}
