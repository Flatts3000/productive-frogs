package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Shape gate for the predation Phase 3 recipes (#281), pinning the maintainer's
 * rulings (2026-07-03):
 * <ul>
 *   <li>Ender Net = a Frog Net crafted with an ender pearl (shapeless upgrade).</li>
 *   <li>Slurry Press = iron plate over an obsidian body with a pearl core.</li>
 *   <li>Mob Slurry Basin = obsidian basin with a pearl embedded.</li>
 *   <li>Slime Milk Basin = packed-mud basin with a slime ball; deliberately
 *       UNGATED (slime-side machinery, not predation content).</li>
 * </ul>
 * The three predation recipes carry the {@code config_enabled} / {@code predators}
 * condition. Texture direction follows these recipes (texture-follows-recipe).
 */
class PredationRecipeTest {

    private static final Path RECIPE_ROOT =
        resourcesRoot().resolve("data/productivefrogs/recipe");

    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(PredationRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    @Test
    void enderNetIsAFrogNetPlusAnEnderPearl() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("ender_net.json"));
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString(),
            "ender_net: a shapeless upgrade of the Frog Net (maintainer ruling)");
        assertEquals("productivefrogs:ender_net",
            recipe.getAsJsonObject("result").get("id").getAsString(), "ender_net: result id");
        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        assertEquals(2, ingredients.size(), "ender_net: exactly two ingredients");
        assertTrue(containsString(ingredients, "productivefrogs:frog_net"), "ender_net: needs a Frog Net");
        assertTrue(containsString(ingredients, "minecraft:ender_pearl"), "ender_net: needs an ender pearl");
        assertPredatorsGate(recipe, "ender_net");
    }

    @Test
    void slurryPressIsIronOverObsidianWithAPearlCore() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("slurry_press.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "slurry_press: recipe type");
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals("III", pattern.get(0).getAsString(), "slurry_press: iron plate row");
        assertEquals("OEO", pattern.get(1).getAsString(), "slurry_press: pearl core row");
        assertEquals("OOO", pattern.get(2).getAsString(), "slurry_press: obsidian base row");
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:iron_ingot", key.get("I").getAsString(), "slurry_press: I = iron ingot");
        assertEquals("minecraft:obsidian", key.get("O").getAsString(), "slurry_press: O = obsidian");
        assertEquals("minecraft:ender_pearl", key.get("E").getAsString(), "slurry_press: E = ender pearl");
        assertPredatorsGate(recipe, "slurry_press");
    }

    @Test
    void mobSlurryBasinIsAnObsidianBasinWithAPearl() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("mob_slurry_basin.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "mob_slurry_basin: recipe type");
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals("O O", pattern.get(0).getAsString(), "mob_slurry_basin: open basin mouth");
        assertEquals("OEO", pattern.get(1).getAsString(), "mob_slurry_basin: pearl row");
        assertEquals("OOO", pattern.get(2).getAsString(), "mob_slurry_basin: base row");
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:obsidian", key.get("O").getAsString(), "mob_slurry_basin: O = obsidian");
        assertEquals("minecraft:ender_pearl", key.get("E").getAsString(), "mob_slurry_basin: E = ender pearl");
        assertPredatorsGate(recipe, "mob_slurry_basin");
    }

    @Test
    void slimeMilkBasinIsAMudBasinWithASlimeBallAndIsUngated() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("slime_milk_basin.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "slime_milk_basin: recipe type");
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals("P P", pattern.get(0).getAsString(), "slime_milk_basin: open basin mouth");
        assertEquals("PSP", pattern.get(1).getAsString(), "slime_milk_basin: slime row");
        assertEquals("PPP", pattern.get(2).getAsString(), "slime_milk_basin: base row");
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:packed_mud", key.get("P").getAsString(), "slime_milk_basin: P = packed mud");
        assertEquals("minecraft:slime_ball", key.get("S").getAsString(), "slime_milk_basin: S = slime ball");
        assertNull(recipe.get("neoforge:conditions"),
            "slime_milk_basin: deliberately UNGATED (slime-side machinery)");
    }

    private static void assertPredatorsGate(JsonObject recipe, String name) {
        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertNotNull(conditions, name + ": must carry the predators config_enabled condition");
        assertEquals(1, conditions.size(), name + ": exactly one condition");
        JsonObject cond = conditions.get(0).getAsJsonObject();
        assertEquals("productivefrogs:config_enabled", cond.get("type").getAsString(), name + ": condition type");
        assertEquals("predators", cond.get("config").getAsString(), name + ": condition config key");
    }

    private static boolean containsString(JsonArray array, String value) {
        for (int i = 0; i < array.size(); i++) {
            if (value.equals(array.get(i).getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject parse(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
