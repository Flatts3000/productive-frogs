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
 * Shape gate for the Froglight Cleaver crafting recipe (#212). Pins the
 * maintainer-prescribed grid (3 Nether Star froglights, 2 Dragon Egg froglights,
 * 2 Dragon's Breath), the component-ingredient variants, the result, and the
 * {@code froglight_weapon} config gate. A stray edit then fails the build.
 */
class FroglightWeaponRecipeTest {

    private static final Path RECIPE =
        resourcesRoot().resolve("data/productivefrogs/recipe/froglight_cleaver.json");

    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(FroglightWeaponRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    @Test
    void cleaverRecipeHasPrescribedShape() {
        JsonObject recipe = parse(RECIPE);
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "cleaver: recipe type");
        assertEquals("productivefrogs:froglight_cleaver",
            recipe.getAsJsonObject("result").get("id").getAsString(), "cleaver: result");

        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals("NNB", pattern.get(0).getAsString(), "cleaver: row 0");
        assertEquals("DNB", pattern.get(1).getAsString(), "cleaver: row 1");
        assertEquals("D  ", pattern.get(2).getAsString(), "cleaver: row 2");

        JsonObject key = recipe.getAsJsonObject("key");
        assertFroglightVariant(key.getAsJsonObject("N"), "productivefrogs:nether_star");
        assertFroglightVariant(key.getAsJsonObject("D"), "productivefrogs:dragon_egg");
        assertEquals("minecraft:dragon_breath", key.getAsJsonObject("B").get("item").getAsString(),
            "cleaver: B = dragon's breath");

        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertNotNull(conditions, "cleaver: must carry a config_enabled condition");
        assertEquals("froglight_weapon",
            conditions.get(0).getAsJsonObject().get("config").getAsString(), "cleaver: gated on froglight_weapon");
    }

    /** A component-ingredient requiring a configurable_froglight of the given variant. */
    private static void assertFroglightVariant(JsonObject ingredient, String variant) {
        assertEquals("neoforge:components", ingredient.get("type").getAsString(), "froglight ingredient type");
        assertEquals("productivefrogs:configurable_froglight",
            ingredient.getAsJsonArray("items").get(0).getAsString(), "froglight ingredient item");
        assertEquals(variant,
            ingredient.getAsJsonObject("components").get("productivefrogs:slime_variant").getAsString(),
            "froglight ingredient variant");
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
