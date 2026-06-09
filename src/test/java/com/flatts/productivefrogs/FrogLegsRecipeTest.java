package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Shape gate for the three Frog Legs cooking recipes (#194): smelting / smoking /
 * campfire all take {@code raw_frog_legs} -> {@code cooked_frog_legs} and carry the
 * {@code productivefrogs:config_enabled} / {@code frog_legs} condition. A dropped
 * gate or a wrong ingredient/result then fails the build.
 */
class FrogLegsRecipeTest {

    private static final Path RECIPE_ROOT =
        resourcesRoot().resolve("data/productivefrogs/recipe");

    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(FrogLegsRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    @Test
    void smeltingCooksRawIntoCooked() {
        assertCookingRecipe("cooked_frog_legs_smelting.json", "minecraft:smelting");
    }

    @Test
    void smokingCooksRawIntoCooked() {
        assertCookingRecipe("cooked_frog_legs_smoking.json", "minecraft:smoking");
    }

    @Test
    void campfireCooksRawIntoCooked() {
        assertCookingRecipe("cooked_frog_legs_campfire.json", "minecraft:campfire_cooking");
    }

    @Test
    void soupIsCraftedFromCookedLegsAndGated() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("frog_legs_soup.json"));
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString(), "soup: recipe type");
        assertEquals("productivefrogs:frog_legs_soup",
            recipe.getAsJsonObject("result").get("id").getAsString(), "soup: result");

        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        boolean usesCookedLegs = false;
        boolean usesBowl = false;
        for (var el : ingredients) {
            String item = el.getAsJsonObject().get("item").getAsString();
            usesCookedLegs |= "productivefrogs:cooked_frog_legs".equals(item);
            usesBowl |= "minecraft:bowl".equals(item);
        }
        assertTrue(usesCookedLegs, "soup: must be crafted from cooked frog legs");
        assertTrue(usesBowl, "soup: must include a bowl (the returned container)");

        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertNotNull(conditions, "soup: must carry a config_enabled condition");
        assertEquals(1, conditions.size(), "soup: exactly one condition");
        JsonObject cond = conditions.get(0).getAsJsonObject();
        assertEquals("productivefrogs:config_enabled", cond.get("type").getAsString(), "soup: condition type");
        assertEquals("frog_legs", cond.get("config").getAsString(), "soup: gated on frog_legs");
    }

    private static void assertCookingRecipe(String file, String type) {
        JsonObject recipe = parse(RECIPE_ROOT.resolve(file));
        assertEquals(type, recipe.get("type").getAsString(), file + ": recipe type");
        assertEquals("productivefrogs:raw_frog_legs",
            recipe.getAsJsonObject("ingredient").get("item").getAsString(), file + ": ingredient");
        assertEquals("productivefrogs:cooked_frog_legs",
            recipe.getAsJsonObject("result").get("id").getAsString(), file + ": result");

        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertNotNull(conditions, file + ": must carry a config_enabled condition");
        assertEquals(1, conditions.size(), file + ": exactly one condition");
        JsonObject cond = conditions.get(0).getAsJsonObject();
        assertEquals("productivefrogs:config_enabled", cond.get("type").getAsString(), file + ": condition type");
        assertEquals("frog_legs", cond.get("config").getAsString(), file + ": condition config key");
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
