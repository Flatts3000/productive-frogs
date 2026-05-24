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
 * Codec round-trip tests for {@link SlimeVariant}. Pins the optional
 * {@code inner_texture} field (v1.0.1+): the codec must accept variant JSONs
 * both with and without it, and the field must round-trip cleanly through
 * encode → decode.
 */
class SlimeVariantTest {

    private static final String WITHOUT_INNER_TEXTURE = """
        {
          "primer_item": "minecraft:iron_ingot",
          "category": "cave",
          "primary_color": 12895428,
          "secondary_color": 14211288
        }
        """;

    private static final String WITH_INNER_TEXTURE = """
        {
          "primer_item": "minecraft:iron_ingot",
          "category": "cave",
          "primary_color": 12895428,
          "secondary_color": 14211288,
          "inner_texture": "minecraft:textures/block/iron_block.png"
        }
        """;

    @Test
    void codecDecodesVariantWithoutOptionalInnerTextureField() {
        SlimeVariant decoded = decode(WITHOUT_INNER_TEXTURE);
        assertEquals(ResourceLocation.parse("minecraft:iron_ingot"), decoded.primerItem());
        assertEquals(Category.CAVE, decoded.category());
        assertEquals(12895428, decoded.primaryColor());
        assertEquals(14211288, decoded.secondaryColor());
        assertEquals(1, decoded.weight(), "weight defaults to 1 when omitted");
        assertTrue(decoded.innerTexture().isEmpty(),
            "inner_texture must be empty when the JSON omits the field — renderer falls back to the missing-texture sprite");
    }

    @Test
    void codecDecodesVariantWithInnerTextureField() {
        SlimeVariant decoded = decode(WITH_INNER_TEXTURE);
        Optional<ResourceLocation> innerTexture = decoded.innerTexture();
        assertTrue(innerTexture.isPresent(), "inner_texture must be present when the JSON includes the field");
        assertEquals(
            ResourceLocation.parse("minecraft:textures/block/iron_block.png"),
            innerTexture.get()
        );
    }

    @Test
    void codecRoundTripsVariantWithInnerTexture() {
        SlimeVariant original = new SlimeVariant(
            ResourceLocation.parse("minecraft:copper_ingot"),
            Category.CAVE,
            14188339,
            16432204,
            1,
            Optional.of(ResourceLocation.parse("minecraft:textures/block/copper_block.png"))
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
    void codecRoundTripsVariantWithoutInnerTexture() {
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
        assertFalse(decoded.innerTexture().isPresent());
    }

    private static SlimeVariant decode(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        var result = SlimeVariant.CODEC.parse(JsonOps.INSTANCE, parsed);
        return result.result().orElseThrow(() ->
            new AssertionError("decode failed for json:\n" + json + "\nerror: " + result.error().map(Object::toString).orElse("<unknown>")));
    }
}
