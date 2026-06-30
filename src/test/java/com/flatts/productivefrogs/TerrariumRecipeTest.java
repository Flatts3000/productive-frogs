package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Shape gate for the four Terrarium block recipes (#185). They're plain vanilla
 * {@code crafting_shaped} with no conditions, so they can't be exercised in-world
 * here without a player - but a typo'd ingredient or a wrong output count would
 * silently break obtainability, so we pin the maintainer-settled shapes.
 */
class TerrariumRecipeTest {

    private static Path recipe(String name) {
        return resourcesRoot().resolve("data/productivefrogs/recipe/" + name + ".json");
    }

    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(TerrariumRecipeTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    /** Every key item used across the recipe's pattern. */
    private static Set<String> keyItems(JsonObject recipe) {
        Set<String> items = new HashSet<>();
        JsonObject key = recipe.getAsJsonObject("key");
        for (String k : key.keySet()) {
            items.add(key.get(k).getAsString());
        }
        return items;
    }

    private static void assertShaped(JsonObject recipe, String resultId, int count) {
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), "must be a shaped recipe");
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals(resultId, result.get("id").getAsString(), "result id");
        assertEquals(count, result.get("count").getAsInt(), "result count");
    }

    @Test
    void controllerIsInfernalTierWithMagmaCore() {
        JsonObject r = parse(recipe("terrarium_controller"));
        assertShaped(r, "productivefrogs:terrarium_controller", 1);
        Set<String> items = keyItems(r);
        assertTrue(items.contains("minecraft:glowstone"), "controller uses glowstone");
        assertTrue(items.contains("minecraft:quartz_block"), "controller uses a quartz block");
        assertTrue(items.contains("minecraft:blaze_rod"), "controller uses blaze rods");
        assertTrue(items.contains("minecraft:magma_cream"), "controller core is magma cream");
        assertTrue(items.contains("minecraft:nether_bricks"), "controller base is nether bricks");
    }

    @Test
    void incubatorHasSweetslimeCore() {
        JsonObject r = parse(recipe("incubator"));
        assertShaped(r, "productivefrogs:incubator", 1);
        Set<String> items = keyItems(r);
        assertTrue(items.contains("productivefrogs:sweetslime"), "incubator core is the breeding treat");
        assertTrue(items.contains("minecraft:glowstone"), "incubator uses glowstone");
        assertTrue(items.contains("minecraft:nether_bricks"), "incubator frame is nether bricks");
    }

    @Test
    void hatchHasHoppersAndChest() {
        JsonObject r = parse(recipe("hatch"));
        assertShaped(r, "productivefrogs:hatch", 1);
        Set<String> items = keyItems(r);
        assertTrue(items.contains("minecraft:hopper"), "hatch pipes out via hoppers");
        assertTrue(items.contains("minecraft:chest"), "hatch stores in a chest core");
        assertTrue(items.contains("minecraft:iron_ingot"), "hatch frame is iron");
    }

    @Test
    void sprinklerIsCheapBulkWithHopper() {
        JsonObject r = parse(recipe("sprinkler"));
        assertShaped(r, "productivefrogs:sprinkler", 4); // cheap + bulk (up to 25 per Terrarium)
        Set<String> items = keyItems(r);
        assertTrue(items.contains("minecraft:hopper"), "sprinkler drips via a hopper");
        assertTrue(items.contains("minecraft:quartz"), "sprinkler nozzle is quartz");
        assertTrue(items.contains("minecraft:nether_brick"), "sprinkler frame is nether brick");
    }
}
