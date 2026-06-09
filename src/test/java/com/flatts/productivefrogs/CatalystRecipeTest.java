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
 * Shape gate for the four Slime Milk catalyst recipes (docs/slime_milk_catalysts.md).
 * Pins: every catalyst recipe carries the {@code productivefrogs:config_enabled}
 * condition keyed on its own per-catalyst config id (#201 - {@code count_catalyst},
 * {@code speed_catalyst}, {@code quantity_catalyst}, {@code infinite_catalyst}; each
 * ANDs the catalysts master under the hood), each result id is correct, and the
 * Infinite catalyst is genuinely <i>built from</i> Count catalysts (the design
 * decision: Infinite is the final tier of the count line). A dropped condition, a
 * wrong gate key, or a recipe that stopped consuming Count catalysts then fails the build.
 */
class CatalystRecipeTest {

    private static final Path RECIPE_ROOT =
        resourcesRoot().resolve("data/productivefrogs/recipe");

    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(CatalystRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    @Test
    void everyCatalystRecipeIsConfigGatedAndYieldsItsItem() {
        assertGatedRecipe("count_catalyst.json", "productivefrogs:count_catalyst", "count_catalyst");
        assertGatedRecipe("speed_catalyst.json", "productivefrogs:speed_catalyst", "speed_catalyst");
        assertGatedRecipe("quantity_catalyst.json", "productivefrogs:quantity_catalyst", "quantity_catalyst");
        assertGatedRecipe("infinite_catalyst.json", "productivefrogs:infinite_catalyst", "infinite_catalyst");
    }

    @Test
    void infiniteCatalystIsBuiltFromCountCatalysts() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("infinite_catalyst.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "infinite: recipe type");
        // The premium "final tier of the count line": its key must reference the
        // Count catalyst as an ingredient.
        JsonObject key = recipe.getAsJsonObject("key");
        boolean usesCount = key.entrySet().stream().anyMatch(e -> {
            JsonObject ing = e.getValue().getAsJsonObject();
            return ing.has("item")
                && "productivefrogs:count_catalyst".equals(ing.get("item").getAsString());
        });
        assertTrue(usesCount, "infinite catalyst must be crafted from count catalysts");
    }

    private static void assertGatedRecipe(String file, String resultId, String configKey) {
        JsonObject recipe = parse(RECIPE_ROOT.resolve(file));
        assertEquals(resultId, recipe.getAsJsonObject("result").get("id").getAsString(), file + ": result id");
        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertNotNull(conditions, file + ": must carry a config_enabled condition");
        assertEquals(1, conditions.size(), file + ": exactly one condition");
        JsonObject cond = conditions.get(0).getAsJsonObject();
        assertEquals("productivefrogs:config_enabled", cond.get("type").getAsString(), file + ": condition type");
        assertEquals(configKey, cond.get("config").getAsString(), file + ": condition config key");
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
