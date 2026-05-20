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
        assertEquals(cat.name().toLowerCase(), cat.id());
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
        Category.STREAM_CODEC.encode(buf, cat);
        Category decoded = Category.STREAM_CODEC.decode(buf);
        assertEquals(cat, decoded);
        assertEquals(0, buf.readableBytes(), "stream codec must consume every byte it produced");
    }

    @Test
    void invalidSerializedNameFailsCodec() {
        DataResult<Category> decoded = Category.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("not_a_category"));
        assertNull(decoded.result().orElse(null), "unknown id must not decode");
        assertNotNull(decoded.error().orElse(null), "unknown id must produce a codec error");
    }
}
