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
 * Shape gate for the Frog Net crafting recipe (#205). Pins the string/stick net
 * pattern, the result id, and the {@code productivefrogs:config_enabled} /
 * {@code frog_net} condition that makes the net per-feature disableable. A stray
 * hand-edit or a missing / wrong config gate then fails the build.
 */
class FrogNetRecipeTest {

    private static final Path RECIPE_ROOT =
        resourcesRoot().resolve("data/productivefrogs/recipe");

    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(FrogNetRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    @Test
    void frogNetRecipeHasExpectedShape() {
        JsonObject recipe = parse(RECIPE_ROOT.resolve("frog_net.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "frog_net: recipe type");
        assertEquals("productivefrogs:frog_net",
            recipe.getAsJsonObject("result").get("id").getAsString(), "frog_net: result id");

        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals(3, pattern.size(), "frog_net: pattern row count");
        assertEquals("SSS", pattern.get(0).getAsString(), "frog_net: row 0");
        assertEquals("S S", pattern.get(1).getAsString(), "frog_net: row 1");
        assertEquals(" I ", pattern.get(2).getAsString(), "frog_net: row 2");

        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:string", key.get("S").getAsString(), "frog_net: S = string");
        assertEquals("minecraft:stick", key.get("I").getAsString(), "frog_net: I = stick");

        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertNotNull(conditions, "frog_net: must carry a config_enabled condition");
        assertEquals(1, conditions.size(), "frog_net: exactly one condition");
        JsonObject cond = conditions.get(0).getAsJsonObject();
        assertEquals("productivefrogs:config_enabled", cond.get("type").getAsString(), "frog_net: condition type");
        assertEquals("frog_net", cond.get("config").getAsString(), "frog_net: condition config key");
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
