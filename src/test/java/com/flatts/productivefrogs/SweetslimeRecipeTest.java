package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Shape gate for the Sweetslime crafting recipe (docs/frog_breeding.md D6).
 * The recipe is plain vanilla {@code crafting_shapeless} with no conditions, so
 * unlike the cross-mod crush recipes it can't be exercised in-world here without
 * a player - but a typo'd ingredient or a wrong output count would silently break
 * the breeding-item economy, so we pin the verified shape: 1 slime ball + 1 sugar
 * yields 2 Sweetslime.
 */
class SweetslimeRecipeTest {

    private static final Path RECIPE =
        resourcesRoot().resolve("data/productivefrogs/recipe/sweetslime.json");

    private static Path resourcesRoot() {
        try {
            // <root>/assets/productivefrogs/lang/en_us.json is four parents below <root>.
            Path lang = Paths.get(SweetslimeRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    @Test
    void sweetslimeRecipeKeepsVerifiedShape() {
        JsonObject recipe = parse(RECIPE);

        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString(),
            "Sweetslime must be a shapeless crafting recipe");

        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        assertEquals(2, ingredients.size(), "exactly two ingredients (slime ball + sugar)");
        Set<String> items = new HashSet<>();
        for (JsonElement e : ingredients) {
            items.add(e.getAsString());
        }
        assertTrue(items.contains("minecraft:slime_ball"), "must include a slime ball");
        assertTrue(items.contains("minecraft:sugar"), "must include sugar");

        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("productivefrogs:sweetslime", result.get("id").getAsString(),
            "result must be Sweetslime");
        assertEquals(2, result.get("count").getAsInt(),
            "one craft yields two Sweetslime (feeds a breeding pair)");
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
