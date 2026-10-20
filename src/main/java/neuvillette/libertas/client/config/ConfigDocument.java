package neuvillette.libertas.client.config;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/** An isolated editing session. Reading and changing draft values never writes to the original file. */
public final class ConfigDocument {

    public enum Type {
        BOOLEAN,
        INTEGER,
        NUMBER,
        STRING,
        ARRAY,
        JSON
    }

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeNulls()
        .create();
    private static final Gson COMPACT_JSON = new GsonBuilder().disableHtmlEscaping()
        .serializeNulls()
        .create();
    private static final Pattern FORGE_PROPERTY = Pattern
        .compile("^(\\s*(?:([BIDSCHM]):)?(\"[^\"]*\"|[^=<>]+?)\\s*)(=|<)(.*)$");
    private static final Pattern CATEGORY = Pattern.compile("^\\s*(\"[^\"]*\"|[^\"{}=:]+?)\\s*\\{\\s*$");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern RANGE = Pattern.compile("\\[range:\\s*([^~]+)~\\s*([^,\\]]+)");
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;

    private final Path file;
    private final byte[] original;
    private final Charset charset;
    private final boolean bom;
    private final String source;
    private final String newline;
    private final List<String> lines;
    private final List<Entry> entries = new ArrayList<>();
    private final boolean json;
    private final boolean properties;

    private ConfigDocument(Path file, byte[] original) throws IOException {
        this.file = file;
        this.original = original;
        bom = original.length >= 3 && original[0] == (byte) 0xEF
            && original[1] == (byte) 0xBB
            && original[2] == (byte) 0xBF;
        Charset encoding = StandardCharsets.UTF_8;
        String text;
        try {
            text = encoding.newDecoder()
                .decode(ByteBuffer.wrap(original, bom ? 3 : 0, original.length - (bom ? 3 : 0)))
                .toString();
        } catch (CharacterCodingException e) {
            encoding = StandardCharsets.ISO_8859_1;
            text = new String(original, encoding);
        }
        charset = encoding;
        source = text;
        newline = text.contains("\r\n") ? "\r\n" : "\n";
        lines = Arrays.asList(text.split("\\r?\\n", -1));
        String name = file.getFileName()
            .toString()
            .toLowerCase(Locale.ROOT);
        json = name.endsWith(".json");
        properties = name.endsWith(".properties");
        try {
            if (json) readJson(new JsonParser().parse(source), new ArrayList<>());
            else if (properties || name.endsWith(".ini")) readKeyValues();
            else readForge();
        } catch (RuntimeException e) {
            throw new IOException("invalidFile", e);
        }
        if (entries.isEmpty()) throw new IOException("emptyFile");
    }

