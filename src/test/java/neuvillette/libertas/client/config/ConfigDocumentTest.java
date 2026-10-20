package neuvillette.libertas.client.config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import neuvillette.libertas.client.config.ConfigDocument.Entry;

public class ConfigDocumentTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Path root;
    private ConfigFiles files;

    @Before
    public void setUp() throws IOException {
        root = temporary.newFolder("config")
            .toPath();
        files = new ConfigFiles(root);
    }

    private Path write(String name, String text) throws IOException {
        Path file = root.resolve(name);
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    public void draftsDoNotWriteUntilAcceptedAndPreserveForgeCommentsAndLineEndings() throws IOException {
        String original = "# Configuration file\r\n\r\ngeneral {\r\n"
            + "    # Keep this comment [default: true]\r\n    B:enabled=true\r\n"
            + "    nested {\r\n        S:label=hello\r\n    }\r\n}\r\n";
        Path file = write("example.cfg", original);
        ConfigDocument document = ConfigDocument.open(files, file);
        assertEquals(
            2,
            document.getEntries()
                .size());
        assertEquals(
            "general / nested / label",
            document.getEntries()
                .get(1)
                .getName());
        document.getEntries()
            .get(0)
            .setValue("false");
        assertTrue(document.hasChanges());
        assertEquals(original, read(file));
        document.save();
        assertEquals(original.replace("B:enabled=true", "B:enabled=false"), read(file));
    }

    @Test
    public void forgeListEditsPreserveTypedValuesAndTheSurroundingFile() throws IOException {
        Path file = write(
            "lists.cfg",
            "general {\n    I:ports <\n        1\n        2\n    >\n    S:other=unchanged\n}\n");
        ConfigDocument document = ConfigDocument.open(files, file);
        Entry entry = document.getEntries()
            .get(0);
        entry.setValue("[3, 4, 9223372036854775807]");
        assertNull(entry.getError());
        document.save();
        assertTrue(read(file).contains("    I:ports <\n        3\n        4\n        9223372036854775807\n    >"));
        assertTrue(read(file).contains("S:other=unchanged"));
    }

    @Test
    public void codeChickenRootOptionsAndSeparateCategoryBracesCanBeEdited() throws IOException {
        String original = "# CodeChicken style\ncheckUpdates=true\ninventory.gamemodes=creative, adventure\n"
            + "sawDiamond\n{\n    # durability comment\n    durability=2048\n"
            + "    nested\n    {\n        enabled=false\n    }\n}\n";
        Path file = write("codechicken.cfg", original);
        ConfigDocument document = ConfigDocument.open(files, file);
        List<Entry> entries = document.getEntries();
        assertEquals(4, entries.size());
        assertEquals(
            ConfigDocument.Type.BOOLEAN,
            entries.get(0)
                .getType());
        assertEquals(
            ConfigDocument.Type.INTEGER,
            entries.get(2)
                .getType());
        assertEquals(
            "sawDiamond / nested / enabled",
            entries.get(3)
                .getName());
        assertEquals(
            "durability comment",
            entries.get(2)
                .getComment());
        entries.get(0)
            .setValue("false");
        entries.get(2)
            .setValue("4096");
        document.save();
        assertEquals(
            original.replace("checkUpdates=true", "checkUpdates=false")
                .replace("durability=2048", "durability=4096"),
            read(file));
        assertEquals(
            4,
            ConfigDocument.open(files, file)
                .getEntries()
                .size());
    }

    @Test
    public void plainCfgListsCanBeEditedWithoutTurningEmptyCategoriesIntoOptions() throws IOException {
        Path list = write("filters.cfg", "# hidden items\nminecraft:spawn_egg\nminecraft:stone\n");
        ConfigDocument document = ConfigDocument.open(files, list);
        assertEquals(
            2,
            document.getEntries()
                .size());
        document.getEntries()
            .get(0)
            .setValue("minecraft:dirt");
        document.save();
        assertEquals("# hidden items\nminecraft:dirt\nminecraft:stone\n", read(list));
        Path empty = write("empty.cfg", "# no options\ngeneral {\n}\n");
        assertEquals(
            "emptyFile",
            assertThrows(IOException.class, () -> ConfigDocument.open(files, empty)).getMessage());
    }

    @Test
    public void quotedForgeKeysAndBraceValuesKeepTheirMeaning() throws IOException {
        Path file = write("quoted.cfg", "general {\n    S:\"a=b<c\"=old\n    S:brace={\n}\n");
        ConfigDocument document = ConfigDocument.open(files, file);
        assertEquals(
            "general / a=b<c",
            document.getEntries()
                .get(0)
                .getName());
        assertEquals(
            "{",
            document.getEntries()
                .get(1)
                .getValue());
        document.getEntries()
            .get(0)
            .setValue("new");
        document.save();
        assertTrue(read(file).contains("S:\"a=b<c\"=new\n    S:brace={"));
    }

    @Test
    public void invalidValuesAndRangesCannotBeSaved() throws IOException {
        Path file = write("range.cfg", "general {\n    # [range: 1 ~ 10, default: 2]\n    I:count=2\n}\n");
        byte[] original = Files.readAllBytes(file);
        ConfigDocument document = ConfigDocument.open(files, file);
        Entry entry = document.getEntries()
            .get(0);
        entry.setValue("11");
        assertEquals("outOfRange", entry.getError());
        assertFalse(document.isValid());
        assertThrows(IOException.class, document::save);
        entry.setValue("hello");
        assertEquals("invalidINTEGER", entry.getError());
        assertThrows(IOException.class, document::save);
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    public void listValidationRejectsWrongTypesAndConfigurationSyntaxInjection() throws IOException {
        Path file = write("list.cfg", "general {\n    I:values <\n        1\n    >\n}\n");
        ConfigDocument document = ConfigDocument.open(files, file);
        Entry entry = document.getEntries()
            .get(0);
        entry.setValue("[\"not a number\"]");
        assertFalse(document.isValid());
        entry.setValue("[\">\"]");
        assertEquals("invalidList", entry.getError());
        entry.setValue("[\"1\\n}\"]");
        assertEquals("invalidList", entry.getError());
        assertThrows(IOException.class, document::save);
    }

    @Test
    public void jsonKeepsTypesNullsAndKeysContainingPunctuation() throws IOException {
        Path file = write(
            "example.json",
            "{\"render.options\":{\"enabled\":true,\"number\":1.5,"
                + "\"label\":\"old\",\"items\":[1,2],\"empty\":null},\"large\":9007199254740993}\n");
        ConfigDocument document = ConfigDocument.open(files, file);
        List<Entry> entries = document.getEntries();
        entries.get(0)
            .setValue("false");
        entries.get(1)
            .setValue("+2.75");
        entries.get(2)
            .setValue("中文 \\\"value\\\"");
        entries.get(3)
            .setValue("[3,{\"nested\":true}]");
        document.save();
        JsonObject json = new JsonParser().parse(read(file))
            .getAsJsonObject();
        JsonObject options = json.getAsJsonObject("render.options");
        assertFalse(
            options.get("enabled")
                .getAsBoolean());
        assertTrue(
            options.getAsJsonPrimitive("number")
                .isNumber());
        assertEquals(
            2.75,
            options.get("number")
                .getAsDouble(),
            0);
        assertEquals(
            entries.get(2)
                .getValue(),
            options.get("label")
                .getAsString());
        assertTrue(
            options.get("empty")
                .isJsonNull());
        assertEquals(
            2,
            options.getAsJsonArray("items")
                .size());
        assertEquals(
            "9007199254740993",
            json.get("large")
                .getAsString());
    }

    @Test
    public void propertiesEscapesAndContinuedLinesRemainUsableByJavaProperties() throws IOException {
        String original = "# preserve comment\r\nname\\ with\\ spaces = old\r\n"
            + "path=C:\\\\old\\\\folder\r\njoined=hello\\\r\n    world\r\n";
        Path file = write("example.properties", original);
        ConfigDocument document = ConfigDocument.open(files, file);
        assertEquals(
            "name with spaces",
            document.getEntries()
                .get(0)
                .getName());
        assertEquals(
            "helloworld",
            document.getEntries()
                .get(2)
                .getValue());
        document.getEntries()
            .get(0)
            .setValue(" 中文\nvalue\\tail");
        document.save();
        Properties parsed = new Properties();
        parsed.load(new StringReader(read(file)));
        assertEquals(" 中文\nvalue\\tail", parsed.getProperty("name with spaces"));
        assertTrue(read(file).contains("path=C:\\\\old\\\\folder\r\njoined=hello\\\r\n    world\r\n"));
    }

    @Test
    public void propertiesSupportEmptyValuesAndKeysContinuedAcrossLines() throws IOException {
        Path file = write("empty.properties", "# header\nempty\nlong\\\n    Key=old\n");
        ConfigDocument document = ConfigDocument.open(files, file);
        assertEquals(
            "",
            document.getEntries()
                .get(0)
                .getValue());
        assertEquals(
            "longKey",
            document.getEntries()
                .get(1)
                .getName());
        document.getEntries()
            .get(0)
            .setValue("new");
        document.getEntries()
            .get(1)
            .setValue("updated");
        document.save();
        Properties parsed = new Properties();
        parsed.load(new StringReader(read(file)));
        assertEquals("new", parsed.getProperty("empty"));
        assertEquals("updated", parsed.getProperty("longKey"));
    }

    @Test
    public void savingCharactersUnsupportedByTheOriginalEncodingDoesNotCorruptTheFile() throws IOException {
        Path file = root.resolve("legacy.cfg");
        byte[] original = "general {\n    S:label=café\n}\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(file, original);
        ConfigDocument document = ConfigDocument.open(files, file);
        document.getEntries()
            .get(0)
            .setValue("中文");
        assertEquals("encoding", assertThrows(IOException.class, document::save).getMessage());
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    public void iniSectionsAndCommentsAreRetained() throws IOException {
        String original = "; original\n[render]\nenabled = true\nquality = fancy\n[world]\ncount = 2\n";
        Path file = write("example.ini", original);
        ConfigDocument document = ConfigDocument.open(files, file);
        assertEquals(
            "world / count",
            document.getEntries()
                .get(2)
                .getName());
        document.getEntries()
            .get(0)
            .setValue("false");
        document.save();
        assertEquals(original.replace("enabled = true", "enabled = false"), read(file));
    }

    @Test
    public void malformedFilesAreNeverRenamedOrRewrittenByOpeningThem() throws IOException {
        Path file = write("broken.cfg", "general {\n    B:enabled=true\n");
        byte[] original = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> ConfigDocument.open(files, file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(
            1,
            files.list(root)
                .size());
        Path json = write("broken.json", "{\"name\":");
        assertThrows(IOException.class, () -> ConfigDocument.open(files, json));
        assertEquals("{\"name\":", read(json));
    }

    @Test
    public void saveRefusesToOverwriteExternalEdits() throws IOException {
        Path file = write("example.json", "{\"enabled\":true}");
        ConfigDocument document = ConfigDocument.open(files, file);
        document.getEntries()
            .get(0)
            .setValue("false");
        Files.write(file, "{\"enabled\":true,\"external\":1}".getBytes(StandardCharsets.UTF_8));
        IOException exception = assertThrows(IOException.class, document::save);
        assertEquals("changedFile", exception.getMessage());
        assertEquals("{\"enabled\":true,\"external\":1}", read(file));
    }

    @Test
    public void acceptingAnUnchangedDocumentDoesNotRewriteFormattingOrUtf8Bom() throws IOException {
        Path file = write("example.json", "\uFEFF{ \"enabled\" : true }\r\n");
        byte[] original = Files.readAllBytes(file);
        ConfigDocument document = ConfigDocument.open(files, file);
        document.save();
        assertArrayEquals(original, Files.readAllBytes(file));
        document.getEntries()
            .get(0)
            .setValue("false");
        document.save();
        assertTrue(read(file).startsWith("\uFEFF"));
        assertTrue(read(file).endsWith("\r\n"));
    }

    @Test
    public void browserSortsDirectoriesFirstAndRestrictsNavigationToConfig() throws IOException {
        write("a.json", "{}");
        Path directory = Files.createDirectory(root.resolve("z-folder"));
        assertEquals(
            directory,
            files.list(root)
                .get(0));
        assertEquals(directory.toRealPath(), files.resolve("config/z-folder"));
        assertEquals(files.getRoot(), files.resolve("config/"));
        assertThrows(IOException.class, () -> files.resolve(".."));
        Path outside = temporary.newFile("outside.json")
            .toPath();
        assertThrows(IOException.class, () -> ConfigDocument.open(files, outside));
    }
}
