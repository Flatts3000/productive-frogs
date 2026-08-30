package com.flatts.productivefrogs;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards against two crafting recipes the grid can't tell apart - the same input
 * shape producing different results, where the recipe manager silently reaches
 * only one and the other becomes uncraftable. This shipped once on the 1.21.1
 * line (the Wither and End Dragon altar hatches both used obsidian + chest, so
 * only the dragon hatch was craftable), and it is invisible to JEI (which lists
 * both) and to a normal playtest unless someone happens to try the losing recipe.
 *
 * <p>Pure JSON, no Minecraft bootstrap (the {@code AdvancementSetTest} style).
 * Recipes are compared within their matching category - shaped vs shaped,
 * shapeless vs shapeless, and each cooking type against its own kind - because
 * those are the sets the grid / furnace resolves among. Shaped signatures fold in
 * the horizontal mirror, since vanilla matches a shaped recipe or its mirror.
 * Component ingredients (a Froglight stamped with a variant) keep their
 * components in the signature, so the per-variant smelt recipes stay distinct.
 */
class RecipeConflictTest {

    private static final Path RECIPE_DIR = recipeDir();

    private static Path recipeDir() {
        try {
            // casting_mold.json ships on every line, so it's a stable classpath anchor.
            return Paths.get(RecipeConflictTest.class
                .getResource("/data/productivefrogs/recipe/casting_mold.json").toURI()).getParent();
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("could not locate the recipe dir on the test classpath", e);
        }
    }

    private static List<Path> recipeFiles() {
        try (Stream<Path> files = Files.list(RECIPE_DIR)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + RECIPE_DIR, e);
        }
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    /** One recipe's input signature, its result, its file name, and its mod-load gating. */
    private record Entry(String file, String result, java.util.Set<String> requiresMods,
                         java.util.Set<String> forbidsMods) {
        /** Two recipes can be active at once unless one requires a mod the other forbids. */
        boolean canCoexistWith(Entry o) {
            return java.util.Collections.disjoint(requiresMods, o.forbidsMods)
                && java.util.Collections.disjoint(o.requiresMods, forbidsMods);
        }
    }