    public static ConfigDocument open(ConfigFiles files, Path file) throws IOException {
        Path checked = files.check(file);
        if (!ConfigFiles.isSupported(checked)) throw new IOException("unsupported");
        if (Files.size(checked) > MAX_FILE_BYTES) throw new IOException("tooLarge");
        return new ConfigDocument(checked, Files.readAllBytes(checked));
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public Path getFile() {
        return file;
    }

    public boolean hasChanges() {
        return entries.stream()
            .anyMatch(Entry::hasChanges);
    }

    public boolean isValid() {
        return entries.stream()
            .noneMatch(entry -> entry.hasChanges() && entry.getError() != null);
    }

    public void save() throws IOException {
        if (!hasChanges()) return;
        if (!isValid()) throw new IOException("invalidValue");
        if (!file.toRealPath()
            .equals(file) || !Arrays.equals(original, Files.readAllBytes(file))) {
            throw new IOException("changedFile");
        }
        if (!Files.isWritable(file)) throw new IOException("readOnly");
        String rendered = json ? renderJson() : renderLines();
        if (!charset.newEncoder()
            .canEncode(rendered)) throw new IOException("encoding");
        byte[] content = ((bom ? "\uFEFF" : "") + rendered).getBytes(charset);
        Path temporary = Files.createTempFile(file.getParent(), ".libertas-config-", ".tmp");
        try {
            Files.write(temporary, content);
            // Recheck after preparing the replacement so an external edit is not silently overwritten.
            if (!Arrays.equals(original, Files.readAllBytes(file))) throw new IOException("changedFile");
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void readJson(JsonElement value, List<String> path) {
        if (value.isJsonObject() && value.getAsJsonObject()
            .entrySet()
            .size() > 0) {
            for (Map.Entry<String, JsonElement> child : value.getAsJsonObject()
                .entrySet()) {
                List<String> childPath = new ArrayList<>(path);
                childPath.add(child.getKey());
                readJson(child.getValue(), childPath);
            }
            return;
        }
        Type type = Type.JSON;
        String text = COMPACT_JSON.toJson(value);
        if (value.isJsonArray()) type = Type.ARRAY;
        else if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) type = Type.BOOLEAN;
            else if (primitive.isNumber()) type = Type.NUMBER;
            else {
                type = Type.STRING;
                text = primitive.getAsString();
            }
        }
        Entry entry = new Entry(path.isEmpty() ? "$" : String.join(" / ", path), "", type, text);
        entry.jsonPath = path;
        entries.add(entry);
    }

    private void readForge() throws IOException {
        List<String> categories = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        String pendingCategory = null;
        boolean structured = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                comments.add(
                    trimmed.substring(1)
                        .trim());
                continue;
            }
            if (trimmed.isEmpty()) {
                comments.clear();
                continue;
            }
            Matcher category = CATEGORY.matcher(line);
            if (trimmed.equals("{")) {
                if (pendingCategory == null) throw new IOException("invalidFile");
                categories.add(pendingCategory);
                pendingCategory = null;
                structured = true;
                comments.clear();
                continue;
            }
            if (category.matches()) {
                categories.add(
                    unquote(
                        category.group(1)
                            .trim()));
                pendingCategory = null;
                structured = true;
                comments.clear();
                continue;
            }
            if (trimmed.equals("}")) {
                if (categories.isEmpty()) throw new IOException("invalidFile");
                categories.remove(categories.size() - 1);
                pendingCategory = null;
                comments.clear();
                continue;
            }
            Matcher property = FORGE_PROPERTY.matcher(line);
            if (!property.matches()) {
                pendingCategory = unquote(trimmed);
                comments.clear();
                continue;
            }
            pendingCategory = null;
            String key = unquote(
                property.group(3)
                    .trim());
            Type type = forgeType(property.group(2));
            String value = property.group(5);
            if (property.group(2) == null) type = inferType(value);
            int start = i;
            boolean list = property.group(4)
                .equals("<");
            if (list) {
                if (!value.trim()
                    .isEmpty()) throw new IOException("invalidFile");
                JsonArray values = new JsonArray();
                while (++i < lines.size() && !lines.get(i)
                    .trim()
                    .equals(">")) {
                    String element = lines.get(i)
                        .trim();
                    values.add(new JsonPrimitive(element));
                }
                if (i == lines.size()) throw new IOException("invalidFile");
                value = COMPACT_JSON.toJson(values);
            }
            Entry entry = new Entry(
                categories.isEmpty() ? key : String.join(" / ", categories) + " / " + key,
                String.join("\n", comments),
                list ? Type.ARRAY : type,
                value);
            entry.start = start;
            entry.end = i + 1;
            entry.prefix = property.group(1) + property.group(4);
            entry.listType = list ? type : null;
            entry.indent = line.substring(
                0,
                line.length() - line.replaceFirst("^\\s+", "")
                    .length());
            entry.forge = true;
            entries.add(entry);
            comments.clear();
        }
        if (!categories.isEmpty()) throw new IOException("invalidFile");
        if (entries.isEmpty() && !structured) readPlainLines();
    }

    private void readPlainLines() {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim()
                .isEmpty()
                || line.trim()
                    .startsWith("#"))
                continue;
            Entry entry = new Entry("[" + (i + 1) + "]", "", Type.STRING, line);
            entry.start = i;
            entry.end = i + 1;
            entry.prefix = "";
            entry.forge = true;
            entries.add(entry);
        }
    }

    private void readKeyValues() throws IOException {
        String section = "";
        List<String> comments = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.startsWith("!") || (!properties && trimmed.startsWith(";"))) {
                comments.add(
                    trimmed.substring(1)
                        .trim());
                continue;
            }
            if (trimmed.isEmpty()) {
                comments.clear();
                continue;
            }
            if (!properties && trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.substring(1, trimmed.length() - 1);
                comments.clear();
                continue;
            }
            int start = i;
            while (properties && continued(line) && i + 1 < lines.size()) {
                line = line.substring(0, line.length() - 1) + lines.get(++i)
                    .replaceFirst("^\\s+", "");
            }
            int separator = separator(line);
            if (separator < 0 && !properties) continue;
            if (separator < 0) separator = line.length();
            int valueStart = separator;
            while (valueStart < line.length() && Character.isWhitespace(line.charAt(valueStart))) valueStart++;
            if (valueStart < line.length() && (line.charAt(valueStart) == '=' || line.charAt(valueStart) == ':'))
                valueStart++;
            while (valueStart < line.length() && Character.isWhitespace(line.charAt(valueStart))) valueStart++;
            String prefix = line.substring(0, valueStart) + (separator == line.length() ? "=" : "");
            String key = line.substring(0, separator)
                .trim();
            String value = line.substring(valueStart);
            if (properties) {
                Properties parsed = new Properties();
                parsed.load(new StringReader(line));
                if (parsed.size() != 1) throw new IOException("invalidFile");
                key = parsed.stringPropertyNames()
                    .iterator()
                    .next();
                value = parsed.getProperty(key);
            }
            Entry entry = new Entry(
                section.isEmpty() ? key : section + " / " + key,
                String.join("\n", comments),
                inferType(value),
                value);
            entry.start = start;
            entry.end = i + 1;
            entry.prefix = prefix;
            entries.add(entry);
            comments.clear();
        }
    }

    private String renderJson() {
        JsonElement root = new JsonParser().parse(source);
        for (Entry entry : entries) {
            if (!entry.hasChanges()) continue;
            JsonElement value;
            if (entry.type == Type.STRING) value = new JsonPrimitive(entry.value);
            else if (entry.type == Type.NUMBER) value = new JsonPrimitive(new BigDecimal(entry.value));
            else value = new JsonParser().parse(entry.value);
            if (entry.jsonPath.isEmpty()) root = value;
            else {
                JsonObject parent = root.getAsJsonObject();
                for (int i = 0; i < entry.jsonPath.size() - 1; i++) {
                    parent = parent.getAsJsonObject(entry.jsonPath.get(i));
                }
                parent.add(entry.jsonPath.get(entry.jsonPath.size() - 1), value);
            }
        }
        String rendered = JSON.toJson(root)
            .replace("\n", newline);
        return rendered + (source.endsWith("\n") ? newline : "");
    }

    private String renderLines() {
        List<String> result = new ArrayList<>();
        int cursor = 0;
        for (Entry entry : entries) {
            if (!entry.hasChanges()) continue;
            result.addAll(lines.subList(cursor, entry.start));
            if (entry.listType != null) {
                result.add(lines.get(entry.start));
                for (JsonElement value : new JsonParser().parse(entry.value)
                    .getAsJsonArray()) {
                    result.add(entry.indent + "    " + value.getAsString());
                }
                result.add(lines.get(entry.end - 1));
            } else result.add(entry.prefix + (properties ? escapeProperty(entry.value) : entry.value));
            cursor = entry.end;
        }
        result.addAll(lines.subList(cursor, lines.size()));
        return String.join(newline, result);
    }

    private static String escapeProperty(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\') result.append("\\\\");
            else if (ch == '\n') result.append("\\n");
            else if (ch == '\r') result.append("\\r");
            else if (ch == '\t') result.append("\\t");
            else if (ch == '\f') result.append("\\f");
            else if (ch < 32 || ch > 126) result.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
            else {
                if (ch == ' ' && i == 0) result.append('\\');
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static int separator(String line) {
        boolean escaped = false;
        boolean inKey = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (!escaped && (ch == '=' || ch == ':' || (inKey && Character.isWhitespace(ch)))) return i;
            if (!Character.isWhitespace(ch)) inKey = true;
            if (ch == '\\') escaped = !escaped;
            else escaped = false;
        }
        return -1;
    }

    private static boolean continued(String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) count++;
        return count % 2 == 1;
    }

    private static String unquote(String value) {
        return value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
    }

    private static Type forgeType(String type) {
        if ("B".equals(type)) return Type.BOOLEAN;
        if ("I".equals(type)) return Type.INTEGER;
        if ("D".equals(type)) return Type.NUMBER;
        return Type.STRING;
    }

    private static Type inferType(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return Type.BOOLEAN;
        if (value.matches("[+-]?\\d+")) return Type.INTEGER;
        if (NUMBER.matcher(value)
            .matches()) return Type.NUMBER;
        return Type.STRING;
    }

    public static final class Entry {

        private final String name;
        private final String comment;
        private final Type type;
        private final String originalValue;
        private String value;
        private int start;
        private int end;
        private String prefix;
        private String indent;
        private Type listType;
        private boolean forge;
        private List<String> jsonPath;

        private Entry(String name, String comment, Type type, String value) {
            this.name = name;
            this.comment = comment;
            this.type = type;
            this.originalValue = value;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getComment() {
            return comment;
        }

        public Type getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean hasChanges() {
            return !value.equals(originalValue);
        }

        public String getError() {
            try {
                validate(value, type);
                if (forge && type != Type.ARRAY && (value.contains("\n") || value.contains("\r"))) return "singleLine";
                if (listType != null) {
                    for (JsonElement item : new JsonParser().parse(value)
                        .getAsJsonArray()) {
                        if (!item.isJsonPrimitive()) return "invalidList";
                        String text = item.getAsString();
                        if (text.contains("\n") || text.contains("\r")
                            || text.trim()
                                .startsWith(">")
                            || text.endsWith("<")) return "invalidList";
                        validate(text, listType);
                    }
                }
                if (type == Type.INTEGER || type == Type.NUMBER) {
                    Matcher range = RANGE.matcher(comment);
                    if (range.find() && NUMBER.matcher(
                        range.group(1)
                            .trim())
                        .matches()
                        && NUMBER.matcher(
                            range.group(2)
                                .trim())
                            .matches()) {
                        BigDecimal number = new BigDecimal(value);
                        if (number.compareTo(
                            new BigDecimal(
                                range.group(1)
                                    .trim()))
                            < 0
                            || number.compareTo(
                                new BigDecimal(
                                    range.group(2)
                                        .trim()))
                                > 0)
                            return "outOfRange";
                    }
                }
                return null;
            } catch (RuntimeException e) {
                return "invalid" + type.name();
            }
        }

        private static void validate(String value, Type type) {
            switch (type) {
                case BOOLEAN -> {
                    if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException();
                }
                case INTEGER -> new BigInteger(value);
                case NUMBER -> {
                    if (!NUMBER.matcher(value)
                        .matches()) throw new IllegalArgumentException();
                    new BigDecimal(value);
                }
                case ARRAY -> {
                    if (!new JsonParser().parse(value)
                        .isJsonArray()) throw new IllegalArgumentException();
                }
                case JSON -> new JsonParser().parse(value);
                default -> {}
            }
        }
    }
}
