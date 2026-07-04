package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Structure gate for the generated cross-mod crush recipes
 * ({@code scripts/generate_crush_recipes.ps1}, spec in
 * {@code docs/v1_3_crush_recipes.md}).
 *
 * <p>These recipes are {@code mod_loaded}-gated for Mekanism / Immersive
 * Engineering / EnderIO, so CI (which has none of them installed) can't load or
 * run them in-world - that needs a manual {@code runClient} smoke test per mod.
 * What CI <em>can</em> guard is that every generated file is well-formed and
 * keeps the verified shape: the right recipe type, the per-variant
 * {@code neoforge:components} ingredient on {@code configurable_froglight}, a 2x
 * output, and an {@code alltheores} condition exactly when the output is an ATO
 * dust. A generator regression or a stray hand-edit then fails the build instead
 * of silently shipping a dead recipe.
 */
class CrushRecipeTest {

    private static final Path RECIPE_ROOT =
        resourcesRoot().resolve("data/productivefrogs/recipe");

    /** Crusher modid -> the recipe {@code type} its loader expects. */
    private static final Map<String, String> RECIPE_TYPE = Map.of(
        "mekanism", "mekanism:enriching",
        "immersiveengineering", "immersiveengineering:crusher",
        "enderio", "enderio:sag_milling");

    private static Path resourcesRoot() {
        try {
            // <root>/assets/productivefrogs/lang/en_us.json is four parents below <root>.
            Path lang = Paths.get(CrushRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    /** Every generated crush recipe, as {@code <modid>/<variant>.json} relative paths. */
    static Stream<String> crushRecipes() {
        try (Stream<Path> walk = Files.walk(RECIPE_ROOT)) {
            return walk
                .filter(p -> RECIPE_TYPE.containsKey(p.getParent().getFileName().toString()))
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .map(p -> p.getParent().getFileName() + "/" + p.getFileName())
                .sorted()
                .toList()  // materialize before the walk stream closes
                .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + RECIPE_ROOT, e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("crushRecipes")
    void crushRecipeKeepsVerifiedShape(String relPath) {
        String mod = relPath.substring(0, relPath.indexOf('/'));
        String variant = relPath.substring(relPath.indexOf('/') + 1, relPath.length() - ".json".length());
        JsonObject recipe = parse(RECIPE_ROOT.resolve(relPath));

        // type matches the crusher whose folder it lives in
        assertEquals(RECIPE_TYPE.get(mod), recipe.get("type").getAsString(),
            relPath + ": wrong recipe type");

        // conditions: 1 or 2 mod_loaded entries, the crusher first; alltheores
        // present iff the output is an ATO dust (checked together below).
        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertTrue(conditions != null && conditions.size() >= 1 && conditions.size() <= 2,
            () -> relPath + ": expected 1-2 conditions, got " + (conditions == null ? "none" : conditions.size()));
        for (var c : conditions) {
            assertEquals("neoforge:mod_loaded", c.getAsJsonObject().get("type").getAsString(),
                relPath + ": every condition must be mod_loaded");
        }
        assertEquals(mod, conditions.get(0).getAsJsonObject().get("modid").getAsString(),
            relPath + ": first condition must gate on the crusher mod");

        // input: the per-variant data-component ingredient on the one Froglight item
        JsonObject input = recipe.getAsJsonObject("input");
        assertEquals("neoforge:components", input.get("type").getAsString(), relPath + ": input type");
        assertEquals("productivefrogs:configurable_froglight", input.get("items").getAsString(),
            relPath + ": input item");
        assertEquals("productivefrogs:" + variant,
            input.getAsJsonObject("components").get("productivefrogs:slime_variant").getAsString(),
            relPath + ": input variant component must match the filename");

        // output: always 2x; id namespace must agree with the alltheores gating
        JsonObject out = outputObject(mod, recipe);
        assertEquals(2, out.get("count").getAsInt(), relPath + ": output must be 2x");
        boolean atoOutput = out.get("id").getAsString().startsWith("alltheores:");
        boolean atoGated = conditions.size() == 2
            && "alltheores".equals(conditions.get(1).getAsJsonObject().get("modid").getAsString());
        assertEquals(atoOutput, atoGated,
            relPath + ": an alltheores output must be gated on alltheores, and vice versa");
    }

    /** The output-bearing object, normalizing the three crushers' differing shapes. */
    private static JsonObject outputObject(String mod, JsonObject recipe) {
        return switch (mod) {
            case "mekanism" -> recipe.getAsJsonObject("output");
            case "immersiveengineering" -> recipe.getAsJsonObject("result");
            case "enderio" -> recipe.getAsJsonArray("outputs").get(0).getAsJsonObject().getAsJsonObject("item");
            default -> throw new IllegalStateException("unknown crusher mod: " + mod);
        };
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    /** Guard against an empty sweep silently passing (wrong path, resources not copied). */
    @org.junit.jupiter.api.Test
    void someCrushRecipesExist() {
        List<String> all = crushRecipes().toList();
        assertTrue(all.size() >= 30,
            () -> "expected the full generated crush-recipe set, found " + all.size());
    }
}
