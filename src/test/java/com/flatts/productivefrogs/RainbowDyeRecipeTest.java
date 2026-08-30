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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Completeness and balance gate for the Rainbow Froglight dye set.
 *
 * <p>A Rainbow Froglight is one item stack - every one is a
 * {@code configurable_froglight} stamped with the {@code rainbow} variant - so the
 * grid arrangement is the only thing that picks the colour. That makes the set
 * fragile in two ways {@link RecipeConflictTest} does not cover:
 *
 * <ol>
 *   <li>A deleted or misnamed file leaves a dye colour unobtainable, and nothing
 *       else notices - there is no "all sixteen" invariant anywhere else.</li>
 *   <li>The set's fairness rests on every recipe paying the same
 *       {@code DYE_PER_FROGLIGHT}. Edit a pattern to add or drop a Froglight
 *       without touching {@code count} and one colour silently becomes the
 *       best (or worst) deal in the mod.</li>
 * </ol>
 *
 * <p>Distinctness itself is {@code RecipeConflictTest}'s job; this test asserts the
 * set is whole and priced uniformly. Regenerate with
 * {@code scripts/generate_rainbow_dye_recipes.py} rather than hand-editing.
 */
class RainbowDyeRecipeTest {

    /** Yield per Rainbow Froglight consumed. Must match the generator's constant. */
    private static final int DYE_PER_FROGLIGHT = 8;

    private static final String VARIANT = "productivefrogs:rainbow";

    /** Vanilla's sixteen dye colours, in {@code DyeColor} order. */
    private static final List<String> DYE_COLOURS = List.of(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static final Path RECIPE_DIR = resourcesRoot().resolve("data/productivefrogs/recipe");

    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(RainbowDyeRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    static Stream<String> dyeColours() {
        return DYE_COLOURS.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dyeColours")
    void everyDyeColourHasARainbowFroglightRecipe(String colour) {
        Path file = RECIPE_DIR.resolve("rainbow_froglight_to_" + colour + "_dye.json");
        assertTrue(Files.exists(file), () ->
            colour + " dye has no Rainbow Froglight recipe (" + file.getFileName()
                + "). Without it that colour is unobtainable from the rainbow lane; "
                + "regenerate with scripts/generate_rainbow_dye_recipes.py.");

        JsonObject recipe = parse(file);
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(),
            colour + ": must be shaped - the arrangement is what picks the colour, "
                + "so a shapeless recipe here collides with all fifteen others");
        assertEquals("minecraft:" + colour + "_dye",
            recipe.getAsJsonObject("result").get("id").getAsString(), colour + ": result");

        // Exactly one ingredient, and it is the rainbow-stamped Froglight.
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals(1, key.size(), colour + ": the pattern takes one ingredient only");
        JsonObject ingredient = key.getAsJsonObject("X");
        assertEquals(VARIANT,
            ingredient.getAsJsonObject("components").get("productivefrogs:slime_variant").getAsString(),
            colour + ": ingredient must be the rainbow-stamped Froglight");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dyeColours")
    void yieldIsUniformPerFroglight(String colour) {
        JsonObject recipe = parse(RECIPE_DIR.resolve("rainbow_froglight_to_" + colour + "_dye.json"));
        int froglights = 0;
        for (JsonElement row : recipe.getAsJsonArray("pattern")) {
            froglights += row.getAsString().length() - row.getAsString().replace("X", "").length();
        }
        final int used = froglights;
        int expected = used * DYE_PER_FROGLIGHT;
        assertEquals(expected, recipe.getAsJsonObject("result").get("count").getAsInt(),
            () -> colour + ": " + used + " Froglight(s) must yield " + expected + " dye ("
                + DYE_PER_FROGLIGHT + " each). Every colour pays the same rate, so no pattern "
                + "is a better deal than another - change the pattern and the count together.");
    }

    /** The patterns must stay inside a crafting grid. */
    @Test
    void everyPatternFitsTheGrid() {
        for (String colour : DYE_COLOURS) {
            JsonArray pattern = parse(RECIPE_DIR.resolve("rainbow_froglight_to_" + colour + "_dye.json"))
                .getAsJsonArray("pattern");
            assertTrue(pattern.size() <= 3, colour + ": pattern is taller than 3 rows");
            for (JsonElement row : pattern) {
                assertEquals(pattern.get(0).getAsString().length(), row.getAsString().length(),
                    colour + ": shaped pattern rows must all be the same length");
                assertTrue(row.getAsString().length() <= 3, colour + ": pattern is wider than 3 columns");
            }
        }
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
