package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Ships-in-the-jar checks for the Modonomicon guide book (see docs/guidebook.md).
 * The guide is client-render-only, so GameTest and the running client are the only
 * things that watch it draw - but its DATA can still drift silently, and a malformed
 * pattern that still parses will render wrong with no error. Two headless locks run
 * in the required {@code build} job:
 *
 * <ol>
 *   <li>The Terrarium multiblock pattern must keep the real shell/cavity geometry
 *       (7x7 footprint, 6 tall, 5x5x4 air cavity - {@code TerrariumValidator}'s 5x4x5
 *       cavity inflated by one) AND stay right-way-up. Modonomicon {@code dense}
 *       patterns are top-to-bottom ({@code pattern[0]} is the highest Y, per
 *       {@code DenseMultiblock}), so the ceiling Sprinklers belong on the first layer
 *       and the mud floor on the last. A reversed pattern renders the box upside down
 *       and no parser complains; this pins it.</li>
 *   <li>Every lang key the book JSON references must exist in {@code en_us.json}, so a
 *       typo surfaces as a failed build instead of a raw translation key in-game.</li>
 * </ol>
 */
class GuidebookTest {

    private static final Path RESOURCES_ROOT = resourcesRoot();
    private static final Path BOOK_DIR =
        RESOURCES_ROOT.resolve("data/productivefrogs/modonomicon/books/guide");
    private static final Path TERRARIUM_MULTIBLOCK =
        RESOURCES_ROOT.resolve("data/productivefrogs/modonomicon/multiblocks/terrarium.json");
    private static final Path LANG =
        RESOURCES_ROOT.resolve("assets/productivefrogs/lang/en_us.json");

    /** book.productivefrogs... translation keys referenced from the book JSON. */
    private static final Pattern BOOK_KEY = Pattern.compile("book\\.productivefrogs[a-z0-9_.]+");

    @Test
    void terrariumMultiblockKeepsGeometryAndIsRightSideUp() {
        JsonObject mb = readJson(TERRARIUM_MULTIBLOCK).getAsJsonObject();
        assertEquals("modonomicon:dense", mb.get("type").getAsString(), "dense multiblock");

        JsonArray layers = mb.getAsJsonArray("pattern");
        assertEquals(6, layers.size(), "shell is 6 blocks tall");

        List<String[]> grid = new ArrayList<>();
        StringBuilder flat = new StringBuilder();
        for (JsonElement layerEl : layers) {
            JsonArray rows = layerEl.getAsJsonArray();
            assertEquals(7, rows.size(), "each layer is 7 rows deep");
            String[] layer = new String[7];
            for (int z = 0; z < 7; z++) {
                layer[z] = rows.get(z).getAsString();
                assertEquals(7, layer[z].length(), "each row is 7 columns wide");
                flat.append(layer[z]);
            }
            grid.add(layer);
        }

        assertEquals(1, count(flat.toString(), '0'), "exactly one origin marker");

        // Top-to-bottom: pattern[0] is the ceiling (Sprinklers), the last layer is the
        // mud floor. This is the assertion that catches a reversed pattern.
        String ceiling = String.join("", grid.get(0));
        String floor = String.join("", grid.get(grid.size() - 1));
        assertTrue(ceiling.indexOf('P') >= 0, "ceiling Sprinklers live on the top layer (pattern[0])");
        assertFalse(floor.indexOf('P') >= 0, "the floor layer has no Sprinklers (pattern is not reversed)");
        assertTrue(floor.indexOf('M') >= 0, "mud floor on the bottom layer");

        // 5x5x4 air cavity: the four wall layers (1..4) each hold a 5x5 air interior
        // (the machines sit on the shell ring at x/z 0 or 6, never in the 1..5 interior).
        for (int y = 1; y <= 4; y++) {
            String[] layer = grid.get(y);
            for (int z = 1; z <= 5; z++) {
                for (int x = 1; x <= 5; x++) {
                    assertEquals('A', layer[z].charAt(x),
                        "cavity interior must be air at layer " + y + " row " + z + " col " + x);
                }
            }
        }

        // Every character used in the pattern must have a mapping entry, or Modonomicon
        // rejects the multiblock at load.
        JsonObject mapping = mb.getAsJsonObject("mapping");
        for (char c : flat.toString().toCharArray()) {
            assertTrue(mapping.has(String.valueOf(c)), "pattern char '" + c + "' has a mapping");
        }
    }

    @Test
    void everyBookLangKeyIsDefined() {
        Set<String> defined = readJson(LANG).getAsJsonObject().keySet();
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(BOOK_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Matcher m = BOOK_KEY.matcher(Files.readString(file));
                while (m.find()) {
                    if (!defined.contains(m.group())) {
                        missing.add(file.getFileName() + " -> " + m.group());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(missing.isEmpty(), "book JSON references undefined lang keys: " + missing);
    }

    @Test
    void everyEntryDeclaresIdMatchingItsPath() {
        // Modonomicon requires each entry to carry an explicit `id` equal to
        // <namespace>:<path-under-entries> (it is NOT derived from the file path at
        // decode time); a missing id fails the entry decode and cascades into
        // "Page file references unknown entry" errors, and the book silently ships
        // broken. Entry files live directly under entries/; page files sit in a
        // pages/ subfolder and are skipped here.
        Path entries = BOOK_DIR.resolve("entries");
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(entries)) {
            for (Path file : files
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/pages/"))
                    .sorted().toList()) {
                String rel = entries.relativize(file).toString().replace('\\', '/');
                String expected = "productivefrogs:" + rel.substring(0, rel.length() - ".json".length());
                JsonObject entry = readJson(file).getAsJsonObject();
                String id = entry.has("id") ? entry.get("id").getAsString() : "<missing>";
                if (!expected.equals(id)) {
                    problems.add(rel + " has id=" + id + ", expected " + expected);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(problems.isEmpty(), "entry id must match its path: " + problems);
    }

    private static long count(String s, char c) {
        return s.chars().filter(ch -> ch == c).count();
    }

    private static JsonElement readJson(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Resources root = the {@code build/resources/main} dir carrying both assets/ and data/. */
    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(GuidebookTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate en_us.json on the test classpath", e);
        }
    }
}
