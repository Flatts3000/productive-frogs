package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Shape gate for the appliance crafting recipes. The Spawnery and Slime Milker
 * share the 5-cobblestone / 3-planks frame (bonemeal vs slime-ball centre); the
 * Slime Churn uses its own moss / packed-mud frame. Pins the ingredient counts,
 * layout, and result ids, plus the {@code productivefrogs:config_enabled}
 * conditions that make each appliance per-feature disableable (Spawnery off by
 * default; Milker + Churn on by default, #196). A stray hand-edit or a missing /
 * wrong config gate then fails the build.
 */
class SpawneryRecipeTest {

    private static final Path RECIPE_ROOT =
        resourcesRoot().resolve("data/productivefrogs/recipe");

    private static Path resourcesRoot() {
        try {
            // <root>/assets/productivefrogs/lang/en_us.json is four parents below <root>.
            Path lang = Paths.get(SpawneryRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    @Test
    void spawneryRecipeHasExpectedShape() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("spawnery.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "spawnery: recipe type");
        assertResultId(recipe, "productivefrogs:spawnery");

        assertPattern(recipe, "PPP", "CMC", "CCC");
        assertIngredientItem(recipe, "C", "minecraft:cobblestone");
        assertIngredientItem(recipe, "M", "minecraft:bone_meal");
        assertIngredientTag(recipe, "P", "minecraft:planks");

        // Config gate: a single config_enabled condition keyed on "spawnery".
        assertConfigGate(recipe, "spawnery");
    }

    @Test
    void slimeMilkerRecipeHasExpectedShape() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("slime_milker.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "slime_milker: recipe type");
        assertResultId(recipe, "productivefrogs:slime_milker");

        assertPattern(recipe, "PPP", "CSC", "CCC");
        assertIngredientItem(recipe, "C", "minecraft:cobblestone");
        assertIngredientItem(recipe, "S", "minecraft:slime_ball");
        assertIngredientTag(recipe, "P", "minecraft:planks");

        // Config gate (#196): the Milker is now per-appliance disableable.
        assertConfigGate(recipe, "slime_milker");
    }

    @Test
    void slimeChurnRecipeIsConfigGated() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("slime_churn.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "slime_churn: recipe type");
        assertResultId(recipe, "productivefrogs:slime_churn");

        assertPattern(recipe, "PMP", "PSP", "CCC");
        assertIngredientTag(recipe, "P", "minecraft:planks");
        assertIngredientItem(recipe, "M", "minecraft:moss_block");
        assertIngredientItem(recipe, "S", "minecraft:slime_ball");
        assertIngredientItem(recipe, "C", "minecraft:packed_mud");

        // Config gate (#196): the Churn is per-appliance disableable.
        assertConfigGate(recipe, "slime_churn");
    }

    @Test
    void crucibleRecipeIsConfigGated() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("crucible.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "crucible: recipe type");
        assertResultId(recipe, "productivefrogs:crucible");
        assertPattern(recipe, "I I", "B B", "BBB");
        assertIngredientItem(recipe, "I", "minecraft:iron_ingot");
        assertIngredientItem(recipe, "B", "minecraft:brick");
        assertConfigGate(recipe, "crucible");
    }

    @Test
    void castingMoldRecipeIsConfigGated() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("casting_mold.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "casting_mold: recipe type");
        assertResultId(recipe, "productivefrogs:casting_mold");
        assertPattern(recipe, "B B", "I I", "III");
        assertIngredientItem(recipe, "I", "minecraft:iron_ingot");
        assertIngredientItem(recipe, "B", "minecraft:brick");
        assertConfigGate(recipe, "casting_mold");
    }

    /** Assert the recipe carries exactly one {@code productivefrogs:config_enabled} condition with {@code config == key}. */
    private static void assertConfigGate(JsonObject recipe, String key) {
        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertNotNull(conditions, key + ": must carry a config_enabled condition");
        assertEquals(1, conditions.size(), key + ": exactly one condition");
        JsonObject cond = conditions.get(0).getAsJsonObject();
        assertEquals("productivefrogs:config_enabled", cond.get("type").getAsString(), key + ": condition type");
        assertEquals(key, cond.get("config").getAsString(), key + ": condition config key");
    }

    private static void assertPattern(JsonObject recipe, String... rows) {
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals(rows.length, pattern.size(), "pattern row count");
        for (int i = 0; i < rows.length; i++) {
            assertEquals(rows[i], pattern.get(i).getAsString(), "pattern row " + i);
        }
    }

    private static void assertIngredientItem(JsonObject recipe, String key, String item) {
        String ing = recipe.getAsJsonObject("key").get(key).getAsString();
        assertEquals(item, ing, "key '" + key + "' item");
    }

    private static void assertIngredientTag(JsonObject recipe, String key, String tag) {
        String ing = recipe.getAsJsonObject("key").get(key).getAsString();
        assertEquals("#" + tag, ing, "key '" + key + "' tag");
    }

    private static void assertResultId(JsonObject recipe, String id) {
        assertEquals(id, recipe.getAsJsonObject("result").get("id").getAsString(), "result id");
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