    @Test
    void noTwoRecipesShareAnIndistinguishableInput() {
        Map<String, List<Entry>> bySignature = new LinkedHashMap<>();
        for (Path file : recipeFiles()) {
            JsonObject recipe = parse(file);
            if (!recipe.has("type")) {
                continue;
            }
            String signature = signatureOf(recipe);
            if (signature == null) {
                continue; // not a grid/furnace recipe we can collide (custom PF types)
            }
            java.util.Set<String> requires = new java.util.HashSet<>();
            java.util.Set<String> forbids = new java.util.HashSet<>();
            collectModConditions(recipe, requires, forbids);
            bySignature.computeIfAbsent(signature, k -> new ArrayList<>())
                .add(new Entry(file.getFileName().toString(), resultOf(recipe), requires, forbids));
        }

        // A collision is only real if two same-signature recipes can be active together;
        // recipes gated on exclusive mods (ae2 vs "refinedstorage and not ae2") never are.
        List<String> conflicts = new ArrayList<>();
        for (List<Entry> group : bySignature.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    Entry a = group.get(i);
                    Entry b = group.get(j);
                    if (a.canCoexistWith(b)) {
                        conflicts.add(a.file() + " -> " + a.result() + "  ==  " + b.file() + " -> " + b.result());
                    }
                }
            }
        }
        assertTrue(conflicts.isEmpty(), () ->
            "Recipes with an indistinguishable input (only one is craftable; the rest are dead):\n  "
                + String.join("\n  ", conflicts));
    }

    /** Pull {@code mod_loaded} (requires) and {@code not mod_loaded} (forbids) out of a recipe's conditions. */
    private static void collectModConditions(JsonObject recipe, java.util.Set<String> requires,
                                             java.util.Set<String> forbids) {
        if (!recipe.has("neoforge:conditions")) {
            return;
        }
        for (JsonElement condEl : recipe.getAsJsonArray("neoforge:conditions")) {
            JsonObject cond = condEl.getAsJsonObject();
            String type = cond.has("type") ? cond.get("type").getAsString() : "";
            if ("neoforge:mod_loaded".equals(type)) {
                requires.add(cond.get("modid").getAsString());
            } else if ("neoforge:not".equals(type) && cond.has("value")) {
                JsonObject inner = cond.getAsJsonObject("value");
                if ("neoforge:mod_loaded".equals(inner.get("type").getAsString())) {
                    forbids.add(inner.get("modid").getAsString());
                }
            }
        }
    }

    /** A signature two recipes share iff the grid / furnace can't tell their inputs apart, or null if not applicable. */
    private static String signatureOf(JsonObject recipe) {
        String type = recipe.get("type").getAsString();
        return switch (type) {
            case "minecraft:crafting_shaped" -> shapedSignature(recipe);
            case "minecraft:crafting_shapeless" -> "shapeless|" + shapelessSignature(recipe);
            // Each cooking type is its own manager, so key the signature by type.
            case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
                 "minecraft:campfire_cooking" -> type + "|" + canon(recipe.get("ingredient"));
            default -> null;
        };
    }

    /**
     * Trimmed grid of per-cell ingredient tokens, folded to the min of itself and its
     * horizontal mirror.
     *
     * <p>Returns its own namespace prefix rather than taking one, because a shaped
     * recipe that trims to a SINGLE cell is indistinguishable from a one-ingredient
     * shapeless recipe: both match one item sitting anywhere in the grid, and the
     * recipe manager resolves only one of them. Keeping shaped and shapeless in
     * separate namespaces is right for every other size and wrong for this one, so a
     * 1x1 shaped recipe is signed as shapeless (the Rainbow Froglight white-dye
     * recipe is the first 1x1 in the tree, which is what made this load-bearing).
     */
    private static String shapedSignature(JsonObject recipe) {
        JsonArray patternArr = recipe.getAsJsonArray("pattern");
        List<String> rows = new ArrayList<>();
        for (JsonElement row : patternArr) {
            rows.add(row.getAsString());
        }
        JsonObject key = recipe.getAsJsonObject("key");

        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        // Build a grid of ingredient tokens ("" for a space / missing key char).
        String[][] grid = new String[rows.size()][width];
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < width; c++) {
                char ch = c < rows.get(r).length() ? rows.get(r).charAt(c) : ' ';
                grid[r][c] = ch == ' ' || !key.has(String.valueOf(ch)) ? "" : canon(key.get(String.valueOf(ch)));
            }
        }
        String[][] trimmed = trim(grid);
        if (trimmed.length == 1 && trimmed[0].length == 1) {
            // One cell: the grid cannot tell this from a one-ingredient shapeless
            // recipe, so sign it the way shapelessSignature would.
            return "shapeless|" + trimmed[0][0];
        }
        String normal = serializeGrid(trimmed);
        String mirror = serializeGrid(trim(mirror(grid)));
        return "shaped|" + (normal.compareTo(mirror) <= 0 ? normal : mirror);
    }

    /** Sorted multiset of the ingredient tokens (order-independent, like the crafting table). */
    private static String shapelessSignature(JsonObject recipe) {
        List<String> ings = new ArrayList<>();
        for (JsonElement ing : recipe.getAsJsonArray("ingredients")) {
            ings.add(canon(ing));
        }
        ings.sort(null);
        return String.join(",", ings);
    }

    /**
     * Canonical, order-stable token for one ingredient. A bare string or {@code {item}}
     * becomes {@code item:<id>}; {@code {tag}} becomes {@code tag:<id>}; anything richer
     * (a {@code neoforge:components} ingredient, or an alternatives array) is serialized
     * with sorted object keys and sorted array members so equivalent forms collapse and
     * genuinely different ones (a variant-stamped Froglight) stay distinct.
     */
    private static String canon(JsonElement ing) {
        if (ing.isJsonPrimitive()) {
            return "item:" + ing.getAsString();
        }
        if (ing.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonElement e : ing.getAsJsonArray()) {
                parts.add(canon(e));
            }
            parts.sort(null);
            return "any[" + String.join("|", parts) + "]";
        }
        JsonObject o = ing.getAsJsonObject();
        if (o.size() == 1 && o.has("item")) {
            return "item:" + o.get("item").getAsString();
        }
        if (o.size() == 1 && o.has("tag")) {
            return "tag:" + o.get("tag").getAsString();
        }
        return canonJson(o);
    }

    /** Deterministic serialization with sorted object keys (arrays keep order). */
    private static String canonJson(JsonElement e) {
        if (e.isJsonObject()) {
            Map<String, String> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
                sorted.put(entry.getKey(), canonJson(entry.getValue()));
            }
            return sorted.toString();
        }
        if (e.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonElement el : e.getAsJsonArray()) {
                parts.add(canonJson(el));
            }
            return parts.toString();
        }
        return e.toString();
    }

    private static String resultOf(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result == null) {
            return "?";
        }
        if (result.isJsonObject() && result.getAsJsonObject().has("id")) {
            return result.getAsJsonObject().get("id").getAsString();
        }
        if (result.isJsonPrimitive()) {
            return result.getAsString();
        }
        return canonJson(result);
    }

    private static String[][] mirror(String[][] grid) {
        String[][] out = new String[grid.length][];
        for (int r = 0; r < grid.length; r++) {
            int w = grid[r].length;
            out[r] = new String[w];
            for (int c = 0; c < w; c++) {
                out[r][c] = grid[r][w - 1 - c];
            }
        }
        return out;
    }

    /** Drop fully-empty border rows and columns, so equal shapes placed anywhere match. */
    private static String[][] trim(String[][] grid) {
        int rows = grid.length;
        int cols = rows == 0 ? 0 : grid[0].length;
        int top = 0, bottom = rows - 1, left = 0, right = cols - 1;
        while (top <= bottom && rowEmpty(grid, top)) top++;
        while (bottom >= top && rowEmpty(grid, bottom)) bottom--;
        while (left <= right && colEmpty(grid, left)) left++;
        while (right >= left && colEmpty(grid, right)) right--;
        if (top > bottom || left > right) {
            return new String[0][0];
        }
        String[][] out = new String[bottom - top + 1][right - left + 1];
        for (int r = top; r <= bottom; r++) {
            for (int c = left; c <= right; c++) {
                out[r - top][c - left] = grid[r][c];
            }
        }
        return out;
    }

    private static boolean rowEmpty(String[][] grid, int r) {
        for (String cell : grid[r]) {
            if (!cell.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean colEmpty(String[][] grid, int c) {
        for (String[] row : grid) {
            if (!row[c].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String serializeGrid(String[][] grid) {
        List<String> rows = new ArrayList<>();
        for (String[] row : grid) {
            rows.add(String.join("|", row));
        }
        return String.join("/", rows);
    }
}
