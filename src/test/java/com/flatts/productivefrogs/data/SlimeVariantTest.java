package com.flatts.productivefrogs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * Codec round-trip tests for {@link SlimeVariant}. Pins the optional
 * {@code inner_block} field (v1.0.1+): the codec must accept variant JSONs
 * both with and without it, and the field must round-trip cleanly through
 * encode → decode.
 */
class SlimeVariantTest {

    private static final String WITHOUT_INNER_BLOCK = """
        {
          "primer_item": "minecraft:iron_ingot",
          "category": "cave",
          "primary_color": 12895428,
          "secondary_color": 14211288
        }
        """;

    private static final String WITH_INNER_BLOCK = """
        {
          "primer_item": "minecraft:iron_ingot",
          "category": "cave",
          "primary_color": 12895428,
          "secondary_color": 14211288,
          "inner_block": "minecraft:iron_block"
        }
        """;

    private static final String WITH_PRIMER_TAG = """
        {
          "primer_tag": "c:ingots/tin",
          "category": "cave",
          "primary_color": 12895428,
          "secondary_color": 14211288
        }
        """;

    private static final String WITHOUT_ANY_PRIMER = """
        {
          "category": "cave",
          "primary_color": 12895428,
          "secondary_color": 14211288
        }
        """;

    @Test
    void codecDecodesVariantWithPrimerTagAndNoPrimerItem() {
        // A tag-driven cross-mod variant: primer_item is absent, primer_tag drives
        // infusion matching (any mod's tin ingot in c:ingots/tin primes it).
        SlimeVariant decoded = decode(WITH_PRIMER_TAG);
        assertTrue(decoded.primerItem().isEmpty(), "primer_item is optional, absent for a tag-driven variant");
        assertTrue(decoded.primerTag().isPresent(), "primer_tag must decode");
        assertEquals("c:ingots/tin", decoded.primerTag().get().identifier().toString());
        assertEquals(Category.CAVE, decoded.category());
    }

    @Test
    void codecRejectsVariantWithNoPrimer() {
        // A variant with neither primer_item nor primer_tag can never be primed
        // by a player, yet would still enter the discovery pool. The codec must
        // fail the decode at the boundary (the generate script guards first-party
        // variants, but a hand-authored datapack override has no such guard).
        var result = SlimeVariant.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(WITHOUT_ANY_PRIMER));
        assertTrue(result.error().isPresent(),
            "decode must fail for a variant with no primer_item and no primer_tag");
        assertTrue(result.error().get().message().contains("primer"),
            "error message should explain the missing primer requirement");
    }

    @Test
    void codecDecodesVariantWithoutOptionalInnerBlockField() {
        SlimeVariant decoded = decode(WITHOUT_INNER_BLOCK);
        assertEquals(Optional.of(Identifier.parse("minecraft:iron_ingot")), decoded.primerItem());
        assertTrue(decoded.primerTag().isEmpty(), "primer_tag absent for an item-primed variant");
        assertEquals(Category.CAVE, decoded.category());
        assertEquals(12895428, decoded.primaryColor());
        assertEquals(14211288, decoded.secondaryColor());
        assertEquals(1, decoded.weight(), "weight defaults to 1 when omitted");
        assertTrue(decoded.innerBlock().isEmpty(),
            "inner_block must be empty when the JSON omits the field — the inner-block render pass is skipped");
    }

    @Test
    void codecDecodesVariantWithInnerBlockField() {
        SlimeVariant decoded = decode(WITH_INNER_BLOCK);
        Optional<Identifier> innerBlock = decoded.innerBlock();
        assertTrue(innerBlock.isPresent(), "inner_block must be present when the JSON includes the field");
        assertEquals(Identifier.parse("minecraft:iron_block"), innerBlock.get());
    }

    @Test
    void codecRoundTripsVariantWithInnerBlock() {
        SlimeVariant original = new SlimeVariant(
            Optional.of(Identifier.parse("minecraft:copper_ingot")),
            Optional.empty(),
            Category.CAVE,
            14188339,
            16432204,
            1,
            Optional.of(Identifier.parse("minecraft:copper_block")),
            Optional.empty()
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
    void codecRoundTripsVariantWithoutInnerBlock() {
        SlimeVariant original = new SlimeVariant(
            Optional.of(Identifier.parse("minecraft:gold_ingot")),
            Optional.empty(),
            Category.CAVE,
            16777045,
            16774260,
            1,
            Optional.empty(),
            Optional.empty()
        );
        JsonElement encoded = SlimeVariant.CODEC.encodeStart(JsonOps.INSTANCE, original)
            .result()
            .orElseThrow();
        SlimeVariant decoded = SlimeVariant.CODEC.parse(JsonOps.INSTANCE, encoded)
            .result()
            .orElseThrow();
        assertEquals(original, decoded);
        assertFalse(decoded.innerBlock().isPresent());
    }

    private static SlimeVariant decode(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        var result = SlimeVariant.CODEC.parse(JsonOps.INSTANCE, parsed);
        return result.result().orElseThrow(() ->
            new AssertionError("decode failed for json:\n" + json + "\nerror: " + result.error().map(Object::toString).orElse("<unknown>")));
    }
}
