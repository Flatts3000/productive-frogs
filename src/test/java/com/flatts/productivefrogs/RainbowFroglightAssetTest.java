package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
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
     * the dispatch type (NeoForge's dispatch falls back to the vanilla codec only when
     * the key is ABSENT, so a typo'd type is a hard decode error that drops the whole
     * blockstate), the model paths, and the ROTATION - the fallback and the per-variant model within one axis entry must
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

    /**
     * The Rainbow Slime's entity texture is authored in full colour, on both the
     * translucent shell and the inner cube. That only renders as painted while the
     * variant declares {@code untinted_shell}: without it the shell is multiplied by
     * {@code primary_color}, and since the inner cube is seen THROUGH that shell,
     * one flat hue washes over the whole slime. Nothing else catches that - the
     * texture and the flag live in different files and neither is validated against
     * the other.
     */
    @Test
    void rainbowVariantOptsOutOfShellTinting() {
        Path variant = resourcesRoot()
            .resolve("data/" + NS + "/" + NS + "/slime_variant/rainbow.json");
        assertTrue(Files.exists(variant), "missing " + variant.getFileName());
        JsonObject json = parse(variant);
        assertTrue(json.has("untinted_shell") && json.get("untinted_shell").getAsBoolean(),
            "rainbow ships full-colour entity art, so it must set \"untinted_shell\": true - "
                + "otherwise primary_color multiplies over it and the rainbow flattens");

        Path texture = resourcesRoot().resolve(
            "assets/" + NS + "/textures/entity/slime/rainbow_resource_slime.png");
        assertTrue(Files.exists(texture), () ->
            "no baked entity texture at " + texture
                + " - run scripts/generate_rainbow_slime_texture.py");

        // Existence is not enough. With untinted_shell set, primary_color no longer
        // multiplies over this texture, so if the bake ever goes stale or greyscale
        // - someone edits the bake base or the script and forgets to re-run it - the
        // Rainbow Slime ships as a flat grey blob and every test still passes,
        // because the only thing that was giving it colour is now switched off.
        BufferedImage img;
        try {
            img = ImageIO.read(texture.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + texture, e);
        }
        assertMultiHued(img, "outer shell", 0, 8, 32, 16);
        for (int[] face : new int[][] {{6, 16}, {12, 16}, {0, 22}, {6, 22}, {12, 22}, {18, 22}}) {
            assertMultiHued(img, "inner cube face at " + face[0] + "," + face[1],
                face[0], face[1], face[0] + 6, face[1] + 6);
        }
    }

    /** A baked region must be in colour and carry several distinct hues, not one flat tone. */
    private static void assertMultiHued(BufferedImage img, String what,
                                        int minX, int minY, int maxX, int maxY) {
        Set<Integer> colours = new HashSet<>();
        boolean anyChromatic = false;
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                colours.add(argb & 0xFFFFFF);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                anyChromatic |= r != g || g != b;
            }
        }
        assertTrue(anyChromatic, () ->
            what + " is greyscale - the bake is stale or was never run "
                + "(scripts/generate_rainbow_slime_texture.py), and untinted_shell means "
                + "nothing will colour it at render time");
        assertTrue(colours.size() >= 4, () ->
            what + " has only " + colours.size() + " distinct colour(s); a rainbow sweep "
                + "should carry several");
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
