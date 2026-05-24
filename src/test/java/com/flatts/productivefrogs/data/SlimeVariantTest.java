package com.flatts.productivefrogs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Codec round-trip tests for {@link SlimeVariant}. Mostly here to pin the
 * optional {@code texture} field added for per-variant inner textures — the
 * codec must accept variant JSONs both with and without the field, and the
 * field must round-trip cleanly through encode → decode.
 */
class SlimeVariantTest {

    private static final String WITHOUT_TEXTURE = """
        {
          "primer_item": "minecraft:iron_ingot",
          "category": "cave",
          "primary_color": 12895428,
          "secondary_color": 14211288
        }
        """;

    private static final String WITH_TEXTURE = """
        {
          "primer_item": "minecraft:iron_ingot",
          "category": "cave",
          "primary_color": 12895428,
          "secondary_color": 14211288,
          "texture": "productivefrogs:textures/entity/slime/iron_resource_slime.png"
        }
        """;

    @Test
    void codecDecodesVariantWithoutOptionalTextureField() {
        SlimeVariant decoded = decode(WITHOUT_TEXTURE);
        assertEquals(ResourceLocation.parse("minecraft:iron_ingot"), decoded.primerItem());
        assertEquals(Category.CAVE, decoded.category());
        assertEquals(12895428, decoded.primaryColor());
        assertEquals(14211288, decoded.secondaryColor());
        assertEquals(1, decoded.weight(), "weight defaults to 1 when omitted");
        assertTrue(decoded.texture().isEmpty(),
            "texture must be empty when the JSON omits the field — every shipped V1 variant lands here");
    }

    @Test
    void codecDecodesVariantWithTextureField() {
        SlimeVariant decoded = decode(WITH_TEXTURE);
        Optional<ResourceLocation> texture = decoded.texture();
        assertTrue(texture.isPresent(), "texture must be present when the JSON includes the field");
        assertEquals(
            ResourceLocation.parse("productivefrogs:textures/entity/slime/iron_resource_slime.png"),
            texture.get()
        );
    }

    @Test
    void codecRoundTripsVariantWithTexture() {
        SlimeVariant original = new SlimeVariant(
            ResourceLocation.parse("minecraft:copper_ingot"),
            Category.CAVE,
            14188339,
            16432204,
            1,
            Optional.of(ResourceLocation.parse("productivefrogs:textures/entity/slime/copper_resource_slime.png"))
        );
        JsonElement encoded = SlimeVariant.CODEC.encodeStart(JsonOps.INSTANCE, original)
            .result()
            .orElseThrow();
        SlimeVariant decoded = SlimeVariant.CODEC.parse(JsonOps.INSTANCE, encoded)
            .result()
            .orElseThrow();
        assertEquals(original, decoded);
    }

    @Test
    void codecRoundTripsVariantWithoutTexture() {
        SlimeVariant original = new SlimeVariant(
            ResourceLocation.parse("minecraft:gold_ingot"),
            Category.CAVE,
            16777045,
            16774260,
            1,
            Optional.empty()
        );
        JsonElement encoded = SlimeVariant.CODEC.encodeStart(JsonOps.INSTANCE, original)
            .result()
            .orElseThrow();
        SlimeVariant decoded = SlimeVariant.CODEC.parse(JsonOps.INSTANCE, encoded)
            .result()
            .orElseThrow();
        assertEquals(original, decoded);
        assertFalse(decoded.texture().isPresent());
    }

    private static SlimeVariant decode(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        var result = SlimeVariant.CODEC.parse(JsonOps.INSTANCE, parsed);
        return result.result().orElseThrow(() ->
            new AssertionError("decode failed for json:\n" + json + "\nerror: " + result.error().map(Object::toString).orElse("<unknown>")));
    }
}
