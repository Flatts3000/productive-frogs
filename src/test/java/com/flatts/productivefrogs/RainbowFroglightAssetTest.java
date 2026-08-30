package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Asset gate for the Rainbow Froglight's client rendering.
 *
 * <p>Every other Froglight variant renders vanilla's ochre froglight sprites
 * multiplied by its {@code primary_color}, which can only ever produce a flat
 * color. Rainbow therefore ships real textures and renders them UNTINTED, which
 * makes it the one variant whose look depends on a chain of client-only JSON:
 * an item definition that selects on the {@code slime_variant} component, a
 * model that points at PF's own sprites, and the absence of any
 * {@code tintindex}.
 *
 * <p>None of that is reachable by GameTest - a dedicated server never loads
 * models - so a typo'd path or a stray tint index would ship silently and only
 * surface as a missing-texture cube or a magenta-washed Froglight in someone's
 * world. This test walks the chain as plain JSON.
 */
class RainbowFroglightAssetTest {

    private static final String NS = "productivefrogs";

    private static final Path ASSETS = resourcesRoot().resolve("assets/" + NS);

    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(RainbowFroglightAssetTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate resources root on the test classpath", e);
        }
    }

    @Test
    void froglightItemSelectsTheRainbowModelOnTheVariantComponent() {
        JsonObject model = parse(ASSETS.resolve("items/configurable_froglight.json"))
            .getAsJsonObject("model");

        assertEquals("minecraft:select", model.get("type").getAsString(),
            "the Froglight item definition must switch models, or rainbow can never render untinted");
        assertEquals("minecraft:component", model.get("property").getAsString(),
            "rainbow is identified by a data component, not a blockstate");
        assertEquals(NS + ":slime_variant", model.get("component").getAsString(),
            "the variant id lives in the slime_variant component");

        JsonArray cases = model.getAsJsonArray("cases");
        assertEquals(1, cases.size(), "exactly one variant needs its own model today");
        JsonObject rainbow = cases.get(0).getAsJsonObject();
        assertEquals(NS + ":rainbow", rainbow.get("when").getAsString(), "case value");
        assertEquals(NS + ":item/rainbow_froglight",
            rainbow.getAsJsonObject("model").get("model").getAsString(), "rainbow model path");

        // The other 39 variants must keep their tint - losing it turns every
        // Froglight in the mod into a plain ochre one.
        JsonObject fallback = model.getAsJsonObject("fallback");
        assertEquals(NS + ":item/configurable_froglight", fallback.get("model").getAsString(),
            "fallback model");
        assertEquals(NS + ":configurable_froglight",
            fallback.getAsJsonArray("tints").get(0).getAsJsonObject().get("type").getAsString(),
            "fallback must keep the variant tint source");
    }

    @Test
    void rainbowModelIsUntintedAndItsTexturesExist() {
        Path blockModel = ASSETS.resolve("models/block/rainbow_froglight.json");
        assertTrue(Files.exists(blockModel), "missing " + blockModel.getFileName());

        // The item model is the middle link: the item definition names it, and it
        // is the only thing pointing at the untinted block model. Typo its parent
        // to block/resource_froglight and every Rainbow Froglight silently renders
        // as a magenta-tinted ochre one, with the rest of this test still green.
        Path itemModel = ASSETS.resolve("models/item/rainbow_froglight.json");
        assertTrue(Files.exists(itemModel), "missing the item model that parents the block model");
        assertEquals(NS + ":block/rainbow_froglight", parse(itemModel).get("parent").getAsString(),
            "the rainbow item model must parent the UNTINTED rainbow block model");

        JsonObject model = parse(blockModel);

        // A tintindex here would multiply the baked hues by primary_color and
        // wash the whole spectrum toward one color - the exact bug this variant
        // exists to avoid.
        assertFalse(model.toString().contains("tintindex"),
            "the rainbow model must carry NO tintindex - its colors are baked into the texture, "
                + "and any tint would multiply them toward a single hue");

        JsonObject textures = model.getAsJsonObject("textures");
        for (String slot : textures.keySet()) {
            String id = textures.get(slot).getAsString();
            assertTrue(id.startsWith(NS + ":"),
                "rainbow must use PF's own sprites, not vanilla's tinted ochre ones: " + slot + " = " + id);
            Path png = resourcesRoot().resolve(
                "assets/" + NS + "/textures/" + id.substring((NS + ":").length()) + ".png");
            assertTrue(Files.exists(png), () ->
                "texture '" + id + "' (slot " + slot + ") has no file at " + png
                    + " - run scripts/generate_rainbow_froglight_textures.py");
        }
    }

    /**
     * The placed block picks its model from the block entity's variant, through
     * {@code VariantBlockStateModel}. Three things in that JSON are silent if wrong:
     * the dispatch type (a typo falls back to vanilla's single-variant codec and the
     * whole file still loads, rendering every Froglight tinted), the model paths, and
     * the ROTATION - the fallback and the per-variant model within one axis entry must
     * carry identical rotation, or a rainbow Froglight placed sideways renders with
     * the upright model's quads.
     */
    @Test
    void froglightBlockstateDispatchesOnTheVariantWithMatchingRotations() {
        JsonObject variants = parse(ASSETS.resolve("blockstates/configurable_froglight.json"))
            .getAsJsonObject("variants");
        assertEquals(3, variants.size(), "one entry per axis");

        for (String axis : variants.keySet()) {
            JsonObject entry = variants.getAsJsonObject(axis);
            assertEquals(NS + ":variant_model", entry.get("type").getAsString(),
                axis + ": must dispatch through the variant-aware model, or the block "
                    + "cannot see the block entity at all");

            JsonObject fallback = entry.getAsJsonObject("fallback");
            assertEquals(NS + ":block/resource_froglight", fallback.get("model").getAsString(),
                axis + ": fallback keeps the shared tinted model for the other variants");

            JsonObject perVariant = entry.getAsJsonObject("variants");
            assertTrue(perVariant.has(NS + ":rainbow"), axis + ": no rainbow entry");
            JsonObject rainbow = perVariant.getAsJsonObject(NS + ":rainbow");
            assertEquals(NS + ":block/rainbow_froglight", rainbow.get("model").getAsString(),
                axis + ": rainbow model path");

            for (String rot : new String[] {"x", "y", "uvlock"}) {
                assertEquals(fallback.has(rot) ? fallback.get(rot).toString() : "<absent>",
                    rainbow.has(rot) ? rainbow.get(rot).toString() : "<absent>",
                    axis + ": '" + rot + "' must match between fallback and rainbow, "
                        + "otherwise the two render at different orientations");
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
