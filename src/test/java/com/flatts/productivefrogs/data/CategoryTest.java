package com.flatts.productivefrogs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pure-logic tests for {@link Category}. These don't need a running
 * Minecraft world — they exercise the enum's id/path derivations, ARGB packing,
 * and round-trip codecs that drive every Category-aware surface in the mod.
 */
class CategoryTest {

    @Test
    void hasExactlySixCategories() {
        assertEquals(6, Category.values().length, "Category enum width is load-bearing for the design");
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void idIsLowercaseEnumName(Category cat) {
        // Use Locale.ROOT here to match Category.id()'s own conversion — without
        // it, a Turkish-locale JVM would turn METALLIC.name() into "metallıc"
        // (dotless ı) and the test would fail even though production code is fine.
        assertEquals(cat.name().toLowerCase(Locale.ROOT), cat.id());
        assertEquals(cat.id(), cat.getSerializedName());
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void primerTagPathFormat(Category cat) {
        assertEquals("primer/" + cat.id(), cat.primerTagPath());
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void primedEggItemNameFormat(Category cat) {
        assertEquals(cat.id() + "_frog_egg", cat.primedEggItemName());
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void tintArgbHasFullAlphaTintRgbHasNone(Category cat) {
        int argb = cat.tintArgb();
        int rgb = cat.tintRgb();
        assertEquals(0xFF, (argb >>> 24) & 0xFF, "tintArgb must be fully opaque");
        assertEquals(rgb, argb & 0x00FFFFFF, "low 24 bits must agree between argb/rgb");
        assertEquals(0, (rgb >>> 24) & 0xFF, "tintRgb must not set alpha bits");
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void codecRoundTripsViaJsonOps(Category cat) {
        DataResult<JsonElement> encoded = Category.CODEC.encodeStart(JsonOps.INSTANCE, cat);
        JsonElement json = encoded.getOrThrow();
        assertTrue(json instanceof JsonPrimitive);
        assertEquals(cat.id(), json.getAsString());

        DataResult<Category> decoded = Category.CODEC.parse(JsonOps.INSTANCE, json);
        assertEquals(cat, decoded.getOrThrow());
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void streamCodecRoundTrip(Category cat) {
        ByteBuf buf = Unpooled.buffer();
        try {
            Category.STREAM_CODEC.encode(buf, cat);
            Category decoded = Category.STREAM_CODEC.decode(buf);
            assertEquals(cat, decoded);
            assertEquals(0, buf.readableBytes(), "stream codec must consume every byte it produced");
        } finally {
            buf.release();
        }
    }

    @Test
    void invalidSerializedNameFailsCodec() {
        DataResult<Category> decoded = Category.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("not_a_category"));
        assertNull(decoded.result().orElse(null), "unknown id must not decode");
        assertNotNull(decoded.error().orElse(null), "unknown id must produce a codec error");
    }

    /**
     * {@link Category#shellTintArgb()} blends the category colour with light
     * gray (200,200,200) at 70/30 to produce a subtly-tinted gray that drives
     * the variant-less ResourceSlime outer shell. Pin the math so a future
     * weight tweak shows up here, and assert the result is always opaque
     * (the shell layer wants a full-alpha tint multiply, not a transparent
     * one).
     */
    @ParameterizedTest
    @EnumSource(Category.class)
    void shellTintArgbIsOpaque(Category cat) {
        assertEquals(0xFF, (cat.shellTintArgb() >>> 24) & 0xFF,
            "shellTintArgb must be fully opaque so the shell layer can multiply through it");
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void shellTintArgbBlendsTowardLightGray(Category cat) {
        // 70% × 200 + 30% × <component> per channel. Recomputed here so a
        // weight or anchor change in Category.java requires updating the
        // expected math here (not silently passing).
        int argb = cat.shellTintArgb();
        int rgb = cat.tintRgb();
        int expectedR = (200 * 70 + ((rgb >> 16) & 0xFF) * 30) / 100;
        int expectedG = (200 * 70 + ((rgb >>  8) & 0xFF) * 30) / 100;
        int expectedB = (200 * 70 +  (rgb        & 0xFF) * 30) / 100;
        assertEquals(expectedR, (argb >> 16) & 0xFF, cat + " red channel");
        assertEquals(expectedG, (argb >>  8) & 0xFF, cat + " green channel");
        assertEquals(expectedB,  argb        & 0xFF, cat + " blue channel");
    }

    @Test
    void shellTintArgbIsDistinctAcrossCategories() {
        // The whole point of the per-category shell tint is visual variety —
        // every category must produce a distinct value. (If a future colour
        // tweak collides two categories the eye won't notice, but the test
        // forces a conscious choice.)
        java.util.Set<Integer> tints = new java.util.HashSet<>();
        for (Category cat : Category.values()) {
            assertTrue(tints.add(cat.shellTintArgb()),
                "duplicate shellTintArgb across categories: " + cat);
        }
        assertEquals(Category.values().length, tints.size());
    }
}
